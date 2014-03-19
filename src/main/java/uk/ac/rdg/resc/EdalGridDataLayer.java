/*******************************************************************************
 * Copyright (c) 2014 The University of Reading
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the University of Reading, nor the names of the
 *    authors or contributors may be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 ******************************************************************************/

package uk.ac.rdg.resc;

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.cache.GpuResourceCache;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.layers.TiledImageLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.TileKey;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.media.opengl.GLContext;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

public class EdalGridDataLayer {

    final String layerName;
    private VideoWallCatalogue catalogue;
    private LayerList layerList;
    private RescWorldWindow wwd;

    private EdalGridData dataLayer;
    private Double elevation;
    private VerticalAxis zAxis;
    private DateTime time;
    private TimeAxis tAxis;
    private GridVariableMetadata metadata;
    private Extent<Float> scaleRange;

    public EdalGridDataLayer(String layerName, VideoWallCatalogue catalogue, LayerList layerList,
            RescWorldWindow wwd) throws EdalException {
        this.layerName = layerName;
        this.catalogue = catalogue;
        this.layerList = layerList;
        this.wwd = wwd;

        scaleRange = catalogue.getRangeForLayer(layerName);

        VariableMetadata m = catalogue.getVariableMetadataForLayer(layerName);
        if (!(m instanceof GridVariableMetadata)) {
            throw new EdalException("Cannot create a gridded layer from " + layerName
                    + " as it is not gridded");
        }
        metadata = (GridVariableMetadata) m;

        zAxis = metadata.getVerticalDomain();
        tAxis = metadata.getTemporalDomain();

        if (zAxis != null) {
            this.elevation = GISUtils.getClosestElevationToSurface(zAxis);
        }
        if (tAxis != null) {
            this.time = GISUtils.getClosestToCurrentTime(tAxis);
        }

        cacheFromCurrent();

        drawLayer();
    }

    public void destroy() {
        layerList.remove(dataLayer);
    }

    public void setElevation(Double elevation) {
        int zIndex = zAxis.findIndexOf(elevation);
        this.elevation = zAxis.getCoordinateValue(zIndex);
        drawLayer();
    }

    public Double getElevation() {
        return this.elevation;
    }

    public void setTime(DateTime time) {
        this.time = GISUtils.getClosestTimeTo(time, tAxis);
        drawLayer();
    }

    public DateTime getTime() {
        return this.time;
    }

    public GridVariableMetadata getLayerMetadata() {
        return metadata;
    }

    public BufferedImage getLegend(int size) {
        if (dataLayer != null) {
            try {
                return dataLayer.mapImage.getLegend(size, Color.lightGray, new Color(0, true),
                        false, 20);
            } catch (EdalException e) {
                /*
                 * TODO log properly
                 */
                e.printStackTrace();
            }
        }
        return null;
    }

    private void drawLayer() {
        if (dataLayer != null) {
            layerList.remove(dataLayer);
        }
        dataLayer = new EdalGridData(elevation, time, scaleRange);
        layerList.add(dataLayer);
        wwd.redraw();
    }

    private TimeCacher timeCacheThread = null;
    private ElevationCacher elevationCacheThread = null;

    /**
     * Preloads into the GPU texture cache:
     * 
     * All available times at the current elevation
     * 
     * All available elevations at the current time
     * 
     * If such a caching operation is already running, this will stop the
     * previous caching activity and start a new one. Previously cached data
     * will not be recomputed, so this method should be quick to run.
     */
    public void cacheFromCurrent() {
        if (tAxis != null) {
            if (timeCacheThread != null && timeCacheThread.isAlive()) {
                timeCacheThread.stopCaching();
                try {
                    timeCacheThread.join();
                } catch (InterruptedException e) {
                    /*
                     * Ignore this exception, the thread will stop anyway
                     */
                }
            }

            timeCacheThread = new TimeCacher(time, elevation);
            timeCacheThread.start();
        }

        if (zAxis != null) {
            if (elevationCacheThread != null && elevationCacheThread.isAlive()) {
                elevationCacheThread.stopCaching();
                try {
                    elevationCacheThread.join();
                } catch (InterruptedException e) {
                    /*
                     * Ignore this exception, the thread will stop anyway
                     */
                }
            }

            elevationCacheThread = new ElevationCacher(elevation, time);
            elevationCacheThread.start();
        }
    }

    private void cacheEdalGrid(Double elevation, DateTime time, Extent<Float> scaleRange) {
        EdalGridData cacheLayer = new EdalGridData(elevation, time, scaleRange);
        Map<TileKey, TextureData> cacheData = new HashMap<TileKey, TextureData>();
        GpuResourceCache gpuResourceCache = wwd.getGpuResourceCache();
        /*
         * Load the texture data for each tile. This may require actually
         * generating the images so we do this first, to minimise the time which
         * we are hogging the GLContext for
         */
        for (final TextureTile tile : cacheLayer.getTopLevels()) {
            /*
             * We can check if the cache contains a key without needing the
             * GLContext to be current.
             */
            if (!gpuResourceCache.contains(tile.getTileKey())) {
                cacheLayer.loadTexture(tile);
                cacheData.put(tile.getTileKey(), tile.getTextureData());
            }
        }
        /*
         * Now get the GLContext, dump the textures into the cache and release
         * it
         */
        GLContext context = wwd.getContext();
        context.makeCurrent();
        for (Entry<TileKey, TextureData> cacheDatum : cacheData.entrySet()) {
            gpuResourceCache.put(cacheDatum.getKey(), TextureIO.newTexture(cacheDatum.getValue()));
        }
        context.release();
    }

    public class EdalGridData extends TiledImageLayer {
        private MapImage mapImage;
        private Double elevation;
        private DateTime time;

        public EdalGridData(Double elevation, DateTime time, Extent<Float> scaleRange) {
            super(makeLevelSet(layerName, elevation, time));
            this.elevation = elevation;
            this.time = time;

            ColourScheme colourScheme = new SegmentColourScheme(new ColourScale(
                    scaleRange.getLow(), scaleRange.getHigh(), false), Color.black, Color.black,
                    new Color(0, true), "rainbow", 100);
            RasterLayer rasterLayer = new RasterLayer(layerName, colourScheme);
            mapImage = new MapImage();
            mapImage.getLayers().add(rasterLayer);

            setName(layerName);
            setUseTransparentTextures(true);
            setPickEnabled(true);

            setForceLevelZeroLoads(true);
            setRetainLevelZeroTiles(true);
        }

        protected BufferedImage createTileImage(TextureTile tile) {
            if (mapImage == null) {
                return missingImage(tile);
            }

            int width = tile.getLevel().getTileWidth();
            int height = tile.getLevel().getTileHeight();
            Sector s = tile.getSector();
            BoundingBox bbox = new BoundingBoxImpl(s.getMinLongitude().degrees,
                    s.getMinLatitude().degrees, s.getMaxLongitude().degrees,
                    s.getMaxLatitude().degrees, DefaultGeographicCRS.WGS84);

            PlottingDomainParams params = new PlottingDomainParams(width, height, bbox, null, null,
                    null, elevation, time);
            try {
                return mapImage.drawImage(params, catalogue);
            } catch (EdalException e) {
                e.printStackTrace();
                return missingImage(tile);
            }
        }

        protected void loadTexture(final TextureTile tile) {
            TextureData textureData;

            BufferedImage tileImage = createTileImage(tile);

            textureData = AWTTextureIO.newTextureData(Configuration.getMaxCompatibleGLProfile(),
                    tileImage, isUseMipMaps());

            if (textureData == null) {
                /*
                 * Log error
                 */
                System.out.println("Null texture data");
            }

            tile.setTextureData(textureData);
            if (tile.getLevelNumber() != 0) {
                /*
                 * The level 0 cache is never used.
                 */
                TextureTile.getMemoryCache().add(tile.getTileKey(), tile);
            }
        }

        @Override
        protected void forceTextureLoad(TextureTile tile) {
            /*
             * Do not force texture loading. This makes things slow, and
             * removing it has no noticeable negative effects.
             */
        }

        @Override
        protected void requestTexture(DrawContext dc, TextureTile tile) {
            /*
             * Set the priority of the tile and add a request thread to the
             * queue
             */
            Vec4 centroid = tile.getCentroidPoint(dc.getGlobe());
            Vec4 referencePoint = this.getReferencePoint(dc);
            if (referencePoint != null)
                tile.setPriority(centroid.distanceTo3(referencePoint));

            RequestTask task = new RequestTask(tile, this);
            this.getRequestQ().add(task);
        }

        protected RequestTask createRequestTask(TextureTile tile) {
            return new RequestTask(tile, this);
        }

        protected class RequestTask implements Runnable, Comparable<RequestTask> {
            protected final EdalGridData layer;
            protected final TextureTile tile;

            protected RequestTask(TextureTile tile, EdalGridData layer) {
                this.layer = layer;
                this.tile = tile;
            }

            @Override
            public void run() {
                if (Thread.currentThread().isInterrupted()) {
                    /*
                     * The task was cancelled because it's a duplicate or for
                     * some other reason
                     */
                    return;
                }

                layer.loadTexture(tile);
                /*
                 * This means that each tile gets displayed as it is loaded. The
                 * GPU texture caching might mean that this is unecessary, but
                 * it doesn't slow things down noticeably keeping it in.
                 */
                wwd.redraw();
            }

            /*
             * All of the following taken from BasicTiledImageLayer - sorts out
             * tile priorities
             */

            /**
             * @param that
             *            the task to compare
             * 
             * @return -1 if <code>this</code> less than <code>that</code>, 1 if
             *         greater than, 0 if equal
             * 
             * @throws IllegalArgumentException
             *             if <code>that</code> is null
             */
            @Override
            public int compareTo(RequestTask that) {
                if (that == null) {
                    String msg = Logging.getMessage("nullValue.RequestTaskIsNull");
                    Logging.logger().severe(msg);
                    throw new IllegalArgumentException(msg);
                }
                return this.tile.getPriority() == that.tile.getPriority() ? 0 : this.tile
                        .getPriority() < that.tile.getPriority() ? -1 : 1;
            }

            @Override
            public boolean equals(Object o) {
                if (this == o)
                    return true;
                if (o == null || getClass() != o.getClass())
                    return false;

                final RequestTask that = (RequestTask) o;

                // Don't include layer in comparison so that requests are shared among layers
                return !(tile != null ? !tile.equals(that.tile) : that.tile != null);
            }

            @Override
            public int hashCode() {
                return (tile != null ? tile.hashCode() : 0);
            }

            @Override
            public String toString() {
                return this.tile.toString();
            }
        }
    }

    private static BufferedImage missingImage(TextureTile tile) {
        int width = tile.getLevel().getTileWidth();
        int height = tile.getLevel().getTileHeight();
        BufferedImage image = new BufferedImage(tile.getLevel().getTileWidth(), tile.getLevel()
                .getTileHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        // Draw a red cross on white background
        Graphics2D g2 = image.createGraphics();
        g2.setPaint(Color.WHITE);
        g2.fillRect(0, 0, width, height);
        g2.setPaint(Color.RED);
        g2.drawLine(0, 0, width, height);
        g2.drawLine(0, height, width, 0);
        return image;
    }

    private static LevelSet makeLevelSet(String layerName, Double elevation, DateTime time) {
        String layerId = layerName + ":" + elevation + ":" + time;
        /*
         * Now use the catalogue to determine how many levels we should generate
         */

        AVList params = new AVListImpl();

        params.setValue(AVKey.TILE_WIDTH, 512);
        params.setValue(AVKey.TILE_HEIGHT, 512);
        params.setValue(AVKey.DATA_CACHE_NAME, "EDAL/Tiles/" + layerId);
        params.setValue(AVKey.SERVICE, "*");
        params.setValue(AVKey.DATASET_NAME, layerId);
        params.setValue(AVKey.FORMAT_SUFFIX, ".png");
        params.setValue(AVKey.NUM_LEVELS, 5);
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
        params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                new LatLon(Angle.fromDegrees(60d), Angle.fromDegrees(60d)));
        params.setValue(AVKey.SECTOR, Sector.FULL_SPHERE);

        return new LevelSet(params);
    }

    private class TimeCacher extends Thread {
        private boolean stop = false;
        private DateTime time;
        private Double elevation;

        public TimeCacher(final DateTime time, final Double elevation) {
            super();
            this.time = time;
            this.elevation = elevation;
        }

        @Override
        public void run() {
            int timeIndex = tAxis.findIndexOf(time);
            for (int i = timeIndex + 1; i < tAxis.size(); i++) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(elevation, tAxis.getCoordinateValue(i), scaleRange);
            }
            for (int i = timeIndex - 1; i >= 0; i--) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(elevation, tAxis.getCoordinateValue(i), scaleRange);
            }
            /*
             * TODO This would be a useful debug statement in the logging
             */
            //            System.out.println("cached times for layer: " + layerName + " at elevation "
            //                    + elevation);
        }

        public void stopCaching() {
            stop = true;
        }
    }

    private class ElevationCacher extends Thread {
        private boolean stop = false;
        private DateTime time;
        private Double elevation;

        public ElevationCacher(final Double elevation, final DateTime time) {
            super();
            this.elevation = elevation;
            this.time = time;
        }

        @Override
        public void run() {
            int zIndex = zAxis.findIndexOf(elevation);
            for (int i = zIndex + 1; i < zAxis.size(); i++) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(zAxis.getCoordinateValue(i), time, scaleRange);
            }
            for (int i = zIndex - 1; i >= 0; i--) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(zAxis.getCoordinateValue(i), time, scaleRange);
            }
            /*
             * TODO This would be a useful debug statement in the logging
             */
            //            System.out.println("cached elevations for layer: " + layerName + " at time "
            //                    + time);
        }

        public void stopCaching() {
            stop = true;
        }
    }
}

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
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.Layer;
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
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.media.opengl.GLContext;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsVariable;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.edal.wms.GetMapStyleParams;
import uk.ac.rdg.resc.edal.wms.WmsLayerMetadata;
import uk.ac.rdg.resc.edal.wms.util.StyleDef;
import uk.ac.rdg.resc.logging.RescLogging;

import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.TextureIO;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

/**
 * Class for handling gridded EDAL data and displaying it
 * 
 * @author Guy Griffiths
 */
public class EdalGridDataLayer implements EdalDataLayer {
    /** The ID of the layer in the EDAL system */
    final String layerName;
    /** The {@link VideoWallCatalogue} containing the layer */
    private VideoWallCatalogue catalogue;
    /** The {@link LayerList} to which the {@link EdalGridData} will be added */
    private LayerList layerList;
    /** The {@link RescWorldWindow} which will display the layer */
    private RescWorldWindow wwd;

    /** The actual data {@link Layer} */
    private EdalGridData dataLayer;
    /** The current elevation */
    private Double elevation;
    /** The vertical axis for the data */
    private VerticalAxis zAxis;
    /** The current time */
    private DateTime time;
    /** The time axis for the data */
    private TimeAxis tAxis;
    /** The {@link VariableMetadata} associated with the layer */
    private GridVariableMetadata metadata;

    /** The current colour scale range */
    private Extent<Float> scaleRange;
    /** The current palette name */
    private String palette;
    /** The current logarithmic setting (true to use a log colour scale) */
    private boolean logScale;
    /** The current number of colour bands */
    private int numColorBands;
    /** The current background colour (used for no data) */
    private Color bgColor;
    /** The current colour for data below the minimum */
    private Color underColor;
    /** The current colour for data above the maximum */
    private Color overColor;

    /** The thread pool for running caching operations in the background */
    private ExecutorService threadPool;
    /** The thread for caching different times */
    private TimeCacher timeCacheTask = null;
    /** The thread for caching different elevations */
    private ElevationCacher elevationCacheTask = null;

    private int totalPointsX = -1;
    private int totalPointsY = -1;
    private boolean latLon = false;
    private CacheListener cacheListener;

    /**
     * Instantiate a new {@link EdalGridDataLayer}
     * 
     * @param layerName
     *            The ID of the layer to display
     * @param catalogue
     *            The {@link VideoWallCatalogue} to query the data from
     * @param layerList
     *            The {@link LayerList} to which the data layer should be added
     * @param wwd
     *            The {@link RescWorldWindow} to which the data layer should be
     *            added
     * @throws EdalException
     *             If the requested layer name is not a gridded layer, or if it
     *             cannot be found in the {@link VideoWallCatalogue}
     */
    public EdalGridDataLayer(String layerName, VideoWallCatalogue catalogue, LayerList layerList,
            RescWorldWindow wwd, CacheListener cacheListener) throws EdalException {
        this.layerName = layerName;
        this.catalogue = catalogue;
        this.layerList = layerList;
        this.wwd = wwd;
        this.cacheListener = cacheListener;

        VariableMetadata m = catalogue.getVariableMetadataForLayer(layerName);
        if (!(m instanceof GridVariableMetadata)) {
            throw new EdalException("Cannot create a gridded layer from " + layerName
                    + " as it is not gridded");
        }
        metadata = (GridVariableMetadata) m;

        if (GISUtils.isWgs84LonLat(metadata.getHorizontalDomain().getCoordinateReferenceSystem())) {
            /*
             * If we have lat-lon native data, we can easily calculate the
             * required resolution
             */
            BoundingBox boundingBox = metadata.getHorizontalDomain().getBoundingBox();
            double xSpan = boundingBox.getMaxX() - boundingBox.getMinX();
            double ySpan = boundingBox.getMaxY() - boundingBox.getMinY();
            double fracX = xSpan / 360;
            double fracY = ySpan / 180;
            int xSize = metadata.getHorizontalDomain().getXSize();
            int ySize = metadata.getHorizontalDomain().getYSize();
            totalPointsX = (int) (xSize / fracX);
            totalPointsY = (int) (ySize / fracY);
            latLon = true;
        }

        zAxis = metadata.getVerticalDomain();
        tAxis = metadata.getTemporalDomain();

        /*
         * Set a default elevation
         */
        if (zAxis != null) {
            this.elevation = GISUtils.getClosestElevationToSurface(zAxis);
        }
        /*
         * Set a default time
         */
        if (tAxis != null) {
            this.time = GISUtils.getClosestToCurrentTime(tAxis);
        }

        /*
         * TODO make this configurable or dependent on the number of CPU cores
         */
        threadPool = Executors.newFixedThreadPool(2);

        WmsLayerMetadata plottingMetadata = catalogue.getLayerMetadata(layerName);
        /*
         * Set up the default colour scale values
         */
        scaleRange = plottingMetadata.getColorScaleRange();
        palette = plottingMetadata.getPalette();
        logScale = plottingMetadata.isLogScaling();
        numColorBands = plottingMetadata.getNumColorBands();
        bgColor = new Color(0, true);
        underColor = Color.black;
        overColor = Color.black;

        /*
         * Now start caching data in the background. This allows users to change
         * the elevation/time and have a smooth transition
         */
        cacheFromCurrent();

        /*
         * Now create the layer and add it to the layer list
         */
        drawLayer();
    }

    @Override
    public void destroy() {
        /*
         * Remove the data layer
         */
        layerList.remove(dataLayer);

        /*
         * Stop any caching
         */
        if (timeCacheTask != null) {
            timeCacheTask.stopCaching();
        }
        if (elevationCacheTask != null) {
            elevationCacheTask.stopCaching();
        }

        threadPool.shutdown();
    }

    @Override
    public void setElevation(double elevation, Extent<Double> elevationRange) {
        /*
         * Set to the nearest available elevation. Elevation range is ignored
         * for gridded data
         */
        double lastElevation = getElevationOnAxis(elevation);
        if (this.elevation != lastElevation) {
            this.elevation = lastElevation;
            drawLayer();
        }
    }
    
    public Double getElevationOnAxis(double elevation) {
        int zIndex = zAxis.findIndexOf(elevation);
        return zAxis.getCoordinateValue(zIndex);
    }

    @Override
    public Double getElevation() {
        return this.elevation;
    }

    @Override
    public void setTime(DateTime time, Extent<DateTime> timeRange) {
        /*
         * Set to the nearest available time. Time range is ignored for gridded
         * data
         */
        DateTime lastTime = getTimeOnAxis(time);
        if (lastTime.getMillis() != this.time.getMillis()) {
            this.time = lastTime;
            drawLayer();
        }
    }

    public DateTime getTimeOnAxis(DateTime time) {
        return GISUtils.getClosestTimeTo(time, tAxis);
    }
    
    @Override
    public DateTime getTime() {
        return this.time;
    }

    @Override
    public void scaleLimitsChanged(Extent<Float> newScaleRange) {
        this.scaleRange = newScaleRange;
        cacheFromCurrent();
        drawLayer();
    }

    @Override
    public void paletteChanged(String newPalette) {
        this.palette = newPalette;
        cacheFromCurrent();
        drawLayer();
    }

    @Override
    public void aboveMaxColourChanged(Color aboveMax) {
        this.overColor = aboveMax;
        cacheFromCurrent();
        drawLayer();
    }

    @Override
    public void belowMinColourChanged(Color belowMin) {
        this.underColor = belowMin;
        cacheFromCurrent();
        drawLayer();
    }

    @Override
    public void setNumColourBands(int numColourBands) {
        this.numColorBands = numColourBands;
        drawLayer();
    }

    @Override
    public void setOpacity(double opacity) {
        if (dataLayer != null) {
            dataLayer.setOpacity(opacity);
        }
    }
    
    @Override
    public Double getOpacity() {
        if (dataLayer != null) {
            return dataLayer.getOpacity();
        }
        return null;
    }

    @Override
    public void bulkChange(Extent<Float> scaleRange, String palette, Color belowMin,
            Color aboveMax, boolean logScaling, int numColourBands) {
        this.scaleRange = scaleRange;
        this.palette = palette;
        this.underColor = belowMin;
        this.overColor = aboveMax;
        this.logScale = logScaling;
        this.numColorBands = numColourBands;
        cacheFromCurrent();
        drawLayer();
    }

    @Override
    public GridVariableMetadata getVariableMetadata() {
        return metadata;
    }

    @Override
    public NcwmsVariable getPlottingMetadata() {
        return new NcwmsVariable(layerName, scaleRange, palette, underColor, overColor, bgColor,
                logScale ? "log" : "linear", numColorBands);
    }

    @Override
    public BufferedImage getLegend(int size, boolean labels) {
        if (dataLayer != null) {
            try {
                return dataLayer.mapImage.getLegend(size, Color.lightGray, new Color(0, 0, 0, 150),
                        labels, LEGEND_WIDTH);
            } catch (EdalException e) {
                String message = RescLogging.getMessage("resc.DataReadingProblem");
                Logging.logger().warning(message);
            }
        } else {
            String message = RescLogging.getMessage("resc.NoLayer");
            Logging.logger().warning(message);
        }
        return null;
    }

    /**
     * Does the actual drawing of the layer. This gets called when something has
     * changed and the layer needs redrawing.
     */
    private void drawLayer() {
        double opacity = 1.0;
        if (dataLayer != null) {
            opacity = dataLayer.getOpacity();
            layerList.remove(dataLayer);
        }
        /*
         * Because worldwind is geared up to displaying static layers, when we
         * change some parameters, we simply remove the old layer and add a
         * newly-created one.
         */
        dataLayer = new EdalGridData(elevation, time, palette, scaleRange, logScale, numColorBands,
                bgColor, underColor, overColor, latLon, totalPointsX, totalPointsY);
        dataLayer.setOpacity(opacity);
        layerList.add(dataLayer);
        wwd.redraw();
    }

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
        if (timeCacheTask != null) {
            timeCacheTask.stopCaching();
        }
        if (elevationCacheTask != null) {
            elevationCacheTask.stopCaching();
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(20L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            /*
             * Nothing we can do about this
             */
        }
        /*
         * TODO make this a configurable quantity
         */
        threadPool = Executors.newFixedThreadPool(1);

        if (tAxis != null) {
            timeCacheTask = new TimeCacher(time, elevation, cacheListener);
            threadPool.submit(timeCacheTask);
        }

        if (zAxis != null) {
            elevationCacheTask = new ElevationCacher(elevation, time, cacheListener);
            threadPool.submit(elevationCacheTask);
        }
    }

    /**
     * Does the actual caching work for a single elevation and time
     * 
     * @param elevation
     *            The elevation to cache at
     * @param time
     *            The time to cache at
     */
    private void cacheEdalGrid(Double elevation, DateTime time) {
        GpuResourceCache gpuResourceCache = wwd.getGpuResourceCache();
        EdalGridData cacheLayer = new EdalGridData(elevation, time, palette, scaleRange, logScale,
                numColorBands, bgColor, underColor, overColor, latLon, totalPointsX, totalPointsY);
        Map<TileKey, TextureData> cacheData = new HashMap<TileKey, TextureData>();
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
         * Obtaining and releasing the GLContext takes enough time that checking
         * whether we need to do so first makes a big difference
         */
        if (cacheData.size() > 0) {
            /*
             * Now get the GLContext, dump the textures into the cache and
             * release it.
             */
            GLContext context = wwd.getContext();
            context.makeCurrent();
            for (Entry<TileKey, TextureData> cacheDatum : cacheData.entrySet()) {
                gpuResourceCache.put(cacheDatum.getKey(),
                        TextureIO.newTexture(cacheDatum.getValue()));
            }
            context.release();
        }
    }

    /**
     * A {@link TiledImageLayer} which creates tiles on-the-fly when they are
     * required.
     * 
     * This has its own copy of elevation and time. It is a nested class, and
     * could use the values from the parent {@link EdalGridDataLayer}, but this
     * would not allow us to use {@link EdalGridData} to precache layers.
     * 
     * Much of this code is adapted from {@link BasicTiledImageLayer}
     * 
     * @author Guy Griffiths
     */
    public class EdalGridData extends TiledImageLayer {
        /** The {@link MapImage} which will be used to generate the images */
        private MapImage mapImage;
        private Double elevation;
        private DateTime time;

        public EdalGridData(Double elevation, DateTime time, String palette,
                Extent<Float> scaleRange, boolean logScale, int numColorBands, Color bgColor,
                Color underColor, Color overColor, boolean latLon, int totalX, int totalY) {
            super(makeLevelSet(layerName, elevation, time, palette, scaleRange, logScale,
                    numColorBands, bgColor, underColor, overColor, latLon, totalX, totalY));
            this.elevation = elevation;
            this.time = time;

            try {
                /*
                 * Create a MapImage object for generating the data images.
                 * 
                 * This uses the EDAL system to generate the default style for
                 * the given layer.
                 * 
                 * TODO support other pre-defined layer types.
                 */
                List<StyleDef> supportedStyles = catalogue.getSupportedStyles(catalogue
                        .getVariableMetadataForLayer(layerName));
                String plotStyleName = null;
                for (StyleDef style : supportedStyles) {
                    if (style.getStyleName().startsWith("default")) {
                        plotStyleName = style.getStyleName();
                    }
                }
                if (plotStyleName == null) {
                    throw new EdalException("No default style defined for this layer");
                }
                mapImage = GetMapStyleParams.getMapImageFromStyleNameAndParams(catalogue,
                        layerName, plotStyleName, palette, scaleRange, logScale, numColorBands,
                        bgColor, underColor, overColor);
            } catch (EdalException e) {
                e.printStackTrace();
                /*
                 * TODO log better
                 */
            }

            setName(layerName);
            setUseTransparentTextures(true);
            setPickEnabled(true);

            setForceLevelZeroLoads(true);
            setRetainLevelZeroTiles(true);
        }

        /**
         * Generates the image for a given {@link TextureTile}
         * 
         * @param tile
         *            The {@link TextureTile} specifying location, size, etc.
         * @return A {@link BufferedImage} representing the data at the given
         *         location
         */
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
                /*
                 * Problem generating an image. Log and return a standard image
                 */
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
                String message = RescLogging.getMessage("resc.DataReadingProblem");
                Logging.logger().warning(message);
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
                 * GPU texture caching might mean that this is unnecessary, but
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

                /*
                 * Don't include layer in comparison so that requests are shared
                 * among layers
                 */
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

    /**
     * Creates a red cross on a white background for cases where the image
     * cannot be generated
     * 
     * @param tile
     *            The {@link TextureTile} which is to be generated
     * @return A replacement image
     */
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

    private static LevelSet makeLevelSet(String layerName, Double elevation, DateTime time,
            String palette, Extent<Float> scaleRange, boolean logScale, int numColorBands,
            Color bgColor, Color underColor, Color overColor, boolean latLon, int totalX, int totalY) {
        /*
         * Create a unique ID for the layer based on all adjustable settings.
         * This is for caching purposes.
         */
        String layerId = layerName + ":" + elevation + ":" + time + ":" + palette + ":"
                + scaleRange.toString() + ":" + logScale + ":" + numColorBands + ":" + bgColor
                + ":" + underColor + underColor.getAlpha() + ":" + overColor + overColor.getAlpha();
        layerId = UUID.nameUUIDFromBytes(layerId.getBytes()).toString();

        AVList params = new AVListImpl();

        params.setValue(AVKey.DATA_CACHE_NAME, "EDAL/Tiles/" + layerId);
        params.setValue(AVKey.SERVICE, "*");
        params.setValue(AVKey.DATASET_NAME, layerId);
        params.setValue(AVKey.FORMAT_SUFFIX, ".png");
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);

        if (latLon) {
            params.setValue(AVKey.NUM_LEVELS, 1);
            params.setValue(AVKey.TILE_WIDTH, totalX);
            params.setValue(AVKey.TILE_HEIGHT, totalY);
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                    new LatLon(Angle.fromDegrees(180d), Angle.fromDegrees(360d)));
        } else {
            params.setValue(AVKey.NUM_LEVELS, 5);
            params.setValue(AVKey.TILE_WIDTH, 256);
            params.setValue(AVKey.TILE_HEIGHT, 256);
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                    new LatLon(Angle.fromDegrees(60d), Angle.fromDegrees(60d)));
        }
        params.setValue(AVKey.SECTOR, Sector.FULL_SPHERE);

        return new LevelSet(params);
    }

    /**
     * Class to cache all times at a fixed elevation
     * 
     * @author Guy Griffiths
     */
    private class TimeCacher implements Runnable {
        private boolean stop = false;
        private DateTime time;
        private Double elevation;
        private CacheListener cacheListener;

        public TimeCacher(final DateTime time, final Double elevation, final CacheListener cacheListener) {
            super();
            this.time = time;
            this.elevation = elevation;
            this.cacheListener = cacheListener;
        }

        @Override
        public void run() {
            if(cacheListener != null) {
                cacheListener.timeCachingIncomplete();
            }
            int timeIndex = tAxis.findIndexOf(time);
            /*
             * Cache times above the current
             */
            for (int i = timeIndex + 1; i < tAxis.size(); i++) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(elevation, tAxis.getCoordinateValue(i));
            }
            /*
             * Now cache times below the current
             */
            for (int i = timeIndex - 1; i >= 0; i--) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(elevation, tAxis.getCoordinateValue(i));
            }
            String message = RescLogging.getMessage("resc.CachedTimes", layerName, elevation);
            Logging.logger().finer(message);
            if(cacheListener != null) {
                cacheListener.timeCachingComplete();
            }
        }

        public void stopCaching() {
            stop = true;
        }
    }

    /**
     * Class to cache all elevations at a fixed time
     * 
     * @author Guy Griffiths
     */
    private class ElevationCacher implements Runnable {
        private boolean stop = false;
        private DateTime time;
        private Double elevation;
        private CacheListener cacheListener;

        public ElevationCacher(final Double elevation, final DateTime time, final CacheListener cacheListener) {
            super();
            this.elevation = elevation;
            this.time = time;
            this.cacheListener = cacheListener;
        }

        @Override
        public void run() {
            if(cacheListener != null) {
                cacheListener.elevationCachingIncomplete();
            }
            int zIndex = zAxis.findIndexOf(elevation);
            /*
             * Cache elevations above the current
             */
            for (int i = zIndex + 1; i < zAxis.size(); i++) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(zAxis.getCoordinateValue(i), time);
            }
            /*
             * Now cache elevations below the current
             */
            for (int i = zIndex - 1; i >= 0; i--) {
                if (stop) {
                    return;
                }
                cacheEdalGrid(zAxis.getCoordinateValue(i), time);
            }
            String message = RescLogging.getMessage("resc.CachedElevations", layerName, time);
            Logging.logger().finer(message);
            if(cacheListener != null) {
                cacheListener.elevationCachingComplete();
            }
        }

        public void stopCaching() {
            stop = true;
        }
    }
    
    interface CacheListener {
        /**
         * Called to indicate elevation caching is not finished
         */
        public void elevationCachingIncomplete();
        /**
         * Called when elevation caching is complete
         */
        public void elevationCachingComplete();
        /**
         * Called to indicate time caching is not finished
         */
        public void timeCachingIncomplete();
        /**
         * Called when time caching is complete
         */
        public void timeCachingComplete();
    }
}

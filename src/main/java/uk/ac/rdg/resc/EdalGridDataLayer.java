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

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

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
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

/**
 * Class for handling gridded EDAL data and displaying it
 * 
 * @author Guy Griffiths
 */
public class EdalGridDataLayer extends TiledImageLayer implements EdalDataLayer {
    static int gridLayerNumber = 0;

    /** The ID of the layer in the EDAL system */
    private final String layerName;
    /** The {@link VideoWallCatalogue} containing the layer */
    private VideoWallCatalogue catalogue;

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

    /** The {@link MapImage} which will be used to generate the images */
    private MapImage mapImage;

    /**
     * Cache for generated images
     */
    private Cache imageCache;
    private String plotStyleName;

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
    public EdalGridDataLayer(String layerName, VideoWallCatalogue catalogue,
            CacheListener cacheListener) throws EdalException {
        super(makeLevelSet(layerName, catalogue));

        CacheManager manager = CacheManager.create();
        imageCache = manager.getCache(VideoWall.CACHE_NAME);

        this.layerName = layerName;
        this.catalogue = catalogue;

        /*
         * No need to check that this is the correct type - that has already
         * been done in makeLevelSet
         */
        metadata = (GridVariableMetadata) catalogue.getVariableMetadataForLayer(layerName);

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
         * Create a MapImage object for generating the data images.
         * 
         * This uses the EDAL system to generate the default style for the given
         * layer.
         * 
         * TODO support other pre-defined layer types.
         */
        List<StyleDef> supportedStyles = catalogue.getSupportedStyles(catalogue
                .getVariableMetadataForLayer(layerName));
        for (StyleDef style : supportedStyles) {
            if (style.getStyleName().startsWith("default")) {
                plotStyleName = style.getStyleName();
            }
        }
        if (plotStyleName == null) {
            throw new EdalException("No default style defined for this layer");
        }

        mapImageChanged();

        setName(layerName);
        setUseTransparentTextures(true);
        setPickEnabled(true);

        setForceLevelZeroLoads(true);
        setRetainLevelZeroTiles(true);
    }

    @Override
    public void setDataElevation(double elevation, Extent<Double> elevationRange) {
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
    public Double getDataElevation() {
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
        mapImageChanged();
        drawLayer();
    }

    @Override
    public void paletteChanged(String newPalette) {
        this.palette = newPalette;
        mapImageChanged();
        drawLayer();
    }

    @Override
    public void aboveMaxColourChanged(Color aboveMax) {
        this.overColor = aboveMax;
        mapImageChanged();
        drawLayer();
    }

    @Override
    public void belowMinColourChanged(Color belowMin) {
        this.underColor = belowMin;
        mapImageChanged();
        drawLayer();
    }

    @Override
    public void setNumColourBands(int numColourBands) {
        this.numColorBands = numColourBands;
        mapImageChanged();
        drawLayer();
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
        mapImageChanged();
        drawLayer();
    }

    private void mapImageChanged() {
        try {
            mapImage = GetMapStyleParams.getMapImageFromStyleNameAndParams(catalogue, layerName,
                    plotStyleName, palette, scaleRange, logScale, numColorBands, bgColor,
                    underColor, overColor);
        } catch (EdalException e) {
            String message = RescLogging.getMessage("resc.MapImageProblem");
            Logging.logger().severe(message);
        }
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
        try {
            return mapImage.getLegend(LEGEND_WIDTH, size, Color.lightGray, new Color(0, 0, 0, 150), labels, true, 0.1f, 0.05f);
        } catch (EdalException e) {
            e.printStackTrace();
            String message = RescLogging.getMessage("resc.DataReadingProblem");
            Logging.logger().warning(message);
        }
        return null;
    }

    /**
     * Does the actual drawing of the layer. This gets called when something has
     * changed and the layer needs redrawing.
     */
    private void drawLayer() {
        setExpiryTime(System.currentTimeMillis());
        firePropertyChange(AVKey.LAYER, null, this);
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

        CacheKey key = new CacheKey(layerName, params, scaleRange, palette, underColor, overColor,
                logScale, numColorBands);
        BufferedImage image;
        Element element = imageCache.get(key);
        if (element != null && element.getObjectValue() != null) {
            image = (BufferedImage) element.getObjectValue();
        } else {
            try {
                image = mapImage.drawImage(params, catalogue);
                imageCache.put(new Element(key, image));
            } catch (EdalException e) {
                /*
                 * Problem generating an image. Log and return a standard image
                 */
                String message = RescLogging.getMessage("resc.DataReadingProblem");
                Logging.logger().warning(message);
                return missingImage(tile);
            }
        }
        return image;
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
        this.loadTexture(tile);
    }

    @Override
    protected void requestTexture(DrawContext dc, TextureTile tile) {
        Vec4 centroid = tile.getCentroidPoint(dc.getGlobe());
        Vec4 referencePoint = this.getReferencePoint(dc);
        if (referencePoint != null)
            tile.setPriority(centroid.distanceTo3(referencePoint));

        RequestTask task = new RequestTask(tile, this);
        this.getRequestQ().add(task);
    }

    protected class RequestTask implements Runnable, Comparable<RequestTask> {
        protected final EdalGridDataLayer layer;
        protected final TextureTile tile;

        protected RequestTask(TextureTile tile, EdalGridDataLayer layer) {
            this.layer = layer;
            this.tile = tile;
        }

        @Override
        public void run() {
            layer.loadTexture(tile);
            EdalGridDataLayer.this.firePropertyChange(AVKey.LAYER, null, EdalGridDataLayer.this);
        }

        /*
         * All of the following taken from BasicTiledImageLayer - sorts out tile
         * priorities
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
            return this.tile.getPriority() == that.tile.getPriority() ? 0
                    : this.tile.getPriority() < that.tile.getPriority() ? -1 : 1;
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

    /**
     * Overrides the parent method, populating the top level with
     * {@link RescTextureTile}s instead of normal {@link TextureTile}s.
     * 
     * The only difference between the 2 is that {@link RescTextureTile}s have
     * an update time based on when their texture data was set, rather than when
     * they were bound to a draw context. If normal {@link TextureTile}s are
     * used, then they falsely test as not having expired when they actually
     * have.
     * 
     * I'm not sure of exactly why this is the case, but this behaviour can be
     * seen by:
     * 
     * <li>Commenting out this method</li>
     * 
     * <li>Running the software and selecting a layer</li>
     * 
     * <li>Zooming in a lot</li>
     * 
     * <li>Changing the colour palette</li>
     * 
     * <li>Zooming out</li>
     * 
     * After which several tiles are not refreshed, but use the old colour
     * palette. Try the same again with this method in and it works as expected
     * (although you may need to move the mouse about to trigger a redraw)
     */
    protected void createTopLevelTiles() {
        super.createTopLevelTiles();
        ArrayList<TextureTile> rescTiles = new ArrayList<>();
        for (TextureTile tile : topLevels) {
            rescTiles.add(new RescTextureTile(tile.getSector(), tile.getLevel(), tile.getRow(),
                    tile.getColumn()));
        }
        topLevels = rescTiles;
    }

    @SuppressWarnings({ "serial" })
    /**
     * Class to be used as a key for the image cache. Combines
     * {@link PlottingDomainParams} with all of the scale parameters.
     * 
     * The ideal would be to use {@link MapImage} in place of all of the scale
     * parameters since this is more precise, but at the time of writing
     * {@link MapImage} doesn't necessarily implement hashCode and equals
     *
     * @author Guy Griffiths
     */
    private static class CacheKey implements Serializable {
        private String layerName;
        private PlottingDomainParams params;
        private Extent<Float> scaleRange;
        private String palette;
        private Color belowMin;
        private Color aboveMax;
        private boolean logScaling;
        private int numColourBands;

        public CacheKey(String layerName, PlottingDomainParams params, Extent<Float> scaleRange,
                String palette, Color belowMin, Color aboveMax, boolean logScaling,
                int numColourBands) {
            this.layerName = layerName;
            this.params = params;
            this.scaleRange = scaleRange;
            this.palette = palette;
            this.belowMin = belowMin;
            this.aboveMax = aboveMax;
            this.logScaling = logScaling;
            this.numColourBands = numColourBands;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((aboveMax == null) ? 0 : aboveMax.hashCode());
            result = prime * result + ((belowMin == null) ? 0 : belowMin.hashCode());
            result = prime * result + ((layerName == null) ? 0 : layerName.hashCode());
            result = prime * result + (logScaling ? 1231 : 1237);
            result = prime * result + numColourBands;
            result = prime * result + ((palette == null) ? 0 : palette.hashCode());
            result = prime * result + ((params == null) ? 0 : params.hashCode());
            result = prime * result + ((scaleRange == null) ? 0 : scaleRange.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (aboveMax == null) {
                if (other.aboveMax != null)
                    return false;
            } else if (!aboveMax.equals(other.aboveMax))
                return false;
            if (belowMin == null) {
                if (other.belowMin != null)
                    return false;
            } else if (!belowMin.equals(other.belowMin))
                return false;
            if (layerName == null) {
                if (other.layerName != null)
                    return false;
            } else if (!layerName.equals(other.layerName))
                return false;
            if (logScaling != other.logScaling)
                return false;
            if (numColourBands != other.numColourBands)
                return false;
            if (palette == null) {
                if (other.palette != null)
                    return false;
            } else if (!palette.equals(other.palette))
                return false;
            if (params == null) {
                if (other.params != null)
                    return false;
            } else if (!params.equals(other.params))
                return false;
            if (scaleRange == null) {
                if (other.scaleRange != null)
                    return false;
            } else if (!scaleRange.equals(other.scaleRange))
                return false;
            return true;
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

    private static LevelSet makeLevelSet(String layerName, VideoWallCatalogue catalogue)
            throws EdalException {
        VariableMetadata m = catalogue.getVariableMetadataForLayer(layerName);
        if (!(m instanceof GridVariableMetadata)) {
            throw new EdalException("Cannot create a gridded layer from " + layerName
                    + " as it is not gridded");
        }
        GridVariableMetadata metadata = (GridVariableMetadata) m;
        AVList params = new AVListImpl();
        params.setValue(AVKey.DATA_CACHE_NAME, "EDAL/Tiles/" + layerName + (gridLayerNumber++));
        params.setValue(AVKey.DATASET_NAME, layerName);

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
            int totalPointsX = (int) (xSize / fracX);
            int totalPointsY = (int) (ySize / fracY);

            int biggest = totalPointsX > totalPointsY ? totalPointsX : totalPointsY;
            if (biggest < 256) {
                /*
                 * For total grids < 256x256 pixels we want one level with the
                 * correct number of points
                 */
                params.setValue(AVKey.NUM_LEVELS, 1);
                params.setValue(AVKey.TILE_WIDTH, totalPointsX);
                params.setValue(AVKey.TILE_HEIGHT, totalPointsY);
            } else {
                /*
                 * Otherwise we want to tile it in 256x256 tiles
                 */
                int nLevels = 2 + (int) (Math.log(biggest / 256) / Math.log(2));
                params.setValue(AVKey.NUM_LEVELS, nLevels);
                params.setValue(AVKey.TILE_WIDTH, 256);
                params.setValue(AVKey.TILE_HEIGHT, 256);
            }
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                    new LatLon(Angle.fromDegrees(180d), Angle.fromDegrees(360d)));
        } else {
            params.setValue(AVKey.NUM_LEVELS, 10);
            params.setValue(AVKey.TILE_WIDTH, 256);
            params.setValue(AVKey.TILE_HEIGHT, 256);
            params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                    new LatLon(Angle.fromDegrees(60d), Angle.fromDegrees(60d)));
        }

        params.setValue(AVKey.SERVICE, "*");
        params.setValue(AVKey.FORMAT_SUFFIX, ".png");
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
        params.setValue(AVKey.SECTOR, Sector.FULL_SPHERE);

        return new LevelSet(params);
    }
}

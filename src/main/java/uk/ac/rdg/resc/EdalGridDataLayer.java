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
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.util.LevelSet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class EdalGridDataLayer {

    final String layerName;
    private VideoWallCatalogue catalogue;
    private LayerList layerList;
    private RescWorldWindow wwd;

    private EdalGridData dataLayer;
    private Double elevation;
    private DateTime time;
    private Extent<Float> scaleRange;

    public EdalGridDataLayer(String layerName, VideoWallCatalogue catalogue, LayerList layerList, RescWorldWindow wwd) {
        this.layerName = layerName;
        this.catalogue = catalogue;
        this.layerList = layerList;
        this.wwd = wwd;

        try {
            scaleRange = catalogue.getRangeForLayer(layerName);
        } catch (EdalException e) {
            e.printStackTrace();
            /*
             * TODO log this properly
             */
            return;
        }

        try {
            VariableMetadata metadata = catalogue.getVariableMetadataForLayer(layerName);
            /*
             * If the time/elevation values are not valid then set them to a
             * default value
             */
            if (metadata.getTemporalDomain() != null
                    && !metadata.getTemporalDomain().contains(time)) {
                time = metadata.getTemporalDomain().getExtent().getHigh();
            }
            if (metadata.getVerticalDomain() != null
                    && !metadata.getVerticalDomain().contains(elevation)) {
                elevation = metadata.getVerticalDomain().getExtent().getLow();
            }
        } catch (EdalException e) {
            e.printStackTrace();
            /*
             * TODO log this properly
             */
            return;
        }
        drawLayer();
    }

    public void destroy() {
        layerList.remove(dataLayer);
    }

    public void setElevation(Double elevation) {
        this.elevation = elevation;
        drawLayer();
    }

    public void setTime(DateTime time) {
        this.time = time;
        drawLayer();
    }

    private void drawLayer() {
        if (dataLayer != null) {
            layerList.remove(dataLayer);
        }
        dataLayer = new EdalGridData(elevation, time, scaleRange);
        layerList.add(dataLayer);
        wwd.redraw();
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

    public class EdalGridData extends BasicTiledImageLayer {
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

        protected void loadTexture(TextureTile tile) {
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
        }

        @Override
        protected void retrieveTexture(TextureTile tile, DownloadPostProcessor postProcessor) {
            final File outFile = WorldWind.getDataFileStore().newFile(tile.getPath());
            if (outFile == null) {
                return;
            }

            if (outFile.exists()) {
                addTileToCache(tile);
                return;
            }

            // Create and save tile texture image
            BufferedImage image = createTileImage(tile);
            addTileToCache(tile);
            try {
                synchronized (this.fileLock) {
                    ImageIO.write(image, "png", outFile);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static LevelSet makeLevelSet(String layerName, Double elevation, DateTime time) {
        String layerId = layerName + ":" + elevation + ":" + time;
        /*
         * Now use the catalogue to determine how many levels we should generate
         */

        AVList params = new AVListImpl();

        params.setValue(AVKey.TILE_WIDTH, 128);
        params.setValue(AVKey.TILE_HEIGHT, 128);
        params.setValue(AVKey.DATA_CACHE_NAME, "EDAL/Tiles/" + layerId);
        params.setValue(AVKey.SERVICE, "*");
        params.setValue(AVKey.DATASET_NAME, layerId);
        params.setValue(AVKey.FORMAT_SUFFIX, ".png");
        params.setValue(AVKey.NUM_LEVELS, 10);
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
        params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                new LatLon(Angle.fromDegrees(36d), Angle.fromDegrees(36d)));
        params.setValue(AVKey.SECTOR, Sector.FULL_SPHERE);

        return new LevelSet(params);
    }
}

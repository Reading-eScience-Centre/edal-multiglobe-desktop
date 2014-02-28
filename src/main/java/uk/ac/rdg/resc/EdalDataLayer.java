/*
Copyright (C) 2001, 2006 United States Government
as represented by the Administrator of the
National Aeronautics and Space Administration.
All Rights Reserved.
 */
package uk.ac.rdg.resc;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.util.LevelSet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.imageio.ImageIO;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.TemporalDomain;
import uk.ac.rdg.resc.edal.domain.VerticalDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourMap;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.PaletteColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

/**
 * Procedural layer example
 * 
 * @author Patrick Murris
 * @version $Id:$
 */
public class EdalDataLayer extends BasicTiledImageLayer implements SelectListener {
    private VideoWallCatalogue catalogue;
    private MapImage mapImage = null;

    private DateTime time;// = new DateTime(2010, 07, 15, 12, 00, ISOChronology.getInstanceUTC());
    private Double elevation;// = 5.0;

    /**
     * Creates an {@link EdalDataLayer} and creates all necessary data in the
     * cache.
     * 
     * @param layerName
     * @param catalogue
     */
    public static void precacheLayer(final String layerName, final VideoWallCatalogue catalogue) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                VariableMetadata metadata;
                try {
                    metadata = catalogue.getVariableMetadataForLayer(layerName);
                } catch (EdalException e) {
                    e.printStackTrace();
                    /*
                     * TODO log
                     */
                    return;
                }
                /*
                 * Now cache level 0 data for all possible levels. This
                 * corresponds to a resolution of 36 degrees / 128 pixels in
                 * radians
                 */
                //                double resolution = 36 * Math.PI / (180.0 * 128);

                List<Double> elevations = new ArrayList<>();
                VerticalDomain verticalDomain = metadata.getVerticalDomain();
                if (verticalDomain != null) {
                    if (verticalDomain instanceof VerticalAxis) {
                        VerticalAxis verticalAxis = (VerticalAxis) verticalDomain;
                        elevations.addAll(verticalAxis.getCoordinateValues());
                    } else {
                        /*
                         * TODO continuous vertical domain - how can we handle
                         * this in general?
                         * 
                         * For now, just cache the default value
                         */
                        elevations.add(GISUtils.getClosestElevationToSurface(verticalDomain));
                    }
                } else {
                    elevations.add(null);
                }

                List<DateTime> times = new ArrayList<>();
                TemporalDomain timeDomain = metadata.getTemporalDomain();
                if (timeDomain != null) {
                    if (timeDomain instanceof TimeAxis) {
                        TimeAxis timeAxis = (TimeAxis) timeDomain;
                        times.addAll(timeAxis.getCoordinateValues());
                    } else {
                        /*
                         * TODO continuous time domain - how can we handle this
                         * in general?
                         * 
                         * For now, just cache the default value
                         */
                        times.add(GISUtils.getClosestToCurrentTime(timeDomain));
                    }
                } else {
                    times.add(null);
                }
                Collections.reverse(elevations);
                for (Double z : elevations) {
                    for (DateTime t : times) {
                        /*
                         * This gets fired off in it's own thread
                         */
                        EdalDataLayer layer = new EdalDataLayer(layerName, catalogue, z, t);
                        for (TextureTile tile : layer.getTopLevels()) {
                            layer.retrieveRemoteTexture(tile, null);
                        }
                    }
                }
                System.out.println("Done precaching "+layerName);
            }
        }).start();
    }

    public EdalDataLayer(String layerName, VideoWallCatalogue catalogue, Double elevation,
            DateTime time) {
        super(makeLevels(layerName, catalogue, elevation, time));
        this.catalogue = catalogue;
        this.elevation = elevation;
        this.time = time;
        setName(layerName);
        setUseTransparentTextures(true);
        setPickEnabled(true);
        setData(layerName);
        setForceLevelZeroLoads(true);
        setRetainLevelZeroTiles(true);
    }

    private void setData(String layerName) {
        if (layerName == null) {
            /*
             * Setting this layer to display no data. This shouldn't get called.
             */
            setEnabled(false);
            mapImage = null;
            return;
        }

        Extent<Float> scaleRange;
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

        /*
         * TODO use XML styles...
         */
        ColourScheme colourScheme = new PaletteColourScheme(new ColourScale(scaleRange.getLow(),
                scaleRange.getHigh(), false), new ColourMap(Color.black, Color.black, new Color(0,
                true), "rainbow", 100));
        RasterLayer rasterLayer = new RasterLayer(layerName, colourScheme);
        mapImage = new MapImage();
        mapImage.getLayers().add(rasterLayer);
    }

    @Override
    protected synchronized void retrieveRemoteTexture(TextureTile tile,
            DownloadPostProcessor postProcessor) {
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
        try {
            ImageIO.write(image, "png", outFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        addTileToCache(tile);
    }

    protected BufferedImage createTileImage(TextureTile tile) {
        if (mapImage == null) {
            return new BufferedImage(tile.getLevel().getTileWidth(), tile.getLevel()
                    .getTileHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        }

        int width = tile.getLevel().getTileWidth();
        int height = tile.getLevel().getTileHeight();
        Sector s = tile.getSector();
        BoundingBox bbox = new BoundingBoxImpl(s.getMinLongitude().degrees,
                s.getMinLatitude().degrees, s.getMaxLongitude().degrees,
                s.getMaxLatitude().degrees, DefaultGeographicCRS.WGS84);
        /*
         * TODO make this a field and update it when necessary
         */
        PlottingDomainParams params = new PlottingDomainParams(width, height, bbox, null, null,
                null, elevation, time);

        try {
            BufferedImage ret = mapImage.drawImage(params, catalogue);
            return ret;
        } catch (EdalException e) {
            BufferedImage image = new BufferedImage(tile.getLevel().getTileWidth(), tile.getLevel()
                    .getTileHeight(), BufferedImage.TYPE_4BYTE_ABGR);
            e.printStackTrace();
            // Draw a red cross on white background
            Graphics2D g2 = image.createGraphics();
            g2.setPaint(Color.WHITE);
            g2.fillRect(0, 0, width, height);
            g2.setPaint(Color.RED);
            g2.drawLine(0, 0, width, height);
            g2.drawLine(0, height, width, 0);
            return image;
        }
    }

    private static LevelSet makeLevels(String layerName, VideoWallCatalogue catalogue,
            Double elevation, DateTime time) {
        String layerId = createLayerUUID(layerName, elevation, time);
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

    private static String createLayerUUID(String layerName, Double elevation, DateTime time) {
        return layerName + "-" + elevation + "-" + time;
    }

    @Override
    public String toString() {
        return "Edal Data Layer";
    }

    @Override
    public void selected(SelectEvent event) {
        System.out.println("select event");
    }
}

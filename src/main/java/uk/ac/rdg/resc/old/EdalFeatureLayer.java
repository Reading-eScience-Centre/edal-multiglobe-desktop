/*
Copyright (C) 2001, 2006 United States Government
as represented by the Administrator of the
National Aeronautics and Space Administration.
All Rights Reserved.
 */
package uk.ac.rdg.resc.old;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.util.LevelSet;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.OnTheFlyTiledImageLayer;
import uk.ac.rdg.resc.VideoWallCatalogue;
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

/**
 * Procedural layer example
 * 
 * @author Patrick Murris
 * @version $Id:$
 */
public class EdalFeatureLayer extends OnTheFlyTiledImageLayer implements SelectListener {
    private VideoWallCatalogue catalogue;
    private MapImage mapImage = null;

    private Double elevation = null;
    private DateTime time = null;

    public EdalFeatureLayer(String layerName, VideoWallCatalogue catalogue) {
        super(makeLevels(layerName, catalogue));
        this.catalogue = catalogue;
        setName(layerName);
        setUseTransparentTextures(true);
        setPickEnabled(true);
        
        setForceLevelZeroLoads(false);
        setRetainLevelZeroTiles(false);

        setData(layerName);
    }

    public void setElevation(Double elevation) {
        this.elevation = elevation;
        firePropertyChange(AVKey.LAYER, null, this);
    }

    public void setTime(DateTime time) {
        this.time = time;
        firePropertyChange(AVKey.LAYER, null, this);
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
            if (metadata.getTemporalDomain() != null) {
                time = metadata.getTemporalDomain().getExtent().getHigh();
            }
            if (metadata.getVerticalDomain() != null) {
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
        ColourScheme colourScheme = new SegmentColourScheme(new ColourScale(
                scaleRange.getLow(), scaleRange.getHigh(), false), Color.black, Color.black,
                new Color(0, true), "rainbow", 100);
        RasterLayer raster = new RasterLayer(layerName, colourScheme);

        mapImage = new MapImage();
        mapImage.getLayers().add(raster);
    }

    @Override
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

    private BufferedImage missingImage(TextureTile tile) {
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

    private static LevelSet makeLevels(String layerName, VideoWallCatalogue catalogue) {
        String layerId = createLayerUUID(layerName);
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

    private static String createLayerUUID(String layerName) {
        return layerName;
    }

    @Override
    public String toString() {
        return "Edal Feature Layer";
    }

    @Override
    public void selected(SelectEvent event) {
        System.out.println("select event");
    }
}

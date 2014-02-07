/*
Copyright (C) 2001, 2006 United States Government
as represented by the Administrator of the
National Aeronautics and Space Administration.
All Rights Reserved.
 */
package uk.ac.rdg.resc;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.avlist.AVListImpl;
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
import org.joda.time.chrono.ISOChronology;

import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourMap;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.MapImage;
import uk.ac.rdg.resc.edal.graphics.style.PaletteColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.RasterLayer;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

/**
 * Procedural layer example
 * 
 * @author Patrick Murris
 * @version $Id:$
 */
public class ProceduralTestLayer extends ProceduralTiledImageLayer {
    private FeatureCatalogue catalogue;
    private MapImage mapImage;

    public ProceduralTestLayer(String layerName, FeatureCatalogue catalogue, float min, float max) {
        super(makeLevels(layerName));
        this.catalogue = catalogue;
        setName("edal-layer");
        this.setUseTransparentTextures(true);

        ColourScheme colourScheme = new PaletteColourScheme(new ColourScale(min, max, false),
                new ColourMap(Color.black, Color.black, new Color(0, true), "rainbow", 100));
        RasterLayer rasterLayer = new RasterLayer(layerName, colourScheme);
        mapImage = new MapImage();
        mapImage.getLayers().add(rasterLayer);
    }

    private static LevelSet makeLevels(String layerName) {
        AVList params = new AVListImpl();

        params.setValue(AVKey.TILE_WIDTH, 128);
        params.setValue(AVKey.TILE_HEIGHT, 128);
        params.setValue(AVKey.DATA_CACHE_NAME, "EDAL/Tiles/"+layerName);
        params.setValue(AVKey.SERVICE, "*");
        params.setValue(AVKey.DATASET_NAME, "*");
        params.setValue(AVKey.FORMAT_SUFFIX, ".png");
        params.setValue(AVKey.NUM_LEVELS, 10);
        params.setValue(AVKey.NUM_EMPTY_LEVELS, 0);
        params.setValue(AVKey.LEVEL_ZERO_TILE_DELTA,
                new LatLon(Angle.fromDegrees(36d), Angle.fromDegrees(36d)));
        params.setValue(AVKey.SECTOR, Sector.FULL_SPHERE);

        return new LevelSet(params);
    }

    @Override
    protected BufferedImage createTileImage(TextureTile tile, BufferedImage image) {
        int width = tile.getLevel().getTileWidth();
        int height = tile.getLevel().getTileHeight();
        Sector s = tile.getSector();
        BoundingBox bbox = new BoundingBoxImpl(s.getMinLongitude().degrees, s.getMinLatitude().degrees,
                s.getMaxLongitude().degrees, s.getMaxLatitude().degrees, DefaultGeographicCRS.WGS84);
        PlottingDomainParams params = new PlottingDomainParams(width, height, bbox, null, null,
                null, 5.0, new DateTime(2010, 07, 15, 12, 00, ISOChronology.getInstance()));

        try {
            BufferedImage ret = mapImage.drawImage(params, catalogue);
            return ret;
        } catch (EdalException e) {
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

    @Override
    public String toString() {
        return "Procedural Test Layer";
    }
}

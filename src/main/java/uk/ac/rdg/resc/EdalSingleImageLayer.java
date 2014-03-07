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
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.render.SurfaceImage;
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
import uk.ac.rdg.resc.edal.domain.HorizontalDomain;
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
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
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
public class EdalSingleImageLayer extends RenderableLayer implements SelectListener {
    private VideoWallCatalogue catalogue;
    private MapImage mapImage = null;

    private DateTime time;// = new DateTime(2010, 07, 15, 12, 00, ISOChronology.getInstanceUTC());
    private Double elevation;// = 5.0;

    private SurfaceImage dataImage = null;

    public EdalSingleImageLayer(String layerName, VideoWallCatalogue catalogue, Double elevation,
            DateTime time) {
        super();
        this.catalogue = catalogue;
        this.elevation = elevation;
        this.time = time;
        setName(layerName);
        setPickEnabled(true);
        
        dataImage = new SurfaceImage();
        addRenderable(dataImage);
        setData(layerName);
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
            /*
             * TODO use XML styles...
             */
            ColourScheme colourScheme = new PaletteColourScheme(new ColourScale(scaleRange.getLow(),
                    scaleRange.getHigh(), false), new ColourMap(Color.black, Color.black, new Color(0,
                            true), "rainbow", 100));
            RasterLayer rasterLayer = new RasterLayer(layerName, colourScheme);
            mapImage = new MapImage();
            mapImage.getLayers().add(rasterLayer);

            HorizontalDomain horizontalDomain = metadata.getHorizontalDomain();
            if (horizontalDomain instanceof HorizontalGrid) {
                HorizontalGrid grid = (HorizontalGrid) horizontalDomain;
                int width = grid.getXSize();
                int height = grid.getYSize();
                BoundingBox bbox = grid.getBoundingBox();
                PlottingDomainParams params = new PlottingDomainParams(width, height,
                        bbox, null, null, null, elevation, time);
                BufferedImage image = mapImage.drawImage(params, catalogue);
                Sector sector = Sector.fromDegrees(bbox.getMinY(), bbox.getMaxY(), bbox.getMinX(), bbox.getMaxX());
                dataImage.setImageSource(image, sector);
            }
        } catch (EdalException e) {
            e.printStackTrace();
            /*
             * TODO log this properly
             */
            return;
        }

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

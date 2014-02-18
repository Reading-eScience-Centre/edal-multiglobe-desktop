/*
Copyright (C) 2001, 2006 United States Government
as represented by the Administrator of the
National Aeronautics and Space Administration.
All Rights Reserved.
 */
package uk.ac.rdg.resc;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwindx.examples.analytics.AnalyticSurface;
import gov.nasa.worldwindx.examples.analytics.AnalyticSurface.GridPointAttributes;
import gov.nasa.worldwindx.examples.analytics.AnalyticSurfaceAttributes;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;
import org.joda.time.chrono.ISOChronology;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.HorizontalDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBox;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourMap;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.PaletteColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue.FeaturesAndMemberName;
import uk.ac.rdg.resc.edal.grid.HorizontalGrid;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.util.Array2D;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

/**
 * This is a test of using AnalyticSurfaces to display EDAL data.  I don't think it's fast enough, but it's kept just in case
 */
public class EdalAnalyticSurfaceLayer extends RenderableLayer implements SelectListener {
    private VideoWallCatalogue catalogue;

    private String currentLayerName = null;

    private DateTime time = new DateTime(2010, 07, 15, 12, 00, ISOChronology.getInstanceUTC());
    private Double elevation = 5.0;
    private AnalyticSurface surface;

    public EdalAnalyticSurfaceLayer(String layerId, VideoWallCatalogue catalogue) {
        super();
        this.catalogue = catalogue;
        setName(layerId);
        setPickEnabled(true);

        surface = new AnalyticSurface();
        surface.setAltitudeMode(WorldWind.CLAMP_TO_GROUND);
        surface.setClientLayer(this);

        AnalyticSurfaceAttributes attr = new AnalyticSurfaceAttributes();
        attr.setDrawShadow(false);
        attr.setDrawOutline(false);
        attr.setInteriorOpacity(1.0);
        surface.setSurfaceAttributes(attr);

        addRenderable(surface);
    }

    public String getDataLayerName() {
        return currentLayerName;
    }

    public boolean isShowingData() {
        return !(currentLayerName == null || currentLayerName.equals(""));
    }

    public void setData(String layerName) {
        if (layerName == null || layerName.equals("")) {
            /*
             * Setting this layer to display no data
             */
            setEnabled(false);
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
        if (layerName.equals(currentLayerName)) {
            return;
        } else {
            currentLayerName = layerName;
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

            HorizontalDomain horizontalDomain = metadata.getHorizontalDomain();
            if (horizontalDomain instanceof HorizontalGrid) {
                HorizontalGrid horizontalGrid = (HorizontalGrid) horizontalDomain;
                int width = horizontalGrid.getXSize();
                int height = horizontalGrid.getYSize();
                BoundingBox bboxGlobal = new BoundingBoxImpl(-179.5, -89.5, 179.5, 89.5, DefaultGeographicCRS.WGS84);
                PlottingDomainParams params = new PlottingDomainParams(width, height,
                        bboxGlobal, null, null, null, elevation, time);
                FeaturesAndMemberName features = catalogue.getFeaturesForLayer(layerName, params);
                ColourScheme colourScheme = new PaletteColourScheme(new ColourScale(
                        scaleRange.getLow(), scaleRange.getHigh(), false), new ColourMap(
                        Color.black, Color.black, new Color(0, true), "rainbow", 100));
                
                surface.setSector(Sector.fromDegrees(-89.5, 89.5, -179.5, 179.5));

                MapFeature mapFeature = null;
                Collection<? extends DiscreteFeature<?, ?>> mapFeatures = features.getFeatures();
                if(mapFeatures.size() > 1) {
                    System.out.println("Multiple grid features!");
                }
                for (DiscreteFeature<?, ?> feature : mapFeatures) {
                    if (feature instanceof MapFeature) {
                        mapFeature = (MapFeature) feature;

                    } else {
                        System.out.println("Not a grid feature: " + feature.getClass());
                    }
                }
                if(mapFeature != null) {
                    BoundingBox bbox = mapFeature.getDomain().getBoundingBox();
                    surface.setSector(Sector.fromDegrees(bbox.getMinY(), bbox.getMaxY(), bbox.getMinX(), bbox.getMaxX()));
                    surface.setDimensions(width, height);
                    surface.setValues(getValuesFromMapFeature(mapFeature,
                            features.getMember(), colourScheme));
                    firePropertyChange(AVKey.LAYER, null, this);
                }
            } else {
                System.out.println("Not a gridded domain");
            }
        } catch (EdalException e) {
            e.printStackTrace();
            /*
             * TODO log this properly
             */
            return;
        }
    }

    private Iterable<? extends GridPointAttributes> getValuesFromMapFeature(MapFeature mapFeature,
            String member, ColourScheme colourScheme) {
        ArrayList<AnalyticSurface.GridPointAttributes> attributesList = new ArrayList<AnalyticSurface.GridPointAttributes>();

        Array2D<Number> values = mapFeature.getValues(member);
        for (int j = 0; j < mapFeature.getDomain().getYSize(); j++) {
            for (int i = 0; i < mapFeature.getDomain().getXSize(); i++) {
                Number value = values.get(values.getShape()[0] - j - 1, i);
                Color color = colourScheme.getColor(value);
                /*
                 * value refers to the height, so we can set this to 0.0
                 */
                attributesList.add(AnalyticSurface.createGridPointAttributes(0.0, color));
            }
        }

        return attributesList;
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

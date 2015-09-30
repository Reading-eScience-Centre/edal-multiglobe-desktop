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

import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.MarkerLayer;
import gov.nasa.worldwind.render.Material;
import gov.nasa.worldwind.render.markers.BasicMarker;
import gov.nasa.worldwind.render.markers.BasicMarkerAttributes;
import gov.nasa.worldwind.render.markers.BasicMarkerShape;
import gov.nasa.worldwind.render.markers.Marker;
import gov.nasa.worldwind.render.markers.MarkerAttributes;
import gov.nasa.worldwind.util.Logging;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.TemporalDomain;
import uk.ac.rdg.resc.edal.domain.VerticalDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.ScaleRange;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.EnhancedVariableMetadata;
import uk.ac.rdg.resc.edal.graphics.style.util.PlottingStyleParameters;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.logging.RescLogging;

/**
 * Class for handling profile EDAL data and displaying it
 * 
 * @author Guy Griffiths
 */
public class EdalProfileDataLayer extends MarkerLayer implements EdalDataLayer, SelectListener {
    private static final Color TRANSPARENT = new Color(0, true);
    private static final double MARKER_SIZE = 5.0;

    /** The ID of the layer in the EDAL system */
    private final String layerName;
    /** The {@link VideoWallCatalogue} containing the layer */
    private VideoWallCatalogue catalogue;
    /** The {@link RescWorldWindow} which will display the layer */
    private RescWorldWindow wwd;

    /** The current elevation */
    private Double elevation;
    /** The current elevation range */
    private Extent<Double> elevationRange;
    /** The vertical domain for the data */
    private VerticalDomain zDomain;
    /** The current time */
    private DateTime time;
    /** The current time range */
    private Extent<DateTime> timeRange;
    /** The temporal domain for the data */
    private TemporalDomain tDomain;
    /** The {@link VariableMetadata} associated with the layer */
    private VariableMetadata metadata;

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
    
    /** The {@link ColourScheme} to use for this layer */
    private SegmentColourScheme colourScheme;
    /**
     * A list of {@link ProfileFeature}s which have values in the given time
     * range
     */
    private List<ProfileFeature> features;
    /**
     * A list of {@link Marker}s which representing {@link ProfileFeature}s
     * which are available for the given time range
     */
    private List<Marker> markers;
    /**
     * A list of {@link Marker}s which representing {@link ProfileFeature}s
     * which are available for the given time range AND which intersect the
     * currently-selected elevation range
     */
    private List<Marker> activeMarkers;
    /** The ID of the variable being plotted */
    private String varId;

    /** Keep track of the last marker to be highlighted so that we can make it bigger */
    private Marker lastHighlit = null;

    /**
     * Instantiate a new {@link EdalProfileDataLayer}
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
     *             If the requested layer name is not available in the
     *             {@link VideoWallCatalogue}
     */
    public EdalProfileDataLayer(String layerName, VideoWallCatalogue catalogue,
            RescWorldWindow wwd) throws EdalException {
        this.layerName = layerName;
        this.catalogue = catalogue;
        this.wwd = wwd;

        metadata = catalogue.getVariableMetadataForLayer(layerName);

        zDomain = metadata.getVerticalDomain();
        tDomain = metadata.getTemporalDomain();

        /*
         * Set a default elevation
         */
        if (zDomain != null) {
            this.elevation = GISUtils.getClosestElevationToSurface(zDomain);
            this.elevationRange = Extents.newExtent(this.elevation, this.elevation);
        }
        /*
         * Set a default time
         */
        if (tDomain != null) {
            this.time = GISUtils.getClosestToCurrentTime(tDomain);
            this.timeRange = Extents.newExtent(this.time, this.time);
        }

        EnhancedVariableMetadata enhancedMetadata = catalogue.getLayerMetadata(catalogue
                .getVariableMetadataForLayer(layerName));
        /*
         * Set up the default colour scale values
         */
        if (enhancedMetadata != null) {
            PlottingStyleParameters plottingMetadata = enhancedMetadata
                    .getDefaultPlottingParameters();
            scaleRange = plottingMetadata.getColorScaleRange();
            palette = plottingMetadata.getPalette();
            logScale = plottingMetadata.isLogScaling();
            numColorBands = plottingMetadata.getNumColorBands();
            bgColor = plottingMetadata.getNoDataColour();
            underColor = plottingMetadata.getBelowMinColour();
            overColor = plottingMetadata.getAboveMaxColour();
        }
        /*
         * Make out of range slightly transparent
         */
        underColor = new Color(underColor.getRed(), underColor.getGreen(), underColor.getBlue(), 64);
        overColor = new Color(overColor.getRed(), overColor.getGreen(), overColor.getBlue(), 64);

        colourSchemeChanged();
        
        features = new ArrayList<>();
        markers = new ArrayList<>();
        activeMarkers = new ArrayList<>();

        setName(layerName);
        setPickEnabled(true);

        wwd.addSelectListener(this);

        extractNewProfiles();
    }

    @Override
    public void setDataElevation(double elevation, Extent<Double> elevationRange) {
        if (elevation > zDomain.getExtent().getHigh()) {
            this.elevation = zDomain.getExtent().getHigh();
        } else if (elevation < zDomain.getExtent().getLow()) {
            this.elevation = zDomain.getExtent().getLow();
        } else {
            this.elevation = elevation;
        }
        this.elevationRange = elevationRange;
        redrawExistingProfiles();
    }

    @Override
    public Double getDataElevation() {
        return this.elevation;
    }

    @Override
    public void setTime(DateTime time, Extent<DateTime> timeRange) {
        this.time = GISUtils.getClosestTimeTo(time, tDomain);
        this.timeRange = timeRange;
        extractNewProfiles();
    }

    @Override
    public DateTime getTime() {
        return this.time;
    }

    @Override
    public void scaleLimitsChanged(Extent<Float> newScaleRange) {
        this.scaleRange = newScaleRange;
        colourSchemeChanged();
        redrawExistingProfiles();
    }

    @Override
    public void paletteChanged(String newPalette) {
        this.palette = newPalette;
        colourSchemeChanged();
        redrawExistingProfiles();
    }

    @Override
    public void aboveMaxColourChanged(Color aboveMax) {
        this.overColor = aboveMax;
        colourSchemeChanged();
        redrawExistingProfiles();
    }

    @Override
    public void belowMinColourChanged(Color belowMin) {
        this.underColor = belowMin;
        colourSchemeChanged();
        redrawExistingProfiles();
    }

    @Override
    public void setNumColourBands(int numColourBands) {
        this.numColorBands = numColourBands;
        colourSchemeChanged();
        redrawExistingProfiles();
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
        colourSchemeChanged();
        redrawExistingProfiles();
    }

    /**
     * This re-calculates the colour scheme based on the values of the enclosing
     * {@link EdalProfileDataLayer}, and should be called when any values have
     * been changed.
     */
    public void colourSchemeChanged() {
        ScaleRange colourScale = new ScaleRange(scaleRange, logScale);
        colourScheme = new SegmentColourScheme(colourScale, underColor, overColor, bgColor,
                palette, numColorBands);
    }

    @Override
    public VariableMetadata getVariableMetadata() {
        return metadata;
    }

    @Override
    public PlottingStyleParameters getPlottingMetadata() {
        return new PlottingStyleParameters(scaleRange, palette, overColor,
                underColor, bgColor, logScale, numColorBands, 1f);
    }

    @Override
    public BufferedImage getLegend(int size, boolean labels) {
        return colourScheme.getScaleBar(LEGEND_WIDTH, size, 0.05f, true, true, Color.lightGray,
                new Color(0, 0, 0, 150));
    }

    /**
     * Does the actual drawing of the layer. This gets called when something has
     * changed and the layer needs redrawing.
     */
    private void extractNewProfiles() {
        features = new ArrayList<>();
        markers = new ArrayList<>();
        activeMarkers = new ArrayList<>();

        /*
         * Start the feature extraction in a new thread, so that we don't get a
         * pause
         */
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                List<? extends ProfileFeature> profileFeatures;
                try {
                    Dataset dataset = catalogue.getDatasetFromLayerName(layerName);
                    varId = catalogue.getVariableMetadataForLayer(layerName).getId();
                    profileFeatures = dataset.extractProfileFeatures(CollectionUtils.setOf(varId),
                            new PlottingDomainParams(1, 1, BoundingBoxImpl.global(),
                                    getVariableMetadata().getVerticalDomain().getExtent(),
                                    timeRange, null, elevation, time));
                } catch (VariableNotFoundException e) {
                    String message = RescLogging.getMessage("resc.NoLayer", layerName);
                    Logging.logger().severe(message);
                    return;
                } catch (EdalException e) {
                    String message = RescLogging.getMessage("resc.DataReadingProblem");
                    Logging.logger().warning(message);
                    return;
                }

                for (ProfileFeature profile : profileFeatures) {
                    MarkerAttributes attrs = new BasicMarkerAttributes(new Material(TRANSPARENT),
                            BasicMarkerShape.SPHERE, 1.0);
                    attrs.setMarkerPixels(MARKER_SIZE);

                    Marker marker = new BasicMarker(new Position(new LatLon(Angle
                            .fromDegrees(profile.getHorizontalPosition().getY()), Angle
                            .fromDegrees(profile.getHorizontalPosition().getX())), 0.0), attrs);

                    features.add(profile);
                    markers.add(marker);
                }
                redrawExistingProfiles();
            }
        });
        this.setPickEnabled(true);
        wwd.addSelectListener(this);
        wwd.redraw();
    }

    /**
     * This method refreshes the colours of any profiles which are plotted on
     * the map. Because the entirety of each profile is extracted when a layer
     * is created, this should be called when the elevation or colour scheme has
     * changed. However, when the time values of the parent
     * {@link EdalProfileDataLayer} change, new features need to be extracted,
     * so calling this method will not do anything.
     */
    private void redrawExistingProfiles() {
        activeMarkers.clear();
        /*
         * Go through map of profiles -> markers and set the colour
         */
        for (int i = 0; i < features.size(); i++) {
            ProfileFeature profile = features.get(i);

            int zIndex = GISUtils.getIndexOfClosestElevationTo(elevation, profile.getDomain());

            Color markerColor;
            if (profile.getDomain().getExtent().intersects(elevationRange) && zIndex >= 0) {
                /*
                 * We want to draw this marker.
                 */
                Number value = profile.getValues(varId).get(zIndex);
                markerColor = colourScheme.getColor(value);
                if (markerColor.getAlpha() == 0) {
                    /*
                     * We don't want transparent markers to be 100% transparent
                     * (as in gridded fields), so we set the colour to be ~25%
                     * instead
                     */
                    markerColor = new Color(markerColor.getRed(), markerColor.getGreen(),
                            markerColor.getBlue(), 64);
                }
                if (getOpacity() < 1.0) {
                    markerColor = new Color(markerColor.getRed(), markerColor.getGreen(),
                            markerColor.getBlue(), (int) (markerColor.getAlpha() * getOpacity()));
                }
                markers.get(i).getAttributes().setMaterial(new Material(markerColor));
                activeMarkers.add(markers.get(i));
            }
        }
        setMarkers(activeMarkers);
    }

    @Override
    public void setOpacity(double opacity) {
        super.setOpacity(opacity);
        redrawExistingProfiles();
    }

    @Override
    public void selected(SelectEvent event) {
        /*
         * For profiles, we handle the select events here. This is different to
         * gridded data, where the Layer is not pickable in the same way
         */
        if (lastHighlit != null
                && (!event.hasObjects() || !event.getTopObject().equals(lastHighlit))) {
            lastHighlit.getAttributes().setMarkerPixels(MARKER_SIZE);
            lastHighlit = null;
        }
        if (event.hasObjects()) {
            Object topObject = event.getTopObject();
            if (topObject instanceof Marker) {
                if (event.isRollover()) {
                    lastHighlit = (Marker) topObject;
                    lastHighlit.getAttributes().setMarkerPixels(MARKER_SIZE * 1.5);
                } else if (event.isLeftClick()) {
                    wwd.getModel().showFeatureInfo(((Marker) topObject).getPosition(), true);
                }
            }
        }
    }
}

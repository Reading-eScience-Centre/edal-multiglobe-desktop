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

import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.globes.FlatGlobe;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.util.Logging;

import java.awt.Color;
import java.io.File;
import java.text.DecimalFormat;
import java.util.List;
import java.util.concurrent.Executors;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.EdalGridDataLayer.CacheListener;
import uk.ac.rdg.resc.LinkedView.LinkedViewState;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.TemporalDomain;
import uk.ac.rdg.resc.edal.domain.VerticalDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.graphics.Charting;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.VerticalCrs;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.TimeUtils;
import uk.ac.rdg.resc.edal.wms.exceptions.EdalLayerNotFoundException;
import uk.ac.rdg.resc.logging.RescLogging;
import uk.ac.rdg.resc.widgets.FeatureInfoBalloon;
import uk.ac.rdg.resc.widgets.SliderWidget.SliderWidgetHandler;
import uk.ac.rdg.resc.widgets.SliderWidgetAnnotation;

/**
 * A {@link Model} for displaying data using the EDAL libraries.
 * 
 * @author Guy Griffiths
 */
public class RescModel extends BasicModel implements SliderWidgetHandler, CacheListener {
    /*
     * IDs for the 2 sliders which may be present
     */
    private final static String ELEVATION_SLIDER = "depth-slider";
    private final static String TIME_SLIDER = "time-slider";

    /**
     * A reference to the parent frame so that we can access other RescModels to
     * link various properties (time/elevation sliders, colour scales, etc.)
     */
    private MultiGlobeFrame parent;
    /**
     * A reference to the associated WorldWindow for various purposes
     */
    private RescWorldWindow wwd;

    /*
     * Keep an instance of a globe and a flat map to switch between the two
     */
    private Earth globe;
    private EarthFlat flatMap;
    private boolean flat = false;
    /* The projection used for the flat map */
    private Projection flatProjection = Projection.MERCATOR;

    /** Access to the data catalogue */
    private VideoWallCatalogue catalogue;

    /** The layer to display data on */
    private EdalDataLayer edalDataLayer = null;
    /** The name of the currently selected data layer */
    private String edalLayerName = null;

    /**
     * A layer holding buttons, legend, and the palette selector
     */
    private EdalConfigLayer edalConfigLayer;

    /**
     * A layer holding the sliders and any FeatureInfoBalloons
     */
    private AnnotationLayer annotationLayer;
    /**
     * A layer to hold full-screen graphs. This is separate from the other
     * annotation layer because it should be on top of all other layers
     */
    private AnnotationLayer fullScreenAnnotationLayer;

    /*
     * Sliders to control the time/elevation value displayed
     */
    private SliderWidgetAnnotation timeSlider = null;
    private SliderWidgetAnnotation elevationSlider = null;
    private String elevationUnits = "";

    /**
     * A reference to the current feature info balloon so that we can close it
     * if a user clicks somewhere else
     */
    private FeatureInfoBalloon balloon = null;

    /**
     * Construct a new {@link RescModel}
     * 
     * @param catalogue
     *            The {@link VideoWallCatalogue} containing data to make
     *            available for display
     * @param wwd
     *            The {@link RescWorldWindow} to which this model belongs
     * @param parent
     *            The {@link MultiGlobeFrame} which is the parent of the
     *            supplied {@link RescWorldWindow}. This gives access to other
     *            {@link RescModel}s which are being displayed
     */
    public RescModel(VideoWallCatalogue catalogue, RescWorldWindow wwd, MultiGlobeFrame parent) {
        super();

        this.catalogue = catalogue;
        this.wwd = wwd;
        this.parent = parent;

        globe = new Earth();
        /*
         * Disable terrain elevation - not useful for the data visualisation we
         * are doing
         */
        globe.getElevationModel().setEnabled(false);
        setGlobe(globe);

        flatMap = new EarthFlat();

        /*
         * Add the appropriate layers in the correct order
         */
        annotationLayer = new AnnotationLayer();
        getLayers().add(annotationLayer);

        edalConfigLayer = new EdalConfigLayer(wwd, catalogue);
        getLayers().add(edalConfigLayer);

        fullScreenAnnotationLayer = new AnnotationLayer();
        getLayers().add(fullScreenAnnotationLayer);
    }

    public RescWorldWindow getWorldWindow() {
        return wwd;
    }

    public EdalConfigLayer getConfigLayer() {
        return edalConfigLayer;
    }

    /**
     * Sets the display to use either a globe or a flat map
     * 
     * @param flat
     *            <code>true</code> if a flat map is required,
     *            <code>false</code> for a 3d globe
     */
    public void setFlat(boolean flat) {
        this.flat = flat;
        if (flat) {
            setGlobe(flatMap);
        } else {
            setGlobe(globe);
        }
    }

    /**
     * @param projection
     *            The projection to use when displaying a flat map
     */
    public void setProjection(Projection projection) {
        this.flatProjection = projection;
    }

    /**
     * @return <code>true</code> if a flat map is currently selected,
     *         <code>false</code> if a 3d globe is selected
     */
    public boolean isFlat() {
        return flat;
    }

    /**
     * @return The projection being used for the flat map
     */
    public Projection getProjection() {
        return flatProjection;
    }

    /**
     * @return The {@link EdalDataLayer} which this model is displaying
     */
    public EdalDataLayer getDataLayer() {
        return edalDataLayer;
    }

    /**
     * Cycles through the flat map projections
     */
    public void cycleProjection() {
        flatProjection = flatProjection.getNext();
        flatMap.setProjection(flatProjection.getKey());
    }

    /**
     * Chooses a data layer to display.
     * 
     * @param layerName
     *            The ID of the layer to display in the model
     * @throws EdalLayerNotFoundException
     *             If the ID supplied dose not represent a layer in the
     *             {@link VideoWallCatalogue} attached to this {@link RescModel}
     */
    public void setDataLayer(String layerName) throws EdalLayerNotFoundException {
        System.out.println("setting data layer: " + layerName);

        /*
         * Do not do anything if no layer ID supplied, or if we are already
         * displaying this layer
         */
        if (layerName != null && !layerName.equals(edalLayerName)) {
            Dataset dataset = catalogue.getDatasetFromLayerName(layerName);

            /*
             * Instantiate the new layer in its own variable to reduce any delay
             * between removing the old layer and adding the new one
             */
            EdalDataLayer tempLayer = null;

            /*
             * Choose what type of EdalDataLayer to show
             */
            Class<? extends DiscreteFeature<?, ?>> mapFeatureType = dataset
                    .getFeatureType(catalogue.getVariableFromId(layerName));

            /*
             * Note that when we create an EdalDataLayer, it is not an instance
             * of Layer which we add to the LayerList for this model.
             * 
             * Instead it is essentially a layer management class which takes
             * care of changing elevation/time/palette etc. and adds/removes the
             * layers to the list when required. This means that we must pass it
             * the LayerList for this model so that it can add/remove layers
             * when necessary.
             */
            if (GridFeature.class.isAssignableFrom(mapFeatureType)) {
                try {
                    tempLayer = new EdalGridDataLayer(layerName, catalogue, getLayers(), wwd, this);
                } catch (EdalException e) {
                    String message = RescLogging.getMessage("resc.BadGridLayer");
                    Logging.logger().severe(message);
                    e.printStackTrace();
                    return;
                }
            } else if (ProfileFeature.class.isAssignableFrom(mapFeatureType)) {
                try {
                    tempLayer = new EdalProfileDataLayer(layerName, catalogue, getLayers(), wwd);
                } catch (EdalException e) {
                    String message = RescLogging.getMessage("resc.BadProfileLayer");
                    Logging.logger().severe(message);
                    e.printStackTrace();
                    return;
                }
            } else {
                String message = RescLogging
                        .getMessage("resc.UnsupportedLayerType", mapFeatureType);
                Logging.logger().severe(message);
                return;
            }
            /*
             * We have successfully instantiated the layer with no errors, so
             * now we assign it to gridDataLayer
             */
            if (edalDataLayer != null) {
                /*
                 * Destroy the previous layer. This will remove all data layers
                 * from the layer list
                 */
                edalDataLayer.destroy();
            }

            edalDataLayer = tempLayer;
            /*
             * Wire up this data layer to the config layer which handles changes
             * in colour scale
             */
            edalConfigLayer.setPaletteHandler(edalDataLayer);
            edalConfigLayer.setLegend(edalDataLayer);

            /*
             * Remove all annotations. This will get rid of the time and
             * elevation sliders (new ones get put back if required), and also
             * any feature info balloons.
             */
            annotationLayer.removeAllAnnotations();

            /*
             * Set the time/elevation sliders
             */
            VariableMetadata layerMetadata = edalDataLayer.getVariableMetadata();
            if (layerMetadata != null) {
                addSliders(layerMetadata);
            }

            edalLayerName = layerName;
        }
    }

    /**
     * Changes the elevation slider by a fraction of the whole range
     * 
     * @param frac
     *            The fraction to change the slider by
     */
    public void changeElevationSlider(double frac) {
        if (elevationSlider != null) {
            elevationSlider.changeByFrac(frac);
        }
    }

    /**
     * Changes the time slider by a fraction of the whole range
     * 
     * @param frac
     *            The fraction to change the slider by
     */
    public void changeTimeSlider(double frac) {
        if (timeSlider != null) {
            timeSlider.changeByFrac(frac);
        }
    }

    /**
     * Adds/adjusts the time/elevation sliders
     * 
     * @param layerMetadata
     *            The {@link VariableMetadata} corresponding to the data layer
     *            currently being displayed
     */
    private void addSliders(VariableMetadata layerMetadata) {
        /*
         * Add an elevation slider if it is required (i.e. we have a vertical
         * domain, and if it's a vertical axis, it has at least two values),
         * otherwise nullify an existing slider
         */
        VerticalDomain verticalDomain = layerMetadata.getVerticalDomain();
        if (verticalDomain != null
                && !(verticalDomain instanceof VerticalAxis && ((VerticalAxis) verticalDomain)
                        .getCoordinateValues().size() < 2)) {

            /*
             * Either create a new slider with the correct limits, or set the
             * limits on the existing one
             */
            if (elevationSlider == null) {
                elevationSlider = new SliderWidgetAnnotation(ELEVATION_SLIDER, AVKey.VERTICAL,
                        AVKey.WEST, verticalDomain.getExtent().getLow(), verticalDomain.getExtent()
                                .getHigh(), wwd, this);
            } else {
                elevationSlider.setLimits(verticalDomain.getExtent().getLow(), verticalDomain
                        .getExtent().getHigh());
            }

            VerticalCrs verticalCrs = verticalDomain.getVerticalCrs();
            if (verticalCrs != null) {
                /*
                 * Ensure that the slider treats depth and height correctly, and
                 * save the units
                 */
                if (verticalCrs.isPositiveUpwards()) {
                    elevationSlider.setReversed(false);
                } else {
                    elevationSlider.setReversed(true);
                }
                if (verticalCrs.getUnits() != null) {
                    elevationUnits = verticalCrs.getUnits();
                }
            } else {
                elevationSlider.setReversed(false);
            }

            /*
             * Set the slider value to the currently-selected elevation
             */
            elevationSlider.setSliderValue(edalDataLayer.getElevation());

            /*
             * Find all elevation sliders on other models, and if they share the
             * same limits, link with this slider
             */
            List<RescModel> allModels = parent.getAllModels();
            for (RescModel model : allModels) {
                if (model != this && model.elevationSlider != null
                        && model.elevationSlider.equalLimits(elevationSlider)) {
                    elevationSlider.linkSlider(model.elevationSlider);
                    if (model.edalDataLayer != null) {
                        /*
                         * Set the value of this slider to match that of the
                         * linked one
                         */
                        elevationSlider.setSliderValue(model.elevationSlider.getSliderValue());
                        edalDataLayer.setElevation(model.elevationSlider.getSliderValue(),
                                model.elevationSlider.getSliderRange());
                    }
                }
            }

            /*
             * Add the elevation slider
             */
            annotationLayer.addAnnotation(elevationSlider);
        } else {
            elevationSlider = null;
        }

        /*
         * Add an elevation slider if it is required (i.e. we have a temporal
         * domain, and if it's a time axis, it has at least two values),
         * otherwise nullify an existing slider
         */
        TemporalDomain tDomain = layerMetadata.getTemporalDomain();
        if (tDomain != null
                && !(tDomain instanceof TimeAxis && ((TimeAxis) tDomain).getCoordinateValues()
                        .size() < 2)) {
            /*
             * Either create a new slider with the correct limits, or set the
             * limits on the existing one.
             * 
             * For speed, sliders always work with primitive doubles, so we need
             * to convert time to a numeric value
             */
            if (timeSlider == null) {
                timeSlider = new SliderWidgetAnnotation(TIME_SLIDER, AVKey.HORIZONTAL, AVKey.SOUTH,
                        tDomain.getExtent().getLow().getMillis(), tDomain.getExtent().getHigh()
                                .getMillis(), wwd, this);
            } else {
                timeSlider.setLimits(tDomain.getExtent().getLow().getMillis(), tDomain.getExtent()
                        .getHigh().getMillis());
            }
            /*
             * Set the slider value to the currently selected time
             */
            timeSlider.setSliderValue(edalDataLayer.getTime().getMillis());

            /*
             * Find all time sliders on other models, and if they share the same
             * limits, link with this slider
             */
            List<RescModel> allModels = parent.getAllModels();
            for (RescModel model : allModels) {
                if (model != this && model.timeSlider != null
                        && model.timeSlider.equalLimits(timeSlider)) {
                    timeSlider.linkSlider(model.timeSlider);
                    if (model.edalDataLayer != null) {
                        /*
                         * Set the value of this slider to match that of the
                         * linked one
                         */
                        timeSlider.setSliderValue(model.timeSlider.getSliderValue());
                        DateTime value = new DateTime((long) model.timeSlider.getSliderValue());
                        edalDataLayer.setTime(value, model.getTimeSliderRange());
                    }
                }
            }
            /*
             * Add the time slider
             */
            annotationLayer.addAnnotation(timeSlider);
        } else {
            timeSlider = null;
        }
    }

    /**
     * @return The range of the time slider, in {@link DateTime} units. Sliders
     *         work exclusively with primitive doubles, so
     *         {@link SliderWidgetAnnotation#getSliderRange()} cannot be used to
     *         directly get a {@link DateTime} range
     */
    private Extent<DateTime> getTimeSliderRange() {
        /*
         * TODO Parameterise {@link SliderWidgetAnnotation}, keep {@link
         * SliderWidget} working with doubles
         */
        if (timeSlider == null) {
            return null;
        }
        Extent<Double> doubleRange = timeSlider.getSliderRange();
        return Extents.newExtent(new DateTime(doubleRange.getLow().longValue()), new DateTime(
                doubleRange.getHigh().longValue()));
    }

    /**
     * Displays a {@link FeatureInfoBalloon} with information about the
     * currently-displayed data layer
     * 
     * @param position
     *            The {@link Position} at which to measure data and display the
     *            {@link FeatureInfoBalloon}
     * @param replaceExisting
     *            Whether or not to pop up a new {@link FeatureInfoBalloon} if
     *            we already have one showing
     */
    public void showFeatureInfo(final Position position, boolean replaceExisting) {
        /*
         * Only display feature info if we have an active layer, and either
         * don't have an existing balloon or want to replace the existing one
         */
        if (edalLayerName != null && !edalLayerName.equals("")
                && ((balloon == null || !balloon.isActive()) || replaceExisting)) {
            /*
             * Delegate the actual work to a private method which can then be
             * called for all linked models.
             */
            doShowFeatureInfo(position);
            /*
             * Now if this view is linked with other views, and we have a time
             * axis, we may want to display feature info on the other views
             */
            if (wwd.getView().getLinkedViewState() == LinkedViewState.LINKED
                    || wwd.getView().getLinkedViewState() == LinkedViewState.ANTILINKED
                    && timeSlider != null) {
                for (RescModel model : parent.getAllModels()) {
                    /*
                     * If another model has the same time value selected and has
                     * its view in sync with this one, we want to display
                     * feature info on it as well
                     */
                    if (model != this
                            && timeSlider != null
                            && model.timeSlider != null
                            && model.timeSlider.getSliderValue() == timeSlider.getSliderValue()
                            && wwd.getView().getLinkedViewState()
                                    .equals(model.wwd.getView().getLinkedViewState())) {
                        model.doShowFeatureInfo(position);
                    }
                }
            }
        }
    }

    /**
     * Does the work of {@link RescModel#showFeatureInfo(Position, boolean)}
     * 
     * @param position
     *            The {@link Position} at which to measure data and display the
     *            {@link FeatureInfoBalloon}
     */
    private void doShowFeatureInfo(final Position position) {
        /*
         * Remove any existing balloons (we only want to show one at a time)
         */
        if (balloon != null) {
            annotationLayer.removeAnnotation(balloon);
        }
        /*
         * Create the balloon with very basic information and display it.
         */
        balloon = new FeatureInfoBalloon(position, wwd, annotationLayer, fullScreenAnnotationLayer);
        annotationLayer.addAnnotation(balloon);
        firePropertyChange(AVKey.LAYER, null, annotationLayer);

        /*
         * Now extract the value and graphs in their own thread so that the
         * balloon appears instantly
         */
        Executors.newSingleThreadExecutor().submit(new Runnable() {
            @Override
            public void run() {
                VariableMetadata metadata;
                try {
                    metadata = catalogue.getVariableMetadataForLayer(edalLayerName);
                } catch (EdalException e) {
                    /*
                     * Can't find metadata for this layer. No point in
                     * continuing
                     */
                    balloon.setInfoText("No data layer selected");
                    e.printStackTrace();
                    return;
                }
                /*
                 * First read the data value and display the feature info
                 * balloon
                 */
                Number value = null;
                try {
                    value = catalogue.getLayerValue(edalLayerName, position,
                            edalDataLayer.getElevation(), edalDataLayer.getTime(),
                            elevationSlider == null ? null : elevationSlider.getSliderRange(),
                            getTimeSliderRange(), 0.5);
                } catch (EdalException e) {
                    /*
                     * There is a problem reading the data. We log the error and
                     * continue. The feature info balloon will give a no-data
                     * message.
                     */
                    String message = RescLogging.getMessage("resc.DataReadingProblem");
                    Logging.logger().warning(message);
                    e.printStackTrace();
                }

                String valueText;
                if (value == null || Double.isNaN(value.doubleValue())) {
                    valueText = "No data value here";
                } else {
                    valueText = "Value of layer " + metadata.getId() + " is "
                            + FeatureInfoBalloon.NUMBER_3DP.format(value)
                            + metadata.getParameter().getUnits();
                }

                balloon.setInfoText(valueText);

                try {
                    /*
                     * Get the size of the panel (minus a border) to generate
                     * the fullscreen graphs
                     */
                    int width = wwd.getWidth() - 52;
                    int height = wwd.getHeight() - 52;

                    String profileLocation = null;
                    String timeseriesLocation = null;
                    if (metadata.getTemporalDomain() != null) {
                        /*
                         * Generate a timeseries graph
                         */
                        double sensitivity = 1;
                        List<? extends PointSeriesFeature> timeseries = catalogue.getTimeseries(
                                edalLayerName, position, sensitivity,
                                elevationSlider == null ? null : elevationSlider.getSliderRange(),
                                getTimeSliderRange());

                        if (timeseries != null && timeseries.size() > 0) {
                            if (edalDataLayer instanceof EdalGridDataLayer) {
                                /*
                                 * If we have a gridded feature, we only want a
                                 * profile from the nearest point plotted.
                                 * 
                                 * For a profile dataset it is less clear which
                                 * one we require so we plot the closest 5
                                 */
                                timeseries = timeseries.subList(0, 1);
                            } else {
                                timeseries = timeseries.subList(0, timeseries.size() >= 5 ? 5
                                        : timeseries.size());
                            }

                            JFreeChart timeseriesChart = Charting.createTimeSeriesPlot(timeseries,
                                    new HorizontalPosition(position.longitude.degrees,
                                            position.latitude.degrees, DefaultGeographicCRS.WGS84));
                            timeseriesChart.setBackgroundPaint(Color.white);

                            /*
                             * Save the chart at the same size as the panel
                             */
                            timeseriesLocation = "EDAL/Charts/TS-" + edalLayerName
                                    + System.currentTimeMillis();
                            File timeseriesFile = WorldWind.getDataFileStore().newFile(
                                    timeseriesLocation);
                            ChartUtilities.saveChartAsPNG(timeseriesFile, timeseriesChart, width,
                                    height);
                            /*
                             * Now save a fixed-ratio preview chart
                             */
                            File timeseriesPreviewFile = WorldWind.getDataFileStore().newFile(
                                    timeseriesLocation + "-preview");
                            ChartUtilities
                                    .saveChartAsPNG(
                                            timeseriesPreviewFile,
                                            timeseriesChart,
                                            (int) (FeatureInfoBalloon.TARGET_WIDTH / FeatureInfoBalloon.PREVIEW_SCALE),
                                            (int) (FeatureInfoBalloon.TARGET_HEIGHT / FeatureInfoBalloon.PREVIEW_SCALE));
                        }
                    }
                    if (metadata.getVerticalDomain() != null) {
                        /*
                         * Generate a profile graph
                         */
                        double sensitivity = 1;
                        List<? extends ProfileFeature> profiles = catalogue.getProfiles(
                                edalLayerName, position, sensitivity,
                                elevationSlider == null ? null : elevationSlider.getSliderRange(),
                                getTimeSliderRange());
                        if (profiles != null && profiles.size() > 0) {
                            if (edalDataLayer instanceof EdalGridDataLayer) {
                                /*
                                 * If we have a gridded feature, we only want a
                                 * profile from the nearest point plotted.
                                 * 
                                 * For a profile dataset it is less clear which
                                 * one we require so we plot the closest 5
                                 */
                                profiles = profiles.subList(0, 1);
                            } else {
                                profiles = profiles.subList(0,
                                        profiles.size() >= 5 ? 5 : profiles.size());
                            }
                            JFreeChart profileChart = Charting.createVerticalProfilePlot(profiles,
                                    new HorizontalPosition(position.longitude.degrees,
                                            position.latitude.degrees, DefaultGeographicCRS.WGS84));
                            profileChart.setBackgroundPaint(Color.white);

                            /*
                             * Save the chart at the same size as the panel
                             */
                            profileLocation = "EDAL/Charts/PF-" + edalLayerName
                                    + System.currentTimeMillis();
                            File profileFile = WorldWind.getDataFileStore()
                                    .newFile(profileLocation);
                            ChartUtilities.saveChartAsPNG(profileFile, profileChart, width, height);
                            /*
                             * Now save a fixed-ratio preview chart
                             */
                            File profilePreviewFile = WorldWind.getDataFileStore().newFile(
                                    profileLocation + "-preview");
                            ChartUtilities
                                    .saveChartAsPNG(
                                            profilePreviewFile,
                                            profileChart,
                                            (int) (FeatureInfoBalloon.TARGET_WIDTH / FeatureInfoBalloon.PREVIEW_SCALE),
                                            (int) (FeatureInfoBalloon.TARGET_HEIGHT / FeatureInfoBalloon.PREVIEW_SCALE));
                        }
                    }
                    /*
                     * Add the graphs to the balloon
                     */
                    balloon.setGraphs(profileLocation, timeseriesLocation);
                    /*
                     * Redraw the balloon
                     */
                    firePropertyChange(AVKey.LAYER, null, annotationLayer);
                } catch (Exception e) {
                    /*
                     * We want to catch any exceptions and log them.
                     * EdalException and IOException are both possible, but we
                     * catch Exception to cover all runtime errors too, since
                     * the solution (log it) is the same.
                     */
                    String message = RescLogging.getMessage("resc.GraphProblem");
                    Logging.logger().warning(message);
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void sliderChanged(String id, double value, Extent<Double> valueRange,
            boolean calledFromLinked) {
        /*
         * If either slider has changed, update the value in the data layer
         */
        switch (id) {
        case ELEVATION_SLIDER:
            if (edalDataLayer != null) {
                edalDataLayer.setElevation(value, valueRange);
            }
            break;
        case TIME_SLIDER:
            /*
             * Because profile data needs to be extracted when a new time/time
             * range is selected this can be quite slow.
             * 
             * Therefore we only actually set the time on a profile layer once
             * the slider has stopped moving (see sliderSettled method)
             */
            if (edalDataLayer instanceof EdalGridDataLayer) {
                if (edalDataLayer != null) {
                    DateTime time = new DateTime((long) value);
                    Extent<DateTime> range = Extents.newExtent(new DateTime(valueRange.getLow()
                            .longValue()), new DateTime(valueRange.getHigh().longValue()));
                    edalDataLayer.setTime(time, range);
                }
            }
            break;
        default:
            break;
        }
    }

    private static final DecimalFormat NUMBER_3DP = new DecimalFormat("#0.000");

    @Override
    public String formatSliderValue(String id, double value) {
        switch (id) {
        case ELEVATION_SLIDER:
            if (edalDataLayer instanceof EdalGridDataLayer) {
                return NUMBER_3DP.format(((EdalGridDataLayer) edalDataLayer)
                        .getElevationOnAxis(value)) + " " + elevationUnits;
            } else {
                return NUMBER_3DP.format(value) + " " + elevationUnits;
            }
        case TIME_SLIDER:
            if (edalDataLayer instanceof EdalGridDataLayer) {
                return TimeUtils.formatUtcHumanReadableDateTime(((EdalGridDataLayer) edalDataLayer)
                        .getTimeOnAxis(new DateTime((long) value)));
            } else {
                return TimeUtils.formatUtcHumanReadableDateTime(new DateTime((long) value));
            }
        default:
            return "";
        }
    }

    @Override
    public void sliderSettled(String id) {
        if (edalDataLayer != null) {
            if (edalDataLayer instanceof EdalGridDataLayer) {
                /*
                 * Cache all of the times at this elevation and all of the
                 * elevations at this time.
                 * 
                 * This means that we don't cache everything, but anything that
                 * might be used gets cached when required
                 */
                ((EdalGridDataLayer) edalDataLayer).cacheFromCurrent();
            } else if (edalDataLayer instanceof EdalProfileDataLayer) {
                /*
                 * If we have a profile layer, we wait until the time slider has
                 * settled before changing the time, since extraction can be
                 * slow, and we don't want to extract every value as the slider
                 * is dragged
                 */
                switch (id) {
                case TIME_SLIDER:
                    if (edalDataLayer != null) {
                        DateTime time = new DateTime((long) timeSlider.getSliderValue());
                        Extent<DateTime> range = Extents.newExtent(new DateTime(timeSlider
                                .getSliderRange().getLow().longValue()), new DateTime(timeSlider
                                .getSliderRange().getHigh().longValue()));
                        edalDataLayer.setTime(time, range);
                    }
                    break;
                default:
                    break;
                }
            }
        }
    }

    /*
     * Make the projection values into an enum so that we can cycle through them
     * nicely
     */
    public enum Projection {
        MERCATOR {
            @Override
            public String getKey() {
                return FlatGlobe.PROJECTION_MERCATOR;
            }
        },
        LATLON {
            @Override
            public String getKey() {
                return FlatGlobe.PROJECTION_LAT_LON;
            }
        },
        SIN {
            @Override
            public String getKey() {
                return FlatGlobe.PROJECTION_SINUSOIDAL;
            }
        },
        SIN_MOD {
            @Override
            public String getKey() {
                return FlatGlobe.PROJECTION_MODIFIED_SINUSOIDAL;
            }
        };
        abstract public String getKey();

        public Projection getNext() {
            return values()[(ordinal() + 1) % values().length];
        }
    }

    @Override
    public void elevationCachingIncomplete() {
        if (elevationSlider != null) {
            elevationSlider.setNotCached();
            wwd.redraw();
        }
    }

    @Override
    public void elevationCachingComplete() {
        if (elevationSlider != null) {
            elevationSlider.setCached();
            wwd.redraw();
        }
    }

    @Override
    public void timeCachingIncomplete() {
        if (timeSlider != null) {
            timeSlider.setNotCached();
            wwd.redraw();
        }
    }

    @Override
    public void timeCachingComplete() {
        if (timeSlider != null) {
            timeSlider.setCached();
            wwd.redraw();
        }
    }
}

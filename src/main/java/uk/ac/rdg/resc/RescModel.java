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
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.layers.AnnotationLayer;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.SliderWidget.SliderWidgetHandler;
import uk.ac.rdg.resc.edal.domain.TemporalDomain;
import uk.ac.rdg.resc.edal.domain.VerticalDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.graphics.Charting;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.VerticalCrs;
import uk.ac.rdg.resc.edal.util.GISUtils;

public class RescModel extends BasicModel implements SliderWidgetHandler {
    private final static String ELEVATION_SLIDER = "depth-slider";
    private final static String TIME_SLIDER = "time-slider";

    private MultiGlobeFrame parent;

    private Earth globe;
    private EarthFlat flatMap;
    private VideoWallCatalogue catalogue;
    private boolean flat = false;

    private EdalDataLayer currentDataLayer = null;
    private VariableMetadata currentMetadata = null;
    private String edalLayerName = null;

    private EdalConfigLayer edalConfigLayer;

    private SliderWidgetAnnotation elevationSlider;
    private SliderWidgetAnnotation timeSlider;

    private AnnotationLayer annotationLayer;
    private RescWorldWindow wwd;

    private Double elevation;
    private DateTime time;

    private List<RescModel> elevationLinkedModels = new ArrayList<>();
    private List<RescModel> timeLinkedModels = new ArrayList<>();

    public RescModel(VideoWallCatalogue catalogue, RescWorldWindow wwd, MultiGlobeFrame parent) {
        super();

        this.catalogue = catalogue;
        this.wwd = wwd;
        this.parent = parent;

        globe = new Earth();
        flatMap = new EarthFlat();
        setGlobe(globe);

        edalConfigLayer = new EdalConfigLayer(wwd, catalogue);
        getLayers().add(edalConfigLayer);

        annotationLayer = new AnnotationLayer();
        getLayers().add(annotationLayer);

        elevationSlider = new SliderWidgetAnnotation(ELEVATION_SLIDER, AVKey.VERTICAL, AVKey.WEST,
                100, 0, wwd, this);
        timeSlider = new SliderWidgetAnnotation(TIME_SLIDER, AVKey.HORIZONTAL, AVKey.SOUTH, 0,
                1000, wwd, this);

        //        ViewControlsLayer viewControlsLayer = new ViewControlsLayer();
        //        viewControlsLayer.setShowPitchControls(false);
        //        viewControlsLayer.setShowVeControls(false);
        //        getLayers().add(viewControlsLayer);
        //        wwd.addSelectListener(new ViewControlsSelectListener(wwd, viewControlsLayer));
    }

    public void setFlat(boolean flat) {
        this.flat = flat;
        if (flat) {
            setGlobe(flatMap);
        } else {
            setGlobe(globe);
        }
    }

    public boolean isFlat() {
        return flat;
    }

    public void setDataLayer(String layerName) {
        boolean newLayer = false;
        if (layerName != null && !layerName.equals(edalLayerName)) {
            //            EdalDataLayer.precacheLayer(layerName, catalogue);
            newLayer = true;

            /*
             * We have changed the layer. We want to pick a default datetime and
             * depth, and precache all available times/elevations
             */
            try {
                currentMetadata = catalogue.getVariableMetadataForLayer(layerName);
            } catch (EdalException e) {
                /*
                 * The specified layer isn't found. Don't set it
                 * 
                 * TODO log this
                 */
                return;
            }

            /*
             * Remove the elevation/time sliders. We will add them again if
             * required.
             */
            annotationLayer.removeAnnotation(elevationSlider);
            annotationLayer.removeAnnotation(timeSlider);

            VerticalDomain verticalDomain = currentMetadata.getVerticalDomain();
            if (verticalDomain != null) {
                VerticalCrs verticalCrs = verticalDomain.getVerticalCrs();
                if (verticalCrs != null) {
                    if (verticalCrs.isPositiveUpwards()) {
                        /*
                         * TODO check if this is depth or elevation and set the
                         * limits accordingly.
                         */
                        elevationSlider.setReversed(false);
                    } else {
                        elevationSlider.setReversed(true);
                    }
                }
                /*
                 * Set the elevation, slider limits and slider value
                 */
                setElevation(GISUtils.getClosestElevationToSurface(verticalDomain));
                elevationSlider.setLimits(verticalDomain.getExtent().getLow(), verticalDomain
                        .getExtent().getHigh());
                elevationSlider.setSliderValue(elevation);

                List<RescModel> allModels = parent.getAllModels();
                elevationLinkedModels.clear();
                for (RescModel model : allModels) {
                    if (model.elevationSlider.equalLimits(elevationSlider)) {
                        elevationLinkedModels.add(model);
                        if (!model.elevationLinkedModels.contains(this)) {
                            model.elevationLinkedModels.add(this);
                        }
                        if (model != this) {
                            elevationSlider.setSliderValue(model.elevationSlider.getSliderValue());
                            setElevation(model.elevationSlider.getSliderValue());
                        }
                    }
                }

                /*
                 * Add the elevation slider
                 */
                annotationLayer.addAnnotation(elevationSlider);
            }

            TemporalDomain timeDomain = currentMetadata.getTemporalDomain();
            if (timeDomain != null) {
                setTime(GISUtils.getClosestToCurrentTime(timeDomain));
                timeSlider.setLimits(timeDomain.getExtent().getLow().getMillis(), timeDomain
                        .getExtent().getHigh().getMillis());
                timeSlider.setSliderValue(time.getMillis());

                List<RescModel> allModels = parent.getAllModels();
                timeLinkedModels.clear();
                for (RescModel model : allModels) {
                    if (model.timeSlider.equalLimits(timeSlider)) {
                        timeLinkedModels.add(model);
                        if (!model.timeLinkedModels.contains(this)) {
                            model.timeLinkedModels.add(this);
                        }
                    }
                    if (model != this) {
                        timeSlider.setSliderValue(model.timeSlider.getSliderValue());
                        setTime(new DateTime((long) model.timeSlider.getSliderValue()));
                    }
                }
                /*
                 * Add the time slider
                 */
                annotationLayer.addAnnotation(timeSlider);
            }

        }
        edalLayerName = layerName;
        drawLayer();

        if (newLayer) {
            //            EdalDataLayer.precacheLayer(layerName, catalogue);
        }
        /*
         * TODO Add time, elevation, etc to method signature
         */
    }

    private void setTime(DateTime time) {
        if (currentMetadata == null) {
            /*
             * TODO make sure that sliders are only drawn when there is a
             * time/depth axis
             */
            return;
        }
        TemporalDomain temporalDomain = currentMetadata.getTemporalDomain();
        if (temporalDomain instanceof TimeAxis) {
            TimeAxis timeAxis = (TimeAxis) temporalDomain;
            this.time = GISUtils.getClosestTimeTo(time, timeAxis);
        } else {
            this.time = time;
        }
    }

    private void setElevation(double depth) {
        if (currentMetadata == null) {
            return;
        }
        VerticalDomain zDomain = currentMetadata.getVerticalDomain();
        if (zDomain instanceof VerticalAxis) {
            VerticalAxis verticalAxis = (VerticalAxis) zDomain;
            int zIndex = verticalAxis.findIndexOf(depth);
            this.elevation = verticalAxis.getCoordinateValue(zIndex);
        } else {
            this.elevation = depth;
        }
    }

    private void drawLayer() {
        if (currentDataLayer != null) {
            getLayers().remove(currentDataLayer);
        }
        if (edalLayerName == null) {
            currentDataLayer = null;
        } else {
            /*
             * TODO layerId needs to be (e.g.) a UUID which encapsulates date,
             * time, elevation, layername, colourmap, etc. for caching
             */
            currentDataLayer = new EdalDataLayer(edalLayerName, catalogue, elevation, time);
            getLayers().add(currentDataLayer);
        }
    }

    public void showFeatureInfo(final Position position) {
        /*
         * Only display feature info if we have an active layer
         */
        if (edalLayerName != null && !edalLayerName.equals("")) {
            for (RescModel model : timeLinkedModels) {
                if (model.wwd.getLinkedView().getLinkedViewState()
                        .equals(wwd.getLinkedView().getLinkedViewState())) {
                    model.doShowFeatureInfo(position);
                }
            }
        }
    }

    private void doShowFeatureInfo(final Position position) {
        final FeatureInfoBalloon balloon = new FeatureInfoBalloon(position, wwd, annotationLayer);
        annotationLayer.addAnnotation(balloon);

        /*
         * Now extract the value and graphs in their own thread so that the
         * balloon appears instantly
         */
        new Thread(new Runnable() {
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
                    balloon.setValueText("No data layer selected");
                    e.printStackTrace();
                    return;
                }
                /*
                 * First read the data value and display the feature info
                 * balloon
                 */
                Number value = null;
                try {
                    value = catalogue.getLayerValue(edalLayerName, position, null, null);
                } catch (EdalException e) {
                    /*
                     * There is a problem reading the data. We log the error and
                     * continue. The feature info balloon will give a no-data
                     * message.
                     */
                    /*
                     * TODO log error in WW way
                     */
                    e.printStackTrace();
                }
                String valueText;
                if (value == null || Double.isNaN(value.doubleValue())) {
                    valueText = "No data value here";
                } else {
                    valueText = "Value of layer " + metadata.getId() + " is " + value
                            + metadata.getParameter().getUnits();
                }

                balloon.setValueText(valueText);

                try {
                    int width = wwd.getWidth() - 52;
                    int height = wwd.getHeight() - 52;
                    String profileLocation = null;
                    String timeseriesLocation = null;
                    if (metadata.getTemporalDomain() != null) {
                        /*
                         * Generate a timeseries graph
                         */
                        Collection<? extends PointSeriesFeature> timeseries = catalogue
                                .getTimeseries(edalLayerName, position);

                        if (timeseries != null && timeseries.size() > 0) {
                            JFreeChart timeseriesChart = Charting.createTimeSeriesPlot(timeseries,
                                    new HorizontalPosition(position.longitude.degrees,
                                            position.latitude.degrees, DefaultGeographicCRS.WGS84));
                            timeseriesChart.setBackgroundPaint(Color.white);

                            /*
                             * Save the chart at the same size as the panel
                             */
                            /*
                             * Change to a UUID once it works
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
                        Collection<? extends ProfileFeature> profiles = catalogue.getProfiles(
                                edalLayerName, position);
                        if (profiles != null && profiles.size() > 0) {
                            JFreeChart profileChart = Charting.createVerticalProfilePlot(profiles,
                                    new HorizontalPosition(position.longitude.degrees,
                                            position.latitude.degrees, DefaultGeographicCRS.WGS84));
                            profileChart.setBackgroundPaint(Color.white);
                            /*
                             * Change to a UUID once it works
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
                    balloon.setGraphs(profileLocation, timeseriesLocation);
                } catch (EdalException e) {
                    /*
                     * Another problem generating graphs
                     */
                    e.printStackTrace();
                } catch (IOException e) {
                    /*
                     * Problem writing chart to data file store
                     */
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private Thread sliderTimer = null;

    @Override
    public void sliderChanged(String id, double value) {
        resetSliderTimer();
        switch (id) {
        case ELEVATION_SLIDER:
            for (RescModel model : elevationLinkedModels) {
                model.setElevation(value);
                model.drawLayer();
                if (model != this) {
                    model.elevationSlider.setSliderValue(value);
                }
            }
            break;
        case TIME_SLIDER:
            for (RescModel model : timeLinkedModels) {
                model.setTime(new DateTime((long) value));
                model.drawLayer();
                if (model != this) {
                    model.timeSlider.setSliderValue(value);
                }
            }
            break;
        default:
            break;
        }
        sliderTimer.start();
    }

    private void resetSliderTimer() {
        if (sliderTimer != null) {
            sliderTimer.interrupt();
        }
        sliderTimer = new Thread(new Runnable() {
            @Override
            public void run() {
                /*
                 * Sleep for 1 second and then call the sliderSettled method
                 */
                try {
                    Thread.sleep(500L);
                    sliderSettled();
                } catch (InterruptedException e) {
                    /*
                     * Don't do anything - the timer was interrupted
                     * deliberately
                     */
                }
            }
        });
    }

    private void sliderSettled() {
        System.out.println("Slider stopped moving for 500ms");
        /*
         * TODO go and cache all of the times at this elevation and all of the
         * elevations at this time.
         * 
         * This means that we don't cache everything, but anything that might be
         * used gets cached when required
         */
    }
}

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
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.SliderWidget.SliderWidgetHandler;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.graphics.Charting;
import uk.ac.rdg.resc.edal.grid.TimeAxis;
import uk.ac.rdg.resc.edal.grid.VerticalAxis;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.VerticalCrs;
import uk.ac.rdg.resc.edal.util.TimeUtils;

public class RescModel extends BasicModel implements SliderWidgetHandler {
    private final static String ELEVATION_SLIDER = "depth-slider";
    private final static String TIME_SLIDER = "time-slider";

    private MultiGlobeFrame parent;

    private Earth globe;
    private EarthFlat flatMap;
    private VideoWallCatalogue catalogue;
    private boolean flat = false;

    //    private EdalDataLayer currentDataLayer = null;
    private EdalGridDataLayer gridDataLayer = null;
    private String edalLayerName = null;

    //    private VariableMetadata currentMetadata = null;

    private EdalConfigLayer edalConfigLayer;

    private SliderWidgetAnnotation elevationSlider;
    private String elevationUnits = "";
    private SliderWidgetAnnotation timeSlider;

    private AnnotationLayer annotationLayer;
    private RescWorldWindow wwd;

    //    private Double elevation;
    //    private DateTime time;

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

        //        SliderWidgetAnnotation westSlider = new SliderWidgetAnnotation("test", AVKey.VERTICAL,
        //                AVKey.WEST, 0, 100, wwd, this);
        //        SliderWidgetAnnotation eastSlider = new SliderWidgetAnnotation("test", AVKey.VERTICAL,
        //                AVKey.EAST, 0, 100, wwd, this);
        //        SliderWidgetAnnotation northSlider = new SliderWidgetAnnotation("test", AVKey.HORIZONTAL,
        //                AVKey.NORTH, 0, 100, wwd, this);
        //        SliderWidgetAnnotation southSlider = new SliderWidgetAnnotation("test", AVKey.HORIZONTAL,
        //                AVKey.SOUTH, 0, 100, wwd, this);
        //        annotationLayer.addAnnotation(southSlider);
        //        annotationLayer.addAnnotation(northSlider);
        //        annotationLayer.addAnnotation(eastSlider);
        //        annotationLayer.addAnnotation(westSlider);

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
        if (layerName != null && !layerName.equals(edalLayerName)) {
            /*
             * TODO here's where we check whether we have a gridded or point
             * layer.
             * 
             * But only once we've implemented point layers
             */

            EdalGridDataLayer tempLayer;
            try {
                tempLayer = new EdalGridDataLayer(layerName, catalogue, getLayers(), wwd);
            } catch (EdalException e1) {
                /*
                 * TODO log this better
                 */
                e1.printStackTrace();
                return;
            }

            /*
             * We have successfully instantiated the layer with no errors, so
             * now we assign it to gridDataLayer
             */
            if (gridDataLayer != null) {
                /*
                 * Destroy the previous layer. This will remove all data layers
                 * from the layer list
                 */
                gridDataLayer.destroy();
            }

            gridDataLayer = tempLayer;

            /*
             * Remove all annotations. This will get rid of the time and
             * elevation sliders (new ones get put back if required), and also
             * any feature info balloons
             */
            annotationLayer.removeAllAnnotations();

            GridVariableMetadata layerMetadata = gridDataLayer.getLayerMetadata();
            VerticalAxis verticalDomain = layerMetadata.getVerticalDomain();
            if (verticalDomain != null) {
                VerticalCrs verticalCrs = verticalDomain.getVerticalCrs();
                if (verticalCrs != null) {
                    /*
                     * Ensure that the slider treats depth and height correctly,
                     * and save the units
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
                elevationSlider.setLimits(verticalDomain.getExtent().getLow(), verticalDomain
                        .getExtent().getHigh());
                elevationSlider.setSliderValue(gridDataLayer.getElevation());
                
                List<RescModel> allModels = parent.getAllModels();
                elevationLinkedModels.clear();
                for (RescModel model : allModels) {
                    if (model.elevationSlider.equalLimits(elevationSlider)) {
                        elevationLinkedModels.add(model);
                        if (!model.elevationLinkedModels.contains(this)) {
                            model.elevationLinkedModels.add(this);
                        }
                        if (model != this && model.gridDataLayer != null) {
                            elevationSlider.setSliderValue(model.elevationSlider.getSliderValue());
                            gridDataLayer.setElevation(model.elevationSlider.getSliderValue());
                        }
                    }
                }

                /*
                 * Add the elevation slider
                 */
                annotationLayer.addAnnotation(elevationSlider);
            }

            TimeAxis tAxis = layerMetadata.getTemporalDomain();
            if (tAxis != null) {
                timeSlider.setLimits(tAxis.getExtent().getLow().getMillis(), tAxis
                        .getExtent().getHigh().getMillis());
                timeSlider.setSliderValue(gridDataLayer.getTime().getMillis());

                List<RescModel> allModels = parent.getAllModels();
                timeLinkedModels.clear();
                for (RescModel model : allModels) {
                    if (model.timeSlider.equalLimits(timeSlider)) {
                        timeLinkedModels.add(model);
                        if (!model.timeLinkedModels.contains(this)) {
                            model.timeLinkedModels.add(this);
                        }
                    }
                    if (model != this && model.gridDataLayer != null) {
                        timeSlider.setSliderValue(model.timeSlider.getSliderValue());
                        gridDataLayer.setTime(new DateTime((long) model.timeSlider.getSliderValue()));
                    }
                }
                /*
                 * Add the time slider
                 */
                annotationLayer.addAnnotation(timeSlider);
            }

        }
        edalLayerName = layerName;
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

    @Override
    public void sliderChanged(String id, double value) {
        switch (id) {
        case ELEVATION_SLIDER:
            for (RescModel model : elevationLinkedModels) {
                model.gridDataLayer.setElevation(value);
                if (model != this) {
                    model.elevationSlider.setSliderValue(value);
                }
            }
            break;
        case TIME_SLIDER:
            for (RescModel model : timeLinkedModels) {
                model.gridDataLayer.setTime(new DateTime((long) value));
                if (model != this) {
                    model.timeSlider.setSliderValue(value);
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
            return NUMBER_3DP.format(value) + " " + elevationUnits;
        case TIME_SLIDER:
            return TimeUtils.formatUtcHumanReadableDateTime(new DateTime((long) value));
        default:
            return "";
        }
    }

    @Override
    public void sliderSettled() {
        /*
         * TODO go and cache all of the times at this elevation and all of the
         * elevations at this time.
         * 
         * This means that we don't cache everything, but anything that might be
         * used gets cached when required
         */
        //        System.out.println("Slider stopped moving for 500ms");
        if (gridDataLayer != null) {
            gridDataLayer.cacheFromCurrent();
            //            EdalDataLayer.premptivelyCacheLayer(edalLayerName, elevation, time, catalogue);
        }
    }
}

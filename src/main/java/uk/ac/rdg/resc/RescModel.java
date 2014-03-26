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
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.LinkedView.LinkedViewState;
import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.domain.TemporalDomain;
import uk.ac.rdg.resc.edal.domain.VerticalDomain;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.graphics.Charting;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.position.VerticalCrs;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.util.TimeUtils;
import uk.ac.rdg.resc.edal.wms.exceptions.WmsLayerNotFoundException;
import uk.ac.rdg.resc.widgets.FeatureInfoBalloon;
import uk.ac.rdg.resc.widgets.SliderWidget.SliderWidgetHandler;
import uk.ac.rdg.resc.widgets.SliderWidgetAnnotation;

public class RescModel extends BasicModel implements SliderWidgetHandler, SelectListener {
    private final static String ELEVATION_SLIDER = "depth-slider";
    private final static String TIME_SLIDER = "time-slider";

    private MultiGlobeFrame parent;

    private Earth globe;
    private EarthFlat flatMap;
    private VideoWallCatalogue catalogue;
    private boolean flat = false;

    /*
     * TODO separate into gridded and non-gridded so that we can have both? This
     * will require better handling of colour scales etc.
     */
    private EdalDataLayer edalDataLayer = null;
    private String edalLayerName = null;

    //    private VariableMetadata currentMetadata = null;

    private EdalConfigLayer edalConfigLayer;

    private AnnotationLayer annotationLayer;
    private SliderWidgetAnnotation elevationSlider = null;
    private String elevationUnits = "";
    private SliderWidgetAnnotation timeSlider = null;
    private ImageAnnotation legendAnnotation = null;

    /*
     * We store the viewport height so that if it changes we can re-generated
     * the legend
     */
    private int lastViewportHeight = -1;

    private RescWorldWindow wwd;

    private List<RescModel> timeLinkedModels = new ArrayList<>();

    /*
     * Keep a reference to the current feature info balloon so that we can close
     * it if a user clicks somewhere else
     */
    private FeatureInfoBalloon balloon = null;

    private boolean legendRefresh = false;
    private boolean legendShrunk = false;

    public RescModel(VideoWallCatalogue catalogue, RescWorldWindow wwd, MultiGlobeFrame parent) {
        super();

        this.catalogue = catalogue;
        this.wwd = wwd;
        this.parent = parent;

        globe = new Earth();
        flatMap = new EarthFlat();
        setGlobe(globe);

        annotationLayer = new AnnotationLayer() {

            @Override
            protected void doRender(DrawContext dc) {
                super.doRender(dc);
                if (edalDataLayer != null) {
                    Rectangle viewport = dc.getView().getViewport();
                    if (viewport.height != lastViewportHeight || legendRefresh) {
                        lastViewportHeight = viewport.height;
                        addLegend(viewport.height / 2);
                    }
                    if (legendAnnotation != null && legendAnnotation.getImageSource() != null) {
                        int width = (int) (((BufferedImage) legendAnnotation.getImageSource())
                                .getWidth() * legendAnnotation.getAttributes().getImageScale());
                        int height = (int) (((BufferedImage) legendAnnotation.getImageSource())
                                .getHeight() * legendAnnotation.getAttributes().getImageScale());

                        int xOffset = legendShrunk ? -10 : 10;
                        int yOffset = legendShrunk ? 10 : height / 2;

                        legendAnnotation.setScreenPoint(new Point(viewport.x + viewport.width
                                - width + xOffset, viewport.y + yOffset));
                    }
                }
            }
        };
        getLayers().add(annotationLayer);

        edalConfigLayer = new EdalConfigLayer(wwd, catalogue);
        getLayers().add(edalConfigLayer);

        wwd.addSelectListener(this);

        //        SliderWidgetHandler handler = new SliderWidgetHandler() {
        //            @Override
        //            public void sliderSettled() {
        //            }
        //            
        //            @Override
        //            public void sliderChanged(String id, double value, Extent<Double> valueRange) {
        //                System.out.println(id+" has value "+value+" ("+valueRange.getLow()+" - "+valueRange.getHigh()+")");
        //            }
        //            
        //            @Override
        //            public String formatSliderValue(String id, double value) {
        //                return null;
        //            }
        //        };
        //        
        //        SliderWidgetAnnotation horizNormal = new SliderWidgetAnnotation("horiznorm", AVKey.HORIZONTAL, AVKey.SOUTH, 0, 100, wwd, handler);
        //        annotationLayer.addAnnotation(horizNormal);
        //        SliderWidgetAnnotation horizRev = new SliderWidgetAnnotation("horizrev", AVKey.HORIZONTAL, AVKey.NORTH, 0, 100, wwd, handler);
        //        horizRev.setReversed(true);
        //        annotationLayer.addAnnotation(horizRev);
        //        SliderWidgetAnnotation vertNormal = new SliderWidgetAnnotation("vertnorm", AVKey.VERTICAL, AVKey.WEST, 0, 100, wwd, handler);
        //        annotationLayer.addAnnotation(vertNormal);
        //        SliderWidgetAnnotation vertRev = new SliderWidgetAnnotation("vertrev", AVKey.VERTICAL, AVKey.EAST, 0, 100, wwd, handler);
        //        vertRev.setReversed(true);
        //        annotationLayer.addAnnotation(vertRev);
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

    public void setDataLayer(String layerName) throws WmsLayerNotFoundException {
        System.out.println("setting data layer: " + layerName);
        if (layerName != null && !layerName.equals(edalLayerName)) {
            Dataset dataset = catalogue.getDatasetFromLayerName(layerName);
            Class<? extends DiscreteFeature<?, ?>> mapFeatureType = dataset
                    .getMapFeatureType(catalogue.getVariableIdFromLayerName(layerName));

            VariableMetadata layerMetadata = null;

            EdalDataLayer tempLayer = null;
            if (MapFeature.class.isAssignableFrom(mapFeatureType)) {
                try {
                    tempLayer = new EdalGridDataLayer(layerName, catalogue, getLayers(), wwd);
                } catch (EdalException e1) {
                    /*
                     * TODO log this better
                     */
                    e1.printStackTrace();
                    return;
                }
            } else if (ProfileFeature.class.isAssignableFrom(mapFeatureType)) {
                try {
                    tempLayer = new EdalProfileDataLayer(layerName, catalogue, getLayers(), wwd);
                } catch (EdalException e1) {
                    /*
                     * TODO log this better
                     */
                    e1.printStackTrace();
                    return;
                }
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

            legendRefresh = true;

            layerMetadata = edalDataLayer.getLayerMetadata();

            if (layerMetadata != null) {
                addSliders(layerMetadata);
            }

            edalLayerName = layerName;
        }
    }

    private void addSliders(VariableMetadata layerMetadata) {
        /*
         * Remove all annotations. This will get rid of the time and elevation
         * sliders (new ones get put back if required), and also any feature
         * info balloons
         */
        annotationLayer.removeAllAnnotations();

        VerticalDomain verticalDomain = layerMetadata.getVerticalDomain();
        if (verticalDomain != null) {
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
            elevationSlider.setLimits(verticalDomain.getExtent().getLow(), verticalDomain
                    .getExtent().getHigh());
            elevationSlider.setSliderValue(edalDataLayer.getElevation());

            List<RescModel> allModels = parent.getAllModels();
            for (RescModel model : allModels) {
                if (model != this && model.elevationSlider != null
                        && model.elevationSlider.equalLimits(elevationSlider)) {
                    elevationSlider.linkSlider(model.elevationSlider);
                    if (model.edalDataLayer != null) {
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

        TemporalDomain tAxis = layerMetadata.getTemporalDomain();
        if (tAxis != null) {
            if (timeSlider == null) {
                timeSlider = new SliderWidgetAnnotation(TIME_SLIDER, AVKey.HORIZONTAL, AVKey.SOUTH,
                        tAxis.getExtent().getLow().getMillis(), tAxis.getExtent().getHigh()
                                .getMillis(), wwd, this);
            } else {
                timeSlider.setLimits(tAxis.getExtent().getLow().getMillis(), tAxis.getExtent()
                        .getHigh().getMillis());
            }
            timeSlider.setSliderValue(edalDataLayer.getTime().getMillis());

            List<RescModel> allModels = parent.getAllModels();
            timeLinkedModels.clear();
            timeLinkedModels.add(this);
            for (RescModel model : allModels) {
                if (model != this && model.timeSlider != null
                        && model.timeSlider.equalLimits(timeSlider)) {
                    timeLinkedModels.add(model);
                    if (!model.timeLinkedModels.contains(this)) {
                        model.timeLinkedModels.add(this);
                    }

                    timeSlider.linkSlider(model.timeSlider);
                    if (model.edalDataLayer != null) {
                        timeSlider.setSliderValue(model.timeSlider.getSliderValue());
                        DateTime value = new DateTime((long) model.timeSlider.getSliderValue());
                        Extent<Double> doubleRange = model.timeSlider.getSliderRange();
                        Extent<DateTime> range = Extents.newExtent(new DateTime(doubleRange
                                .getLow().longValue()), new DateTime(doubleRange.getHigh()
                                .longValue()));
                        edalDataLayer.setTime(value, range);
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

    private void addLegend(int size) {
        if (legendAnnotation != null) {
            annotationLayer.removeAnnotation(legendAnnotation);
        }
        BufferedImage legend = edalDataLayer.getLegend(size, true);
        if (legend != null) {
            if (legend.getWidth() > size) {
                /*
                 * We have a big legend - i.e. a multidimensional field
                 */

                /*
                 * First get a new legend with field labels
                 */
                legend = edalDataLayer.getLegend(size, true);
                legendAnnotation = new ImageAnnotation(legend);
                legendShrunk = true;
                shrinkLegend(100);
            } else {
                legendAnnotation = new ImageAnnotation(legend);
                legendShrunk = false;
            }
            legendRefresh = false;
            legendAnnotation.setPickEnabled(true);
            annotationLayer.addAnnotation(legendAnnotation);
        }
    }

    private void shrinkLegend(int desiredSize) {
        BufferedImage legend = (BufferedImage) legendAnnotation.getAttributes().getImageSource();
        legendAnnotation.getAttributes().setImageScale((double) desiredSize / legend.getWidth());
        legendAnnotation.getAttributes().setDrawOffset(
                new Point(legend.getWidth() / 2, -legend.getHeight() + desiredSize));
    }

    public void showFeatureInfo(final Position position) {
        //        if(balloon != null && balloon.getAttributes().isVisible()) {
        //            return;
        //        }
        System.out.println("showFI called");
        /*
         * Only display feature info if we have an active layer
         */
        if (edalLayerName != null && !edalLayerName.equals("")) {
            doShowFeatureInfo(position);
            /*
             * Now if this view is linked with other views, and we have a time
             * axis, we may want to display feature info on the other views
             */
            if (wwd.getLinkedView().getLinkedViewState() == LinkedViewState.LINKED
                    || wwd.getLinkedView().getLinkedViewState() == LinkedViewState.ANTILINKED
                    && timeSlider != null) {
                for (RescModel model : parent.getAllModels()) {
                    /*
                     * If another model has the same time value selected and has
                     * its view in sync with this one, we want to display
                     * feature info on it as well
                     */
                    if (model != this
                            && model.timeSlider.getSliderValue() == timeSlider.getSliderValue()
                            && wwd.getLinkedView().getLinkedViewState()
                                    .equals(model.wwd.getLinkedView().getLinkedViewState())) {
                        model.doShowFeatureInfo(position);
                    }
                }
            }
        }
    }

    private void doShowFeatureInfo(final Position position) {
        if (balloon != null) {
            annotationLayer.removeAnnotation(balloon);
        }
        balloon = new FeatureInfoBalloon(position, wwd, annotationLayer);
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
                    firePropertyChange(AVKey.LAYER, null, annotationLayer);
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
        });
    }

    @Override
    public void sliderChanged(String id, double value, Extent<Double> valueRange) {
        switch (id) {
        case ELEVATION_SLIDER:
            if (edalDataLayer != null) {
                edalDataLayer.setElevation(value, valueRange);
            }
            break;
        case TIME_SLIDER:
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
            return NUMBER_3DP.format(value) + " " + elevationUnits;
        case TIME_SLIDER:
            return TimeUtils.formatUtcHumanReadableDateTime(new DateTime((long) value));
        default:
            return "";
        }
    }

    @Override
    public void sliderSettled(String id) {
        /*
         * Cache all of the times at this elevation and all of the elevations at
         * this time.
         * 
         * This means that we don't cache everything, but anything that might be
         * used gets cached when required
         */
        if (edalDataLayer != null && edalDataLayer instanceof EdalGridDataLayer) {
            ((EdalGridDataLayer) edalDataLayer).cacheFromCurrent();
        } else if (edalDataLayer instanceof EdalProfileDataLayer) {
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

    @Override
    public void selected(SelectEvent event) {
        /*
         * TODO Move the legend handling to the EdalConfigLayer, since it is not
         * just an annotation
         */
        /*
         * Listen for clicks on the legend.
         */
        if (event.hasObjects()) {
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                if (event.getTopObject() == legendAnnotation) {
                    if (legendShrunk) {
                        /*
                         * We have a shrunk version of the legend, in which case
                         * we want to expand it
                         */
                        if (legendAnnotation.getAttributes().getImageScale() == 1.0) {
                            shrinkLegend(100);
                        } else {
                            legendAnnotation.getAttributes().setImageScale(1.0);
                            legendAnnotation.getAttributes().getDrawOffset().y = 0;
                        }
                    } else {
                        /*
                         * We have a normal legend, so we want to bring up the
                         * scale configuration dialog
                         */
                    }
                }
            } else if (event.getEventAction().equals(SelectEvent.ROLLOVER)) {
                if (event.getTopObject() == legendAnnotation) {
                    ((Component) wwd).setCursor(new Cursor(Cursor.HAND_CURSOR));
                    event.consume();
                } else {
                    ((Component) wwd).setCursor(Cursor.getDefaultCursor());
                }
            }
        }
    }
}

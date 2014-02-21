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
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.layers.AnnotationLayer;

import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.Collection;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;

import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.graphics.Charting;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;

public class RescModel extends BasicModel {
    private Earth globe;
    private EarthFlat flatMap;
    private VideoWallCatalogue catalogue;
    private boolean flat = false;

    private EdalDataLayer currentDataLayer = null;
    private String edalLayerName = null;

    private EdalConfigLayer edalConfigLayer;

    private AnnotationLayer annotationLayer;
    private RescWorldWindow wwd;

    public RescModel(VideoWallCatalogue catalogue, RescWorldWindow wwd) {
        super();

        this.wwd = wwd;

        this.catalogue = catalogue;

        globe = new Earth();
        flatMap = new EarthFlat();
        setGlobe(globe);

        edalConfigLayer = new EdalConfigLayer(wwd, catalogue);
        getLayers().add(edalConfigLayer);

        annotationLayer = new AnnotationLayer();
        getLayers().add(annotationLayer);
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
        edalLayerName = layerName;
        /*
         * TODO Add time, elevation, etc to method signature
         */
        if (currentDataLayer != null) {
            getLayers().remove(currentDataLayer);
        }
        if (layerName == null) {
            currentDataLayer = null;
        } else {
            /*
             * TODO layerId needs to be (e.g.) a UUID which encapsulates date,
             * time, elevation, layername, colourmap, etc. for caching
             */
            String layerId = layerName;
            currentDataLayer = new EdalDataLayer(layerId, catalogue);
            currentDataLayer.setData(layerName);
            currentDataLayer.setPickEnabled(true);
            getLayers().add(currentDataLayer);
        }
    }

    public void showFeatureInfo(final Position position) {
        /*
         * Only display feature info if we have an active layer
         */
        if (edalLayerName != null && !edalLayerName.equals("")) {
            final FeatureInfoBalloon balloon = new FeatureInfoBalloon(position, wwd,
                    annotationLayer);
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
                         * There is a problem reading the data. We log the error
                         * and continue. The feature info balloon will give a
                         * no-data message.
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

                    /*
                     * null for DateTime because it's a hack (see comment in
                     * VideoWallCatalogue about this method being a hack...)
                     */
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
                                JFreeChart timeseriesChart = Charting.createTimeSeriesPlot(
                                        timeseries, new HorizontalPosition(
                                                position.longitude.degrees,
                                                position.latitude.degrees,
                                                DefaultGeographicCRS.WGS84));
                                timeseriesChart.setBackgroundPaint(Color.white);

                                /*
                                 * Save the chart at the same size as the panel
                                 */
                                /*
                                 * Change to a UUID once it works
                                 */
                                timeseriesLocation = "EDAL/Charts/TS-" + System.currentTimeMillis();
                                File timeseriesFile = WorldWind.getDataFileStore().newFile(
                                        timeseriesLocation);
                                ChartUtilities.saveChartAsPNG(timeseriesFile, timeseriesChart,
                                        width, height);
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
                                JFreeChart profileChart = Charting.createVerticalProfilePlot(
                                        profiles, new HorizontalPosition(
                                                position.longitude.degrees,
                                                position.latitude.degrees,
                                                DefaultGeographicCRS.WGS84));
                                profileChart.setBackgroundPaint(Color.white);
                                /*
                                 * Change to a UUID once it works
                                 */
                                profileLocation = "EDAL/Charts/PF-" + System.currentTimeMillis();
                                File profileFile = WorldWind.getDataFileStore().newFile(
                                        profileLocation);
                                ChartUtilities.saveChartAsPNG(profileFile, profileChart, width,
                                        height);
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
            ;
        }
    }
}

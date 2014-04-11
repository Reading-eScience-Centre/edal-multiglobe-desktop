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

package uk.ac.rdg.resc.widgets;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.render.Annotation;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.AnnotationFlowLayout;
import gov.nasa.worldwind.render.AnnotationNullLayout;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwindx.examples.util.ButtonAnnotation;
import gov.nasa.worldwindx.examples.util.DialogAnnotation;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.net.URL;
import java.text.DecimalFormat;

import uk.ac.rdg.resc.RescWorldWindow;

/**
 * A balloon to be attached to the globe which shows various information about
 * the data value at that point (including timeseries/depth graphs if
 * appropriate)
 * 
 * @author Guy Griffiths
 */
public class FeatureInfoBalloon extends DialogAnnotation implements SelectListener {
    public static final DecimalFormat NUMBER_3DP = new DecimalFormat("#0.000");
    /** The desired width of the preview graph */
    public static final int TARGET_WIDTH = 300;
    /** The desired height of the preview graph */
    public static final int TARGET_HEIGHT = 150;
    /**
     * The amount the preview image should be scaled (so that it looks like a
     * preview not just a small graph
     */
    public static final double PREVIEW_SCALE = 0.5;

    /** The image to use for the close button */
    private static final String RESC_CLOSE_IMAGE_PATH = "images/closeBubble.png";

    /**
     * A reference to the containing {@link RescWorldWindow}, used so that this
     * {@link FeatureInfoBalloon} can close itself
     */
    private RescWorldWindow wwd;
    /** The {@link AnnotationLayer} to display the balloon on */
    private AnnotationLayer balloonLayer;
    /** The {@link AnnotationLayer} to display full screen graphs on */
    private AnnotationLayer graphLayer;

    /** The title for the balloon */
    private ScreenAnnotation titleLabel;

    /** The info label (usually the data value) */
    private ScreenAnnotation infoLabel = null;
    /** The preview of the timeseries graph */
    private ImageAnnotation timeseriesGraph = null;
    /** The location of the full-sized timeseries graph image */
    private String timeseriesFullPath = null;
    /** The preview of the depth profile graph */
    private ImageAnnotation profileGraph = null;
    /** The location of the full-size profile graph image */
    private String profileFullPath = null;

    /** The close button for the full screen graph */
    private ImageAnnotation closeFullScreen;
    /** The full screen graph */
    private ScreenAnnotation fullScreenGraph = null;

    /** Panel to hold content */
    private Annotation featureInfoContent;

    /**
     * Creates a new {@link FeatureInfoBalloon}
     * 
     * @param position
     *            The position on the globe to display the annotation
     * @param wwd
     *            The {@link RescWorldWindow} which this balloon belongs to
     * @param balloonLayer
     *            The {@link AnnotationLayer} to display the balloon on
     * @param graphLayer
     *            The {@link AnnotationLayer} to display the full-sized graph on
     */
    public FeatureInfoBalloon(Position position, RescWorldWindow wwd, AnnotationLayer balloonLayer,
            AnnotationLayer graphLayer) {
        super(position);

        this.wwd = wwd;
        this.balloonLayer = balloonLayer;
        this.graphLayer = graphLayer;

        initFeatureInfoComponents();
        layoutFeatureInfoComponents();

        /*
         * This will ensure that markers (e.g. for profile data) are never
         * plotted on top of this balloon
         */
        setAlwaysOnTop(true);

        /*
         * Make this listen to select events
         */
        wwd.addSelectListener(this);
    }

    @Override
    protected void initComponents() {
        /*
         * We replace super.initComponents with the necessary stuff, so we can
         * use a different image for the close button
         */
        closeButton = new ButtonAnnotation(RESC_CLOSE_IMAGE_PATH, DEPRESSED_MASK_PATH);

        busyImage = new BusyImage(BUSY_IMAGE_PATH);
    }

    /**
     * Initialises all of the components
     */
    protected void initFeatureInfoComponents() {
        closeFullScreen = new ButtonAnnotation(RESC_CLOSE_IMAGE_PATH, DEPRESSED_MASK_PATH);

        /*
         * Set the title to display the clicked location
         */
        StringBuilder title = new StringBuilder();
        title.append("Clicked (");
        title.append(NUMBER_3DP.format(position.latitude.degrees));
        title.append(", ");
        title.append(NUMBER_3DP.format(position.longitude.degrees));
        title.append(")");
        titleLabel = new ScreenAnnotation(title.toString(), new Point());
        setupLabel(titleLabel);

        AnnotationAttributes attribs = titleLabel.getAttributes();
        attribs.setFont(Font.decode("Arial-BOLD-16"));
        attribs.setSize(new Dimension(300, 0));
        attribs.setTextAlign(AVKey.CENTER);
        attribs.setInsets(new Insets(0, 0, 0, 32));

        /*
         * Now set up the content panels. They start off empty
         */
        featureInfoContent = new ScreenAnnotation("", new java.awt.Point());
        this.setupContainer(featureInfoContent);
        featureInfoContent.setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, 4));

        ScreenAnnotation balloonContent = new ScreenAnnotation("", new java.awt.Point());
        this.setupContainer(balloonContent);
        balloonContent.setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, 16));
        balloonContent.addChild(this.titleLabel);
        balloonContent.addChild(featureInfoContent);

        addChild(balloonContent);
    }

    /**
     * Sets the text of the info label. Usually this will be the data value
     * 
     * @param valueText
     *            The text to set
     */
    public void setInfoText(String valueText) {
        infoLabel = new ScreenAnnotation(valueText, new Point());
        setupLabel(infoLabel);
        AnnotationAttributes attribs = infoLabel.getAttributes();
        attribs.setFont(Font.decode("Arial-BOLD-14"));
        attribs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        attribs.setSize(new Dimension(Integer.MAX_VALUE, 0));
        layoutFeatureInfoComponents();
    }

    /**
     * Sets the graphs. If either path is <code>null</code>, it is assumed that
     * no graph is available, so none is plotted.
     * 
     * @param profilePath
     *            The path to the profile graph (within the WorldWind data
     *            FileStore)
     * @param timeseriesPath
     *            The path to the timeseries graph (within the WorldWind data
     *            FileStore)
     */
    public void setGraphs(String profilePath, String timeseriesPath) {
        if (profilePath != null) {
            profileFullPath = profilePath;
            /*
             * Set a preview image and scale it
             */
            URL profileFile = WorldWind.getDataFileStore()
                    .findFile(profilePath + "-preview", false);
            if (profileFile != null) {
                profileGraph = new ImageAnnotation(profileFile, TARGET_WIDTH, TARGET_HEIGHT);
                profileGraph.setPickEnabled(true);
                AnnotationAttributes attributes = profileGraph.getAttributes();
                attributes.setImageScale(PREVIEW_SCALE);
                attributes.setDistanceMinOpacity(0.0);
            }
        }
        if (timeseriesPath != null) {
            timeseriesFullPath = timeseriesPath;
            /*
             * Set a preview image and scale it
             */
            URL timeseriesFile = WorldWind.getDataFileStore().findFile(timeseriesPath + "-preview",
                    false);
            if (timeseriesFile != null) {
                timeseriesGraph = new ImageAnnotation(timeseriesFile, TARGET_WIDTH, TARGET_HEIGHT);
                timeseriesGraph.setPickEnabled(true);
                AnnotationAttributes attributes = timeseriesGraph.getAttributes();
                attributes.setImageScale(PREVIEW_SCALE);
            }
        }
        layoutFeatureInfoComponents();
    }

    /**
     * Adds the info label and graphs if they are present
     */
    protected void layoutFeatureInfoComponents() {
        featureInfoContent.removeAllChildren();
        if (infoLabel != null) {
            featureInfoContent.addChild(infoLabel);
        }
        ScreenAnnotation graphPanel = new ScreenAnnotation("", new java.awt.Point());
        setupContainer(graphPanel);
        graphPanel.setLayout(new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER, 4, 0));
        boolean graphs = false;
        if (timeseriesGraph != null) {
            graphPanel.addChild(timeseriesGraph);
            graphs = true;
        }
        if (profileGraph != null) {
            graphPanel.addChild(profileGraph);
            graphs = true;
        }
        if (graphs) {
            featureInfoContent.addChild(graphPanel);
        }
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            Object selectObj = event.getTopObject();
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                if (selectObj == closeButton) {
                    /*
                     * Close button clicked. Remove the annotation and the
                     * select listener and consume the mouse event so that we
                     * don't immediately get another FeatureInfoBalloon
                     * displayed
                     */
                    balloonLayer.removeAnnotation(FeatureInfoBalloon.this);
                    wwd.removeSelectListener(this);
                } else if (selectObj == profileGraph) {
                    /* Display the profile graph full-sized */
                    showFullScreenGraph(profileFullPath);
                } else if (selectObj == timeseriesGraph) {
                    /* Display the timeseries graph full-sized */
                    showFullScreenGraph(timeseriesFullPath);
                } else if (selectObj == closeFullScreen) {
                    /*
                     * Set this balloon to be always on top again and remove the
                     * full screen graph
                     */
                    setAlwaysOnTop(true);
                    graphLayer.removeAnnotation(fullScreenGraph);
                    fullScreenGraph = null;
                }
                event.getMouseEvent().consume();
            } else if (event.getEventAction().equals(SelectEvent.ROLLOVER)) {
                /*
                 * TODO This may need to be centralised to avoid conflicts
                 */
                if (selectObj == profileGraph || selectObj == timeseriesGraph) {
                    ((Component) wwd).setCursor(new Cursor(Cursor.HAND_CURSOR));
                    event.consume();
                } else {
                    ((Component) wwd).setCursor(Cursor.getDefaultCursor());
                }
            }
        }
    }

    /**
     * Displays the full screen graph
     * 
     * @param imagePath
     *            The path to the image to display
     */
    private void showFullScreenGraph(String imagePath) {
        if (fullScreenGraph != null) {
            /*
             * We are already displaying a full screen annotation
             */
            return;
        }
        fullScreenGraph = new ScreenAnnotation("", new java.awt.Point(wwd.getWidth() / 2, 10));
        AnnotationNullLayout layout = new AnnotationNullLayout();
        fullScreenGraph.setLayout(layout);
        URL graphFile = WorldWind.getDataFileStore().findFile(imagePath, false);
        ImageAnnotation graph = new ImageAnnotation(graphFile);
        fullScreenGraph.addChild(graph);
        fullScreenGraph.addChild(closeFullScreen);

        /*
         * Stop the original balloon being on top, or else it will be displayed
         * over the graph
         */
        setAlwaysOnTop(false);

        layout.setConstraint(closeFullScreen, AVKey.NORTHEAST);
        graphLayer.addAnnotation(fullScreenGraph);
    }
}

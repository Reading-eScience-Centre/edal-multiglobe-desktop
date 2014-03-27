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
import gov.nasa.worldwind.render.DrawContext;
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

public class FeatureInfoBalloon extends DialogAnnotation implements SelectListener {
    private static final DecimalFormat NUMBER_3DP = new DecimalFormat("#0.000");
    public static final int TARGET_WIDTH = 300;
    public static final int TARGET_HEIGHT = 150;
    public static final double PREVIEW_SCALE = 0.5;
    
    private static final String RESC_CLOSE_IMAGE_PATH = "images/closeBubble.png";

    private RescWorldWindow wwd;
    private AnnotationLayer parent;

    private ScreenAnnotation titleLabel;

    private ScreenAnnotation infoLabel = null;
    private ImageAnnotation timeseriesGraph = null;
    private String timeseriesFullPath = null;
    private ImageAnnotation profileGraph = null;
    private String profileFullPath = null;

    private ImageAnnotation closeFullScreen;
    private ScreenAnnotation fullScreenGraph = null;

    private Annotation featureInfoContent;

    public FeatureInfoBalloon(Position position, RescWorldWindow wwd, AnnotationLayer parent) {
        super(position);

        this.wwd = wwd;
        this.parent = parent;

        initFeatureInfoComponents();
        layoutFeatureInfoComponents();

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
    
    protected void initFeatureInfoComponents() {
        closeFullScreen = new ButtonAnnotation(RESC_CLOSE_IMAGE_PATH, DEPRESSED_MASK_PATH);

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

        featureInfoContent = new ScreenAnnotation("", new java.awt.Point());
        this.setupContainer(featureInfoContent);
        featureInfoContent.setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, 4)); // hgap, vgap

        ScreenAnnotation balloonContent = new ScreenAnnotation("", new java.awt.Point());
        this.setupContainer(balloonContent);
        balloonContent.setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, 16)); // hgap, vgap
        balloonContent.addChild(this.titleLabel);
        balloonContent.addChild(featureInfoContent);

        addChild(balloonContent);
    }

    public void setValueText(String valueText) {
        infoLabel = new ScreenAnnotation(valueText, new Point());
        setupLabel(infoLabel);
        AnnotationAttributes attribs = infoLabel.getAttributes();
        attribs.setFont(Font.decode("Arial-BOLD-14"));
        attribs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        attribs.setSize(new Dimension(Integer.MAX_VALUE, 0));
        layoutFeatureInfoComponents();
    }

    public void setGraphs(String profilePath, String timeseriesPath) {
        if (profilePath != null) {
            profileFullPath = profilePath;
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

    protected void layoutFeatureInfoComponents() {
        featureInfoContent.removeAllChildren();
        if (infoLabel != null) {
            featureInfoContent.addChild(infoLabel);
        }
        ScreenAnnotation graphPanel = new ScreenAnnotation("", new java.awt.Point());
        this.setupContainer(graphPanel);
        graphPanel.setLayout(new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER, 4, 0)); // hgap, vgap
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
                    parent.removeAnnotation(FeatureInfoBalloon.this);
                    wwd.removeSelectListener(this);
                } else if (selectObj == profileGraph) {
                    addFullScreenAnnotation(profileFullPath);
                } else if (selectObj == timeseriesGraph) {
                    addFullScreenAnnotation(timeseriesFullPath);
                } else if (selectObj == closeFullScreen) {
                    parent.removeAnnotation(fullScreenGraph);
                    fullScreenGraph = null;
                }
                event.getMouseEvent().consume();
            } else if (event.getEventAction().equals(SelectEvent.ROLLOVER)) {
                if (selectObj == profileGraph || selectObj == timeseriesGraph) {
                    ((Component) wwd).setCursor(new Cursor(Cursor.HAND_CURSOR));
                    event.consume();
                } else {
                    ((Component) wwd).setCursor(Cursor.getDefaultCursor());
                }
            }
        }
    }

    private void addFullScreenAnnotation(String imagePath) {
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
        layout.setConstraint(closeFullScreen, AVKey.NORTHEAST);
        parent.addAnnotation(fullScreenGraph);
    }
}

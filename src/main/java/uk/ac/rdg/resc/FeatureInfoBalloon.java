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

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.render.Annotation;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.AnnotationFlowLayout;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwindx.examples.util.ButtonAnnotation;
import gov.nasa.worldwindx.examples.util.DialogAnnotation;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;

public class FeatureInfoBalloon extends DialogAnnotation implements SelectListener {
    private static final String RESC_CLOSE_IMAGE_PATH = "images/closeBubble.png";

    private RescWorldWindow wwd;
    private AnnotationLayer parent;

    private ScreenAnnotation titleLabel;
    private ScreenAnnotation infoLabel;

    private ImageAnnotation timeGraph;
    private ImageAnnotation profileGraph;

    public FeatureInfoBalloon(Position position, RescWorldWindow wwd, AnnotationLayer parent) {
        super(position);

        this.wwd = wwd;
        this.parent = parent;

        wwd.addSelectListener(this);
        //        AnnotationAttributes attributes = getAttributes();
        //        attributes.setSize(new Dimension(300, 0));
        //        setAttributes(attributes);
    }

    @Override
    protected void initComponents() {
        /*
         * We replace super.initComponents with the necessary stuff, so we can
         * use a different image for the close button
         */
        closeButton = new ButtonAnnotation(RESC_CLOSE_IMAGE_PATH, DEPRESSED_MASK_PATH);

        busyImage = new BusyImage(BUSY_IMAGE_PATH);
        
        StringBuilder title = new StringBuilder();
        title.append("Clicked (");
        title.append(position.latitude.degrees);
        title.append(", ");
        title.append(position.longitude.degrees);
        title.append(")");
        titleLabel = new ScreenAnnotation(title.toString(), new Point());
        setupLabel(titleLabel);
        
        AnnotationAttributes attribs = titleLabel.getAttributes();
        attribs.setFont(Font.decode("Arial-BOLD-14"));
        attribs.setSize(new Dimension(260, 0));
        attribs.setTextAlign(AVKey.CENTER);
        attribs.setInsets(new Insets(0, 0, 0, 32));

        infoLabel = new ScreenAnnotation("Value is 0.0", new Point());
        setupLabel(infoLabel);

        timeGraph = new ImageAnnotation("images/link.png");
        timeGraph.setPickEnabled(true);
        profileGraph = new ImageAnnotation("images/unlink.png");
        profileGraph.setPickEnabled(true);
    }

    @Override
    protected void layoutComponents() {
        /*
         * Taken from AudioPlayerAnnotation
         */
        super.layoutComponents();

        Annotation controlsContainer = new ScreenAnnotation("", new java.awt.Point());
        {
            this.setupContainer(controlsContainer);
            controlsContainer.setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.LEFT, 0, 4)); // hgap, vgap
            controlsContainer.addChild(infoLabel);
            controlsContainer.addChild(timeGraph);
            controlsContainer.addChild(profileGraph);
        }

        Annotation contentContainer = new ScreenAnnotation("", new java.awt.Point());
        {
            this.setupContainer(contentContainer);
            contentContainer
                    .setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, 16)); // hgap, vgap
            contentContainer.addChild(this.titleLabel);
            contentContainer.addChild(controlsContainer);
        }

        this.addChild(contentContainer);

        /*
         * This lays out all of the components of a feature info balloon
         */
        //        AnnotationNullLayout layout = new AnnotationNullLayout();
        //        setLayout(layout);
        //
        //        addChild(this.closeButton);
        //        layout.setConstraint(this.closeButton, AVKey.NORTHEAST);
        //
        //        AnnotationAttributes titleAttrs = new AnnotationAttributes();
        //        titleAttrs.setBorderWidth(0);
        //        titleAttrs.setCornerRadius(0);
        //        titleAttrs.setTextAlign(AVKey.CENTER);
        //        titleAttrs.setSize(new Dimension(250, 0));
        //        ScreenAnnotation titleText = new ScreenAnnotation("<b>Text1</b>",
        //                new Point(0, 0), titleAttrs);
        //        addChild(titleText);
        //        ScreenAnnotation titleText1 = new ScreenAnnotation("<b>Text2</b>",
        //                new Point(50, 50), titleAttrs);
        //        addChild(titleText1);
        //        ScreenAnnotation titleText2 = new ScreenAnnotation("<b>Text3</b>",
        //                new Point(100, 100), titleAttrs);
        //        addChild(titleText2);
        //        ScreenAnnotation titleText4 = new ScreenAnnotation("<b>Text4</b>",
        //                new Point(), titleAttrs);
        //        addChild(titleText4);
        //        //        layout.setConstraint(titleText, AVKey.NORTH);
        //
        //        ScreenAnnotation a1 = new ScreenAnnotation("annotation1", new Point());
        //        ScreenAnnotation a2 = new ScreenAnnotation("annotation2", new Point());
        //        ScreenAnnotation a3 = new ScreenAnnotation("annotation3", new Point());
        //        ScreenAnnotation a4 = new ScreenAnnotation("annotation4", new Point());
        //
        //        //        addChild(a1);
        //        //        addChild(a2);
        //        //        addChild(a3);
        //        //        addChild(a4);
        //        //        AnnotationFlowLayout layout2 = new AnnotationFlowLayout(AVKey.HORIZONTAL);
        //        //        ScreenAnnotation parent1 = new ScreenAnnotation("", new Point());
        //        //        parent1.addChild(a1);
        //        //        parent1.addChild(a2);
        //        //        parent1.setLayout(layout2);
        //        //        AnnotationFlowLayout layout3 = new AnnotationFlowLayout(AVKey.VERTICAL);
        //        //        ScreenAnnotation parent2 = new ScreenAnnotation("", new Point());
        //        //        parent2.addChild(a3);
        //        //        parent2.addChild(a4);
        //        //        parent2.setLayout(layout3);
        //        //        AnnotationFlowLayout layout4 = new AnnotationFlowLayout(AVKey.VERTICAL);
        //        //        this.addChild(parent1);
        //        //        this.addChild(parent2);
        //        //        this.setLayout(layout4);
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                Object clickedObj = event.getTopObject();
                if (clickedObj == closeButton) {
                    /*
                     * Close button clicked. Remove the annotation and the
                     * select listener and consume the mouse event so that we
                     * don't immediately get another FeatureInfoBalloon
                     * displayed
                     */
                    parent.removeAnnotation(FeatureInfoBalloon.this);
                    wwd.removeSelectListener(this);
                } else if (clickedObj == timeGraph) {
                    System.out.println("time graph clicked");
                } else if (clickedObj == profileGraph) {
                    System.out.println("profile graph clicked");
                }
                event.getMouseEvent().consume();
            }
        }
    }
}

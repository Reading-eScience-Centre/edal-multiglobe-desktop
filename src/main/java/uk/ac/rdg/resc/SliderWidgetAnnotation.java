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
import gov.nasa.worldwind.render.Annotation;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.AnnotationNullLayout;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.ScreenAnnotation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

import uk.ac.rdg.resc.SliderWidget.SliderWidgetHandler;

/**
 * Encapsualtes a {@link SliderWidget} and provides layout management and (TODO)
 * text annotations showing the current value
 * 
 * @author Guy
 */
public class SliderWidgetAnnotation extends ScreenAnnotation {

    private static final int EDGE_DISTANCE_PX = 60;
    private static final int SLIDER_WIDTH = 20;

    private final ScreenAnnotation label;
    private final SliderWidget sliderWidget;
    private final String orientation;
    private String position;

    public SliderWidgetAnnotation(String id, String orientation, String position, double min,
            double max, final RescWorldWindow wwd, final SliderWidgetHandler handler) {
        super("", new Point());

        if(orientation.equals(AVKey.VERTICAL)) {
            this.orientation = orientation;
        } else {
            this.orientation = AVKey.HORIZONTAL;
        }
        this.position = position;

        AnnotationAttributes attributes = new AnnotationAttributes();
        setupDefaultAttributes(attributes);
        getAttributes().setDefaults(attributes);

        setLayout(new AnnotationNullLayout());
        
        sliderWidget = new SliderWidget(id, orientation, min, min, max, new SliderWidgetHandler() {
            @Override
            public void sliderChanged(String id, double value) {
                /*
                 * Update the value label, then pass the change event onto other
                 * listeners
                 */
                label.setText(formatSliderValue(id, value));
                label.getAttributes().setVisible(true);
                if (handler != null) {
                    handler.sliderChanged(id, value);
                }
            }

            @Override
            public String formatSliderValue(String id, double value) {
                if (handler != null) {
                    return handler.formatSliderValue(id, value);
                }
                return "";
            }

            @Override
            public void sliderSettled() {
//                removeChild(label);
                label.getAttributes().setVisible(false);
                if(handler != null) {
                    handler.sliderSettled();
                }
                wwd.redraw();
            }
        }, wwd);
        
        addChild(sliderWidget);
        
        label = new ScreenAnnotation("", new Point());
        AnnotationAttributes labelAttrs = label.getAttributes();
        labelAttrs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        labelAttrs.setBackgroundColor(Color.black);
        labelAttrs.setTextColor(Color.lightGray);
        labelAttrs.setTextAlign(AVKey.CENTER);
        
        addChild(label);
        label.getAttributes().setVisible(false);
    }

    public boolean equalLimits(SliderWidgetAnnotation other) {
        return other.sliderWidget.max == sliderWidget.max
                && other.sliderWidget.min == sliderWidget.min;
    }

    public void setReversed(boolean reversed) {
        sliderWidget.setReversed(reversed);
    }

    public void setLimits(double min, double max) {
        sliderWidget.setMin(min);
        sliderWidget.setMax(max);
    }

    public void setSliderValue(double value) {
        sliderWidget.setValue(value);
    }

    public double getSliderValue() {
        return sliderWidget.value;
    }

    @Override
    protected void doRenderNow(DrawContext dc) {
        /*
         * Position the slider and label relative to the viewport
         */
        Rectangle viewport = dc.getView().getViewport();
        Dimension labelSize = label.getPreferredSize(dc);

        if (orientation.equals(AVKey.HORIZONTAL)) {
            sliderWidget.getAttributes().setSize(
                    new Dimension(viewport.width * 1 / 2, SLIDER_WIDTH));
            Dimension sliderSize = sliderWidget.getAttributes().getSize();
            getAttributes().setDrawOffset(new Point(0, -SLIDER_WIDTH / 2));
            label.getAttributes().setDrawOffset(
                    new Point((sliderSize.width - labelSize.width) / 2,
                            (sliderSize.height - labelSize.height) / 2));
            if (position.equals(AVKey.SOUTH)) {
                setScreenPoint(new Point(viewport.x + viewport.width / 2, EDGE_DISTANCE_PX));
            } else if (position.equals(AVKey.NORTH)) {
                setScreenPoint(new Point(viewport.x + viewport.width / 2, viewport.y
                        + viewport.height - EDGE_DISTANCE_PX));
            } else if (position.equals(AVKey.WEST)) {
                setScreenPoint(new Point(viewport.x + EDGE_DISTANCE_PX + sliderSize.width / 2
                        - SLIDER_WIDTH / 2, viewport.y + viewport.height / 2));
            } else if (position.equals(AVKey.EAST)) {
                setScreenPoint(new Point(viewport.x + viewport.width - EDGE_DISTANCE_PX
                        - sliderSize.width / 2 + SLIDER_WIDTH / 2, viewport.y + viewport.height / 2));
            }
        } else {
            sliderWidget.getAttributes().setSize(
                    new Dimension(SLIDER_WIDTH, viewport.height * 1 / 2));
            Dimension sliderSize = sliderWidget.getAttributes().getSize();
            
            int labelOffset = (int) ((label.getPreferredSize(dc).width )/ 2);
            label.getAttributes().setDrawOffset(
                    new Point(sliderSize.width/ 2 - labelOffset,
                            (sliderSize.height - labelSize.height) / 2));
            getAttributes().setDrawOffset(new Point(labelOffset , -sliderSize.height/2));
            if (position.equals(AVKey.SOUTH)) {
                setScreenPoint(new Point(viewport.x + viewport.width / 2, EDGE_DISTANCE_PX));
            } else if (position.equals(AVKey.NORTH)) {
                setScreenPoint(new Point(viewport.x + viewport.width / 2, viewport.y
                        + viewport.height - EDGE_DISTANCE_PX - sliderSize.height + SLIDER_WIDTH));
            } else if (position.equals(AVKey.WEST)) {
                setScreenPoint(new Point(viewport.x + EDGE_DISTANCE_PX, viewport.y
                        + viewport.height / 2));
            } else if (position.equals(AVKey.EAST)) {
                setScreenPoint(new Point(viewport.x + viewport.width - EDGE_DISTANCE_PX, viewport.y
                        + viewport.height / 2));
            }
        }

        super.doRenderNow(dc);
    }

    protected void setupLabel(Annotation annotation) {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        attributes.setTextColor(Color.white);
        //        attributes.setBackgroundColor(sliderWidget.backgroundColor);
        attributes.setBackgroundColor(new Color(100, 100, 100, 100));
        attributes.setBorderColor(Color.white);
        attributes.setBorderWidth(2);
        attributes.setInsets(new Insets(5, 5, 5, 5));
        attributes.setTextAlign(AVKey.CENTER);
        attributes.setCornerRadius(1);

        annotation.setPickEnabled(false);
        annotation.getAttributes().setDefaults(attributes);
    }

    protected void setupDefaultAttributes(AnnotationAttributes attributes) {
        Color transparentBlack = new Color(0, 0, 0, 0);

        attributes.setBackgroundColor(transparentBlack);
        attributes.setBorderColor(transparentBlack);
        attributes.setBorderWidth(0);
        attributes.setCornerRadius(0);
        attributes.setDrawOffset(new java.awt.Point(0, 0));
        attributes.setHighlightScale(1);
        attributes.setInsets(new java.awt.Insets(0, 0, 0, 0));
        attributes.setLeader(AVKey.SHAPE_NONE);

        /*
         * Container attributes
         */
        attributes.setAdjustWidthToText(AVKey.SIZE_FIXED);
        attributes.setSize(new java.awt.Dimension(0, 0));
    }
}

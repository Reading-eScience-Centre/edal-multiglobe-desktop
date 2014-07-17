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
import java.util.HashSet;
import java.util.Set;

import uk.ac.rdg.resc.RescWorldWindow;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.widgets.SliderWidget.SliderWidgetHandler;

/**
 * Encapsulates a {@link SliderWidget} and provides layout management, slider
 * linking and text annotations showing the current value
 * 
 * @author Guy
 */
public class SliderWidgetAnnotation extends ScreenAnnotation {

    /** The distance to leave from the edge, in pixels */
    private static final int EDGE_DISTANCE_PX = 60;
    /** The width of the slider, in pixels */
    private static final int SLIDER_WIDTH = 30;

    /** The label showing the value of the slider */
    private final ScreenAnnotation label;
    /** The slider widget */
    private final SliderWidget sliderWidget;
    /** The orientation of the slider widget */
    private final String orientation;
    /** The position of the slider widget (AVKey: NORTH, SOUTH, EAST, WEST) */
    private String position;
    /** The handler for slider events */
    private final SliderWidgetHandler handler;
    /** The RescWorldWindow which this is attached to */
    private final RescWorldWindow wwd;
    /** A Collection of other SliderWidgetAnnotations which this is linked to */
    private final Set<SliderWidgetAnnotation> linkedSliders = new HashSet<>();

    /**
     * Creates a new SliderWidgetAnnotation
     * 
     * @param id
     *            The ID of the slider
     * @param orientation
     *            The orientation
     *            {@link gov.nasa.worldwind.avlist.AVKey#VERTICAL}, otherwise
     *            assumed to be
     *            {@link gov.nasa.worldwind.avlist.AVKey#HORIZONTAL}
     * @param position
     *            The position of the slider. Can be
     *            {@link gov.nasa.worldwind.avlist.AVKey#NORTH},
     *            {@link gov.nasa.worldwind.avlist.AVKey#EAST},
     *            {@link gov.nasa.worldwind.avlist.AVKey#SOUTH}, or
     *            {@link gov.nasa.worldwind.avlist.AVKey#WEST}
     * @param min
     *            The minimum value of the slider
     * @param max
     *            The maximum value of the slider
     * @param wwd
     *            The {@link uk.ac.rdg.resc.RescWorldWindow} which this slider
     *            belongs to
     * @param handler
     *            The
     *            {@link uk.ac.rdg.resc.widgets.SliderWidget.SliderWidgetHandler}
     *            to receive slider events
     */
    public SliderWidgetAnnotation(String id, String orientation, String position, double min,
            double max, final RescWorldWindow wwd, final SliderWidgetHandler handler) {
        super("", new Point());

        if (orientation.equals(AVKey.VERTICAL)) {
            this.orientation = orientation;
        } else {
            this.orientation = AVKey.HORIZONTAL;
        }
        this.position = position;

        AnnotationAttributes attributes = new AnnotationAttributes();
        WidgetUtils.setupContainerAttributes(attributes);
        getAttributes().setDefaults(attributes);

        setLayout(new AnnotationNullLayout());

        this.handler = handler;
        this.wwd = wwd;

        /*
         * Create a new SliderWidget with a handler which draws text labels
         * stating the value, and then delegates to the SliderWidgetHandler
         * supplied in the constructor
         */
        sliderWidget = new SliderWidget(id, orientation, min, min, max, new SliderWidgetHandler() {
            @Override
            public void sliderChanged(String id, double value, Extent<Double> valueRange,
                    boolean calledFromLinked) {
                /*
                 * Update the value label, then pass the change event onto other
                 * listeners
                 */
                String sliderText = formatSliderValue(id, value);
                if (sliderText != null) {
                    label.setText(sliderText);
                    label.getAttributes().setVisible(true);
                }
                if (handler != null) {
                    handler.sliderChanged(id, value, valueRange, false);
                }
                for (SliderWidgetAnnotation linked : linkedSliders) {
                    linked.label.setText(formatSliderValue(id, value));
                    linked.label.getAttributes().setVisible(true);
                    linked.sliderWidget.setValue(value);
                    linked.sliderWidget.setValueRange(valueRange.getHigh() - valueRange.getLow());
                    if (linked.handler != null) {
                        linked.handler.sliderChanged(id, value, valueRange, true);
                    }
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
            public void sliderSettled(String id) {
                label.getAttributes().setVisible(false);
                wwd.redraw();
                for (SliderWidgetAnnotation linked : linkedSliders) {
                    linked.label.getAttributes().setVisible(false);
                    linked.wwd.redraw();
                }
                if (handler != null) {
                    handler.sliderSettled(id);
                }
                for (SliderWidgetAnnotation linked : linkedSliders) {
                    if (linked.handler != null) {
                        linked.handler.sliderSettled(id);
                    }
                }
            }
        }, wwd);

        sliderWidget.setCentreColor(Color.yellow);
        addChild(sliderWidget);

        /*
         * Sets up the label attributes
         */
        label = new ScreenAnnotation("", new Point());
        AnnotationAttributes labelAttrs = label.getAttributes();
        labelAttrs.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        labelAttrs.setBackgroundColor(Color.black);
        labelAttrs.setTextColor(Color.lightGray);
        labelAttrs.setTextAlign(AVKey.CENTER);
        labelAttrs.setOpacity(0.8);

        addChild(label);
        label.getAttributes().setVisible(false);
    }

    /**
     * Calls to set an indicator that changing this slider will not result in
     * smooth transitions
     */
    public void setNotCached() {
        sliderWidget.setCentreColor(Color.red);
    }

    /**
     * Calls to set an indicator that changing this slider will result in smooth
     * transitions
     */
    public void setCached() {
        sliderWidget.setCentreColor(Color.green);
    }

    /**
     * Links this slider with another
     * 
     * @param other
     *            The other slider to link to
     */
    public void linkSlider(SliderWidgetAnnotation other) {
        linkedSliders.add(other);
        other.linkedSliders.add(this);
    }

    /**
     * @param other
     *            Another
     *            {@link uk.ac.rdg.resc.widgets.SliderWidget.SliderWidgetHandler}
     *            to compare limits with
     * @return <code>true</code> if the limits match
     */
    public boolean equalLimits(SliderWidgetAnnotation other) {
        return other.sliderWidget.max == sliderWidget.max
                && other.sliderWidget.min == sliderWidget.min;
    }

    /**
     * Sets whether the top/right of this slider represents the highest value
     * 
     * @param reversed
     *            <code>true</code> if this slider is reversed compared to the
     *            normal slider direction
     */
    public void setReversed(boolean reversed) {
        sliderWidget.setReversed(reversed);
    }

    /**
     * Sets the limits of this slider
     * 
     * @param min
     *            The minimum value of the slider
     * @param max
     *            The maximum value of the slider
     */
    public void setLimits(double min, double max) {
        sliderWidget.setMin(min);
        sliderWidget.setMax(max);
    }

    public void changeByFrac(double frac) {
        double change = frac * (sliderWidget.max - sliderWidget.min);
        setSliderValue(sliderWidget.value + change);
    }

    /**
     * Sets the selected value of this slider
     * 
     * @param value
     *            The desired value
     */
    public void setSliderValue(double value) {
        sliderWidget.setValue(value);
    }

    /**
     * @return The selected value of this slider
     */
    public double getSliderValue() {
        return sliderWidget.value;
    }

    /**
     * @return The selected range of this slider
     */
    public Extent<Double> getSliderRange() {
        return sliderWidget.getValueRange();
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

            int labelOffset = (int) ((label.getPreferredSize(dc).width) / 2);
            label.getAttributes().setDrawOffset(
                    new Point(sliderSize.width / 2 - labelOffset,
                            (sliderSize.height - labelSize.height) / 2));
            getAttributes().setDrawOffset(new Point(labelOffset, -sliderSize.height / 2));
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

    /**
     * Sets the annotation attributes for a label
     * 
     * @param annotation
     */
    protected void setupLabel(Annotation annotation) {
        AnnotationAttributes attributes = new AnnotationAttributes();
        attributes.setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        attributes.setTextColor(Color.white);
        attributes.setBackgroundColor(new Color(100, 100, 100, 100));
        attributes.setBorderColor(Color.white);
        attributes.setBorderWidth(2);
        attributes.setInsets(new Insets(5, 5, 5, 5));
        attributes.setTextAlign(AVKey.CENTER);
        attributes.setCornerRadius(1);

        annotation.setPickEnabled(false);
        annotation.getAttributes().setDefaults(attributes);
    }
}

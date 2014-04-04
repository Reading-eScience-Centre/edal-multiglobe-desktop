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

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.FrameFactory;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.OGLStackHandler;
import gov.nasa.worldwindx.examples.util.ProgressAnnotation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.nio.DoubleBuffer;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.util.Extents;

/**
 * A {@link ScreenAnnotation} which allows users to slide the bar and add a
 * handler to detect sliding events.
 * 
 * The initial implementation was strongly based on {@link ProgressAnnotation}
 */
public class SliderWidget extends ScreenAnnotation implements SelectListener {
    /*
     * These should be divisible by 2
     */
    private static final int CIRCLE_SIZE = 32;
    private static final int CENTRE_SIZE = 8;

    final String id;

    private boolean reversed = false;
    protected double value;
    protected double valueRange;
    protected double min;
    protected double max;
    protected Color backgroundColor;
    protected Color elementColor;
    protected Insets interiorInsets;

    private String orientation;
    SliderWidgetHandler handler;
    private Point lastDragPoint = null;

    private DoubleBuffer elementBuffer;
    private DoubleBuffer centreElementBuffer;

    private Thread sliderTimer = null;

    public SliderWidget(String id, String orientation, double value, double min, double max,
            SliderWidgetHandler handler, WorldWindow wwd) {
        super("", new java.awt.Point());

        this.id = id;

        if (orientation.equals(AVKey.VERTICAL)) {
            this.orientation = orientation;
        } else {
            this.orientation = AVKey.HORIZONTAL;
        }

        this.value = value;
        this.min = min;
        this.max = max;

        this.valueRange = 0.0;

        this.handler = handler;

        this.backgroundColor = new Color(60, 60, 60, 128);
        this.elementColor = new Color(171, 171, 171, 196);
        this.interiorInsets = new Insets(2, 2, 2, 2);

        setPickEnabled(true);

        Color transparentBlack = new Color(0, 0, 0, 0);

        AnnotationAttributes sliderAttributes = getAttributes();
        sliderAttributes.setBackgroundColor(transparentBlack);
        sliderAttributes.setBorderColor(transparentBlack);
        sliderAttributes.setBorderWidth(0);
        sliderAttributes.setCornerRadius(0);
        sliderAttributes.setDrawOffset(new java.awt.Point(0, 0));
        sliderAttributes.setHighlightScale(1);
        sliderAttributes.setInsets(new java.awt.Insets(0, 0, 0, 0));
        sliderAttributes.setLeader(AVKey.SHAPE_NONE);

        /*
         * Set default size based on orientation
         */
        if (orientation.equals(AVKey.HORIZONTAL)) {
            sliderAttributes.setSize(new Dimension(400, 10));
        } else {
            sliderAttributes.setSize(new Dimension(10, 400));
        }

        elementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_ELLIPSE, CIRCLE_SIZE,
                CIRCLE_SIZE, 0, null);
        centreElementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_ELLIPSE, CENTRE_SIZE,
                CENTRE_SIZE, 0, null);

        wwd.addSelectListener(this);
    }

    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        if (value < min) {
            value = min;
        } else if (value > max) {
            value = max;
        }
        this.value = value;
    }

    public Extent<Double> getValueRange() {
        return Extents.newExtent(value - valueRange, value + valueRange);
    }

    public void setValueRange(double valueRange) {
        if (valueRange < 0.0) {
            valueRange = 0;
        } else if (valueRange > (max - min)) {
            valueRange = (max - min);
        }
        this.valueRange = valueRange;
        //        recalculateElementBuffer = true;
        setValue(value);
    }

    public void setMin(double min) {
        this.min = min;
        if (value < min) {
            value = min;
        }
    }

    public void setMax(double max) {
        this.max = max;
        if (value > max) {
            value = max;
        }
    }

    public void setOutlineColor(java.awt.Color color) {
        if (color == null) {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.backgroundColor = color;
    }

    public void setInteriorColor(java.awt.Color color) {
        if (color == null) {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.elementColor = color;
    }

    public void setInteriorInsets(java.awt.Insets insets) {
        if (insets == null) {
            String message = Logging.getMessage("nullValue.InsetsIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        // Class java.awt.Insets is known to override the method Object.clone().
        this.interiorInsets = (java.awt.Insets) insets.clone();
    }

    //**************************************************************//
    //********************  Rendering  *****************************//
    //**************************************************************//

    @Override
    protected void doDraw(DrawContext dc, int width, int height, double opacity,
            Position pickPosition) {
        super.doDraw(dc, width, height, opacity, pickPosition);
        this.drawSlider(dc, width, height, opacity, pickPosition);
    }

    protected void drawSlider(DrawContext dc, int width, int height, double opacity,
            Position pickPosition) {
        if (dc.isPickingMode())
            return;

        this.drawSliderBackground(dc, width, height, opacity, pickPosition);
        this.drawSliderElement(dc, width, height, opacity, pickPosition);
    }

    protected void drawSliderBackground(DrawContext dc, int width, int height, double opacity,
            Position pickPosition) {
        Rectangle bounds = computeInsetBounds(width, height);

        GL gl = dc.getGL();
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glLineWidth(1);

        applyColor(dc, backgroundColor, opacity, false);
        drawCallout(dc, GL.GL_TRIANGLE_FAN, bounds, false);
    }

    protected void drawCallout(DrawContext dc, int mode, Rectangle bounds, boolean useTexCoords) {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

        OGLStackHandler stackHandler = new OGLStackHandler();
        stackHandler.pushModelview(gl);

        gl.glTranslated(bounds.x, bounds.y, 0);
        this.drawCallout(dc, mode, bounds.width, bounds.height, useTexCoords);

        stackHandler.pop(gl);
    }

    protected void drawSliderElement(DrawContext dc, int width, int height, double opacity,
            Position pickPosition) {
        Rectangle location = computeSliderLocation(width, height);

        applyColor(dc, elementColor, opacity, true);
        dc.getGL().getGL2().glTranslated(location.x, location.y, 0);
        //        if (recalculateElementBuffer) {
        recalculateElementBuffer(width, height);
        //            recalculateElementBuffer = false;
        //        }
        FrameFactory.drawBuffer(dc, GL.GL_TRIANGLE_FAN, elementBuffer);
        dc.getGL()
                .getGL2()
                .glTranslated(location.width + (CIRCLE_SIZE - CENTRE_SIZE) / 2,
                        location.height + (CIRCLE_SIZE - CENTRE_SIZE) / 2, 0);
        FrameFactory.drawBuffer(dc, GL.GL_TRIANGLE_FAN, centreElementBuffer);
    }

    private void recalculateElementBuffer(int width, int height) {
        double valueMin = value - valueRange;
        double valueMax = value + valueRange;
        if (valueMin < this.min) {
            valueMin = this.min;
        }
        if (valueMax > this.max) {
            valueMax = this.max;
        }
        if (orientation.equals(AVKey.HORIZONTAL)) {
            int min = computeSliderRelativePos(width, valueMin) - CIRCLE_SIZE / 2;
            int max = computeSliderRelativePos(width, valueMax) - CIRCLE_SIZE / 2;
            if (!reversed) {
                elementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_RECTANGLE, max - min
                        + CIRCLE_SIZE, CIRCLE_SIZE, CIRCLE_SIZE / 2, null);
            } else {
                elementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_RECTANGLE, min - max
                        + CIRCLE_SIZE, CIRCLE_SIZE, CIRCLE_SIZE / 2, null);
            }
        } else {
            int min = computeSliderRelativePos(height, valueMin) - CIRCLE_SIZE / 2;
            int max = computeSliderRelativePos(height, valueMax) - CIRCLE_SIZE / 2;
            if (!reversed) {
                elementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_RECTANGLE, CIRCLE_SIZE,
                        max - min + CIRCLE_SIZE, CIRCLE_SIZE / 2, null);
            } else {
                elementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_RECTANGLE, CIRCLE_SIZE,
                        min - max + CIRCLE_SIZE, CIRCLE_SIZE / 2, null);

            }
        }
    }

    /**
     * This method uses
     * 
     * @param width
     * @param height
     * @return
     */
    protected Rectangle computeSliderLocation(int width, int height) {
        double valueMin = value - valueRange;
        double valueMax = value + valueRange;
        if (valueMin < this.min) {
            valueMin = this.min;
        }
        if (valueMax > this.max) {
            valueMax = this.max;
        }
        Rectangle containerBounds = computeInsetBounds(width, height);
        if (orientation == AVKey.HORIZONTAL) {
            int y = containerBounds.y + containerBounds.height / 2 - CIRCLE_SIZE / 2;
            if (!reversed) {
                int xMin = computeSliderRelativePos(containerBounds.width, valueMin) - CIRCLE_SIZE
                        / 2;
                int xMax = computeSliderRelativePos(containerBounds.width, value) - CIRCLE_SIZE / 2;
                return new Rectangle(xMin, y, xMax - xMin, 0);
            } else {
                int xMin = computeSliderRelativePos(containerBounds.width, value) - CIRCLE_SIZE / 2;
                int xMax = computeSliderRelativePos(containerBounds.width, valueMax) - CIRCLE_SIZE
                        / 2;
                return new Rectangle(xMax, y, xMin - xMax, 0);
            }
        } else {
            int x = containerBounds.x + containerBounds.width / 2 - CIRCLE_SIZE / 2;
            int yMax = computeSliderRelativePos(containerBounds.height, value) - CIRCLE_SIZE / 2;
            int yMin;
            if (!reversed) {
                yMin = computeSliderRelativePos(containerBounds.height, valueMin) - CIRCLE_SIZE / 2;
            } else {
                yMin = computeSliderRelativePos(containerBounds.height, valueMax) - CIRCLE_SIZE / 2;
            }
            return new Rectangle(x, yMin, 0, yMax - yMin);
        }
    }

    protected int computeSliderRelativePos(int barSize, double value) {
        double factor = (value - min) / (max - min);
        if (reversed) {
            return (int) ((1.0 - factor) * barSize);
        } else {
            return (int) (factor * barSize);
        }
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            Object topObject = event.getTopObject();
            if (topObject == this) {
                if (event.isLeftPress()) {
                    lastDragPoint = event.getPickPoint();
                }
                if (event.isDrag()) {
                    Point point = event.getMouseEvent().getPoint();
                    double percMove;
                    if (orientation.equals(AVKey.HORIZONTAL)) {
                        int diff = point.x - lastDragPoint.x;
                        percMove = diff / (double) getAttributes().getSize().width;
                    } else {
                        int diff = lastDragPoint.y - point.y;
                        percMove = diff / (double) getAttributes().getSize().height;
                    }
                    double valueDiff = percMove * (max - min);

                    if (!event.getMouseEvent().isShiftDown()) {
                        /*
                         * Normal drag - move the slider
                         */
                        double value;
                        if (reversed) {
                            value = getValue() - valueDiff;
                        } else {
                            value = getValue() + valueDiff;
                        }
                        lastDragPoint = point;
                        setValue(value);
                    } else {
                        /*
                         * Shift-drag - we want to change slider range
                         */
                        double valueRange;
                        valueRange = this.valueRange + valueDiff;
                        setValueRange(valueRange);
                    }
                    lastDragPoint = point;
                    if (handler != null) {
                        resetSliderTimer();
                        handler.sliderChanged(id, getValue(), getValueRange());
                        sliderTimer.start();
                    }
                    event.getMouseEvent().consume();
                }
            }
        }
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
                    if (handler != null) {
                        handler.sliderSettled(id);
                    }
                } catch (InterruptedException e) {
                    /*
                     * Don't do anything - the timer was interrupted
                     * deliberately
                     */
                }
            }
        });
    }

    public interface SliderWidgetHandler {
        /**
         * Gets called when the value of the slider changes
         * 
         * @param id
         *            The ID of the slider which has changed
         * @param value
         *            The double value which the slider has changed to
         */
        public void sliderChanged(String id, double value, Extent<Double> valueRange);

        /**
         * Formats a slider value as text
         * 
         * @param id
         *            The ID of the slider
         * @param value
         *            The value to format
         * @return A formatted string representing the slider's value
         */
        public String formatSliderValue(String id, double value);

        /**
         * Called once a slider has stopped moving for 500ms
         * 
         * @param id
         *            The ID of the slider which has stopped moving
         */
        public void sliderSettled(String id);
    }
}

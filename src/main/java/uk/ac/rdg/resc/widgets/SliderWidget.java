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
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.util.Extents;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import java.awt.*;
import java.nio.DoubleBuffer;

/**
 * A {@link ScreenAnnotation} which allows users to slide the bar and add a
 * handler to detect sliding events.
 * <p/>
 * This {@link SliderWidget} represents a single value as well as a value range
 * (the selected value +- a range). The range can be adjusted by shift-dragging
 * the slider (as opposed to normal dragging to change the value)
 * <p/>
 * The initial implementation was strongly based on ProgressAnnotation
 */
public class SliderWidget extends ScreenAnnotation implements SelectListener {
    /*
     * These should be divisible by 2
     */
    private static final int CIRCLE_SIZE = 32;
    private static final int CENTRE_SIZE = 8;

    /**
     * The ID of this slider. This will passed to any handlers
     */
    final String id;

    /**
     * true if the right/top of the slider is the lowest value
     */
    private boolean reversed = false;
    /**
     * Current value of the slider
     */
    protected double value;
    /**
     * Current range of the slider
     */
    protected double valueRange;
    /**
     * Minimum value of the slider
     */
    protected double min;
    /**
     * Maximum value of the slider
     */
    protected double max;
    /**
     * Background colour of the slider
     */
    protected Color backgroundColor;
    /**
     * Colour of the indicator element of the slider
     */
    protected Color elementColor;
    /**
     * Colour of the centre of the indicator element of the slider
     */
    protected Color centreColor;

    /**
     * The orientation of the slider. Can be {@link AVKey#HORIZONTAL} or
     * {@link AVKey#VERTICAL}
     */
    private String orientation;
    /**
     * A {@link SliderWidgetHandler} to send events to when the value/range
     * changes
     */
    private SliderWidgetHandler handler;
    /**
     * Used to calculate drag amount
     */
    private Point lastDragPoint = null;

    /**
     * Graphics buffer for drawing the element
     */
    private DoubleBuffer elementBuffer;
    /**
     * Graphics buffer for drawing the centre of the element
     */
    private DoubleBuffer centreElementBuffer;

    /**
     * A timer to measure when the slider has been immobile for a certain amount
     * of time
     */
    private Thread sliderTimer = null;

    /**
     * Instantiate a new {@link SliderWidget}
     * 
     * @param id
     *            The ID of this slider
     * @param orientation
     *            The orientation, can be {@link AVKey#VERTICAL}, otherwise
     *            assumed to be {@link AVKey#HORIZONTAL}
     * @param value
     *            The starting value of the slider
     * @param min
     *            The minimum value of the slider
     * @param max
     *            The maximum value of the slider
     * @param handler
     *            The {@link SliderWidgetHandler} to receive events when this
     *            changes
     * @param wwd
     *            The {@link WorldWind} which this slider is attached to
     */
    public SliderWidget(String id, String orientation, double value, double min, double max,
            SliderWidgetHandler handler, WorldWindow wwd) {
        super("", new java.awt.Point());

        /*
         * Setup variables
         */
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
        /*
         * Register select handler to allow dragging
         */
        setPickEnabled(true);
        wwd.addSelectListener(this);
    }

    /**
     * Sets whether the top/right of this slider represents the highest value
     * 
     * @param reversed
     *            <code>true</code> if this slider is reversed compared to the
     *            normal slider direction
     */
    public void setReversed(boolean reversed) {
        this.reversed = reversed;
    }

    /**
     * @return The currently selected value of the slider
     */
    public double getValue() {
        return value;
    }

    /**
     * Sets the value of the slider. The element will be moved on the next
     * redraw
     * 
     * @param value
     *            The desired value
     */
    public void setValue(double value) {
        if (value < min) {
            value = min;
        } else if (value > max) {
            value = max;
        }
        this.value = value;

//        if (handler != null) {
//            resetSliderTimer();
//            sliderTimer.start();
//        }
    }

    /**
     * @return The currently selected value range. This is the selected value +-
     *         a target range
     */
    public Extent<Double> getValueRange() {
        return Extents.newExtent(value - valueRange, value + valueRange);
    }

    /**
     * Sets the target range. The entire value range will be the selected value
     * +- this value
     * 
     * @param valueRange
     *            The range to set
     */
    public void setValueRange(double valueRange) {
        if (valueRange < 0.0) {
            valueRange = 0;
        } else if (valueRange > (max - min)) {
            valueRange = (max - min);
        }
        this.valueRange = valueRange;
        setValue(value);
    }

    /**
     * Sets the minimum value of the slider
     * 
     * @param min
     *            The minimum value
     */
    public void setMin(double min) {
        this.min = min;
        if (value < min) {
            value = min;
        }
    }

    /**
     * Sets the maximum value of the slider
     * 
     * @param max
     *            The maximum value of the slider
     */
    public void setMax(double max) {
        this.max = max;
        if (value > max) {
            value = max;
        }
    }

    /**
     * Sets the colour of the main slider
     * 
     * @param color
     *            The {@link Color} to use
     */
    public void setBackgroundColor(Color color) {
        if (color == null) {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.backgroundColor = color;
    }

    /**
     * Sets the colour of the moveable element
     * 
     * @param colour
     *            The {@link Color} to use
     */
    public void setElementColor(Color colour) {
        if (colour == null) {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        this.elementColor = colour;
    }
    
    /**
     * Sets the colour of the centre of the moveable element
     * 
     * @param colour
     *            The {@link Color} to use
     */
    public void setCentreColor(Color colour) {
        if (colour == null) {
            String message = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        
        this.centreColor = colour;
    }

    // **************************************************************//
    // ******************** Rendering *****************************//
    // **************************************************************//

    @Override
    protected void doDraw(DrawContext dc, int width, int height, double opacity,
            Position pickPosition) {
        super.doDraw(dc, width, height, opacity, pickPosition);
        this.drawSlider(dc, width, height, opacity);
    }

    /**
     * Draws the slider
     * 
     * @param dc
     *            The {@link DrawContext} to use
     * @param width
     *            The width of the slider space
     * @param height
     *            The height of the slider space
     * @param opacity
     *            The overall opacity
     */
    protected void drawSlider(DrawContext dc, int width, int height, double opacity) {
        if (dc.isPickingMode())
            return;

        this.drawSliderBackground(dc, width, height, opacity);
        this.drawSliderElement(dc, width, height, opacity);
    }

    /**
     * Draws the main background of the slider (the bit that the element slides
     * along)
     * 
     * @param dc
     *            The {@link DrawContext} to use
     * @param width
     *            The width of the slider space
     * @param height
     *            The height of the slider space
     * @param opacity
     *            The overall opacity
     */
    protected void drawSliderBackground(DrawContext dc, int width, int height, double opacity) {
        Rectangle bounds = computeInsetBounds(width, height);

        GL gl = dc.getGL();
        gl.glEnable(GL.GL_LINE_SMOOTH);
        gl.glHint(GL.GL_LINE_SMOOTH_HINT, GL.GL_NICEST);
        gl.glLineWidth(1);

        applyColor(dc, backgroundColor, opacity, false);
        drawCallout(dc, GL.GL_TRIANGLE_FAN, bounds, false);
    }

    protected void drawCallout(DrawContext dc, int mode, Rectangle bounds, boolean useTexCoords) {
        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2
                                      // compatibility.

        OGLStackHandler stackHandler = new OGLStackHandler();
        stackHandler.pushModelview(gl);

        gl.glTranslated(bounds.x, bounds.y, 0);
        this.drawCallout(dc, mode, bounds.width, bounds.height, useTexCoords);

        stackHandler.pop(gl);
    }

    /**
     * Draws the moveable element of the slider
     * 
     * @param dc
     *            The {@link DrawContext} to use
     * @param width
     *            The width of the slider space
     * @param height
     *            The height of the slider space
     * @param opacity
     *            The overall opacity
     */
    protected void drawSliderElement(DrawContext dc, int width, int height, double opacity) {
        /*
         * Calculate the location of the element
         */
        Rectangle location = computeElementLocation(width, height);

        /*
         * Recalculate the graphics buffer for the element
         */
        recalculateElementBuffer(width, height);
        /*
         * Now do the actual drawing
         */
        applyColor(dc, elementColor, opacity, true);
        dc.getGL().getGL2().glTranslated(location.x, location.y, 0);
        FrameFactory.drawBuffer(dc, GL.GL_TRIANGLE_FAN, elementBuffer);
        dc.getGL()
                .getGL2()
                .glTranslated(location.width + (CIRCLE_SIZE - CENTRE_SIZE) / 2,
                        location.height + (CIRCLE_SIZE - CENTRE_SIZE) / 2, 0);
        applyColor(dc, centreColor, opacity, true);
        FrameFactory.drawBuffer(dc, GL.GL_TRIANGLE_FAN, centreElementBuffer);
    }

    /**
     * Recalculates the {@link java.nio.DoubleBuffer} storing the element data
     * 
     * @param width
     *            The width of the slider space (i.e. not the element)
     * @param height
     *            The height of the slider space (i.e. not the element)
     */
    private void recalculateElementBuffer(int width, int height) {
        /*
         * Find the end values to draw, truncating if required
         */
        double valueMin = value - valueRange;
        double valueMax = value + valueRange;
        if (valueMin < this.min) {
            valueMin = this.min;
        }
        if (valueMax > this.max) {
            valueMax = this.max;
        }
        /*
         * Calculate the position and size, then regenerate the element buffer
         */
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
     * This method calculates the location of the slider element
     * 
     * @param width
     *            The width of the slider space
     * @param height
     *            The height of the slider space
     * @return A {@link java.awt.Rectangle} which will be zero-width and
     *         represent the space taken by the centre of the sliding element
     *         (i.e. for a slider with zero range, all 4 corners will be the
     *         same point)
     */
    protected Rectangle computeElementLocation(int width, int height) {
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

    /**
     * Calculates the relative position of a value
     * 
     * @param barSize
     *            The length/width of the slider
     * @param value
     *            The value to calculate
     * @return The offset, in pixels, of the given value
     */
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
                    /* Set the last drag point */
                    lastDragPoint = event.getPickPoint();
                }
                if (event.isDrag()) {
                    /*
                     * Calculate how much the mouse has moved and set the value
                     * accordingly
                     */
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
                    /*
                     * Start a new timer ready to trigger an event in 0.5s
                     * unless more dragging occurs
                     */
                    if (handler != null) {
                        resetSliderTimer();
                        handler.sliderChanged(id, getValue(), getValueRange(), false);
                        sliderTimer.start();
                    }
                    /*
                     * Consume the mouse event so that it doesn't get picked up
                     * elsewhere
                     */
                    event.getMouseEvent().consume();
                }
            }
        }
    }

    /**
     * Resets the timer used to trigger a
     * {@link uk.ac.rdg.resc.widgets.SliderWidget.SliderWidgetHandler#sliderSettled(String)}
     * call
     */
    private void resetSliderTimer() {
        if (sliderTimer != null) {
            sliderTimer.interrupt();
        }
        sliderTimer = new Thread(new Runnable() {
            @Override
            public void run() {
                /*
                 * Sleep for 0.5 second and then call the sliderSettled method
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
         * @param valueRange
         *            The value range which the slider has changed to
         * @param calledFromLinked
         *            Whether this method was called from a linked slider or not
         */
        public void sliderChanged(String id, double value, Extent<Double> valueRange,
                boolean calledFromLinked);

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

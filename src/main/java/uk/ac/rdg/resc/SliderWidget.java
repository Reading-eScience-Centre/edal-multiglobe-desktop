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

/**
 * A {@link ScreenAnnotation} which allows users to slide the bar and add a
 * handler to detect sliding events.
 * 
 * The initial implementation was strongly based on {@link ProgressAnnotation}
 */
public class SliderWidget extends ScreenAnnotation implements SelectListener {
    private static final int CIRCLE_SIZE = 22;

    private final String id;

    private boolean reversed = false;
    protected double value;
    protected double min;
    protected double max;
    protected Color backgroundColor;
    protected Color elementColor;
    protected Insets interiorInsets;

    //    private boolean highlighted = false;
//    private Color highlightColor;

    private String orientation;
    private SliderWidgetHandler handler;
    private Point lastDragPoint = null;
    
    private DoubleBuffer circleElementBuffer;
    
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

        this.handler = handler;

        this.backgroundColor = new Color(60, 60, 60, 128);
//        this.highlightColor = new Color(120, 120, 120, 196);
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

        circleElementBuffer = FrameFactory.createShapeBuffer(AVKey.SHAPE_ELLIPSE, CIRCLE_SIZE,
                CIRCLE_SIZE, 0, null);

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
        Point location = computeSliderLocation(width, height);

        applyColor(dc, elementColor, opacity, true);
        dc.getGL().getGL2().glTranslated(location.x, location.y, 0);
        FrameFactory.drawBuffer(dc, GL.GL_TRIANGLE_FAN, circleElementBuffer);
    }

    protected Point computeSliderLocation(int width, int height) {
        Rectangle containerBounds = computeInsetBounds(width, height);
        if (orientation.equals(AVKey.HORIZONTAL)) {
            return new Point(computeSliderRelativePos(containerBounds.width) - CIRCLE_SIZE / 2,
                    containerBounds.y + containerBounds.height / 2 - CIRCLE_SIZE / 2);
        } else {
            return new Point(containerBounds.x + containerBounds.width / 2 - CIRCLE_SIZE / 2,
                    computeSliderRelativePos(containerBounds.height) - CIRCLE_SIZE / 2);
        }
    }

    protected int computeSliderRelativePos(int barSize) {
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
                    //                    highlighted = true;
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

                    double value;
                    if (reversed) {
                        value = getValue() - valueDiff;
                    } else {
                        value = getValue() + valueDiff;
                    }
                    lastDragPoint = point;
                    setValue(value);
                    if (handler != null) {
                        resetSliderTimer();
                        handler.sliderChanged(id, getValue());
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
                    if(handler != null) {
                        handler.sliderSettled();
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
        public void sliderChanged(String id, double value);

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
         */
        public void sliderSettled();
    }
}

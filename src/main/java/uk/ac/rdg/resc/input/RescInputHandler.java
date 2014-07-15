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

package uk.ac.rdg.resc.input;

import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.awt.AWTInputHandler;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;

import java.awt.event.MouseEvent;

import javafx.embed.swing.SwingNode;
import javafx.event.EventHandler;
import javafx.scene.input.RotateEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.input.ZoomEvent;
import uk.ac.rdg.resc.LinkedView;
import uk.ac.rdg.resc.RescWorldWindow;

/**
 * An input handler which copies all behaviour from {@link AWTInputHandler}.
 * Will be extended for multitouch at some point, as well as possibly space
 * mouse and kinect
 * 
 * @author Guy Griffiths
 */
public class RescInputHandler extends AWTInputHandler {
    private RescWorldWindow rescWorldWindow = null;
    private boolean touchScroll = false;
    private boolean tapping = false;
    private int fingersOn = 0;
    private LinkedView view;

    public RescInputHandler(final SwingNode container, final RescWorldWindow rescWorldWindow) {
        setEventSource(rescWorldWindow);
        /*
         * Turn off smooth view changes. See overridden method
         * setSmoothViewChanges for more details
         */
        rescWorldWindow.getView().getViewInputHandler().setEnableSmoothing(false);

        /*
         * Now add handlers for JavaFX events
         */

        /*
         * Keep track of the number of fingers on the screen so that we can
         * separate e.g. zoom events and scroll events
         */
        container.setOnTouchPressed(new EventHandler<TouchEvent>() {
            @Override
            public void handle(TouchEvent event) {
                fingersOn = event.getTouchCount();

                if (fingersOn == 1) {
                    (new Thread(new Runnable() {
                        @Override
                        public void run() {
                            tapping = true;
                            /*
                             * Sleep for 0.05 second and then finish
                             */
                            try {
                                Thread.sleep(100L);
                            } catch (InterruptedException e) {
                                /*
                                 * Don't do anything - the timer was interrupted
                                 * deliberately
                                 */
                            }
                            tapping = false;
                        }
                    })).start();
                } else {
                    tapping = false;
                }
            }
        });

        container.setOnTouchReleased(new EventHandler<TouchEvent>() {
            @Override
            public void handle(TouchEvent event) {
                fingersOn = event.getTouchCount();
                if (tapping && fingersOn == 1) {
                    tapping = false;
                    Position touchedPos = rescWorldWindow.getView().computePositionFromScreenPoint(
                            event.getTouchPoint().getX(), event.getTouchPoint().getY());
                    if (touchedPos != null) {
                        rescWorldWindow.getModel().showFeatureInfo(touchedPos, false);
                        event.consume();
                    }
                }
            }
        });

        container.setOnTouchMoved(new EventHandler<TouchEvent>() {
            @Override
            public void handle(TouchEvent event) {
                if (fingersOn == 1) {

                }
            }
        });

        /*
         * Handler for the 2-finger rotate event
         */
        container.setOnRotate(new EventHandler<RotateEvent>() {
            @Override
            public void handle(RotateEvent re) {
                double angle = re.getAngle();

                if (fingersOn == 2) {
                    view.changeHeading(Angle.fromDegrees(-angle));
                    view.firePropertyChange(AVKey.VIEW, null, view);
                }
            }
        });

        /*
         * Handler for the 2-finger zoom event
         */
        container.setOnZoom(new EventHandler<ZoomEvent>() {
            @Override
            public void handle(ZoomEvent ze) {
                if (fingersOn == 2) {
                    view.changeZoom(1.0 / ze.getZoomFactor());
                    view.firePropertyChange(AVKey.VIEW, null, view);
                }
            }
        });

        /*
         * The difference between touch scroll events and mouse scroll events is
         * that touch ones generate start and end events. We can use this to
         * keep track of whether a scroll event is a touch event or not
         */
        container.setOnScrollStarted(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                touchScroll = true;
            }
        });

        container.setOnScrollFinished(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                touchScroll = false;
            }
        });

        container.setOnScroll(new EventHandler<ScrollEvent>() {
            @Override
            public void handle(ScrollEvent event) {
                if (touchScroll || event.isInertia()) {
                    /*
                     * This is a scroll event generated by touch (either
                     * currently or in the past).
                     * 
                     * We may want to use this to adjust the pitch angle, but
                     * that's probably not a widely used view alteration and may
                     * cause more hassle than it's worth.
                     */
                    event.consume();
                    if (event.getTouchCount() == 3) {
                        /*
                         * Treat 3-fingered scroll events as modifications to
                         * the time/depth.
                         * 
                         * This transformation of deltaX/Y means that a half
                         * panel scroll corresponds to the entire range. This
                         * matches the size of the sliders.
                         */
                        double deltaX = 2 * event.getDeltaX()
                                / container.getBoundsInLocal().getWidth();
                        double deltaY = 2 * event.getDeltaY()
                                / container.getBoundsInLocal().getHeight();
                        /*
                         * Test whether this is a horizontal or vertical drag
                         * and change time/elevation accordingly
                         */
                        if (Math.abs(deltaX) > Math.abs(deltaY)) {
                            rescWorldWindow.getModel().changeTimeSlider(deltaX);
                        } else {
                            rescWorldWindow.getModel().changeElevationSlider(deltaY);
                        }
                    }
                } else {
                    /*
                     * We don't want touch scrolling to zoom, only mouse
                     * scrolling.
                     */
                    view.changeZoom(1.0 - event.getDeltaY() / 200.0);
                    view.firePropertyChange(AVKey.VIEW, null, view);
                }
                event.consume();
            }
        });
    }

    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
        /*
         * We only want true mouse clicks to register (i.e. not single press
         * touch events)
         */
        super.mouseClicked(mouseEvent);
        if (fingersOn == 0) {
            if (!mouseEvent.isConsumed() && mouseEvent.getButton() == MouseEvent.BUTTON1) {
                Position clickPos = rescWorldWindow.getView().computePositionFromScreenPoint(
                        mouseEvent.getX(), mouseEvent.getY());
                if (clickPos != null) {
                    rescWorldWindow.getModel().showFeatureInfo(clickPos, true);
                    mouseEvent.consume();
                }
            }
        }
    }

    @Override
    public void mousePressed(MouseEvent mouseEvent) {
        super.mousePressed(mouseEvent);
    }

    @Override
    public void mouseReleased(MouseEvent mouseEvent) {
        super.mouseReleased(mouseEvent);
    }

    @Override
    public void mouseDragged(MouseEvent mouseEvent) {
        /*
         * We want to rotate the view with 1 or 2 fingers (or none - i.e. mouse)
         * but no more.
         */
        if (fingersOn < 3) {
            super.mouseDragged(mouseEvent);
        }
    }

    @Override
    public void setEventSource(WorldWindow worldWindow) {
        if (worldWindow instanceof RescWorldWindow) {
            this.rescWorldWindow = (RescWorldWindow) worldWindow;
            this.view = rescWorldWindow.getView();
            super.setEventSource(worldWindow);
        } else {
            throw new IllegalArgumentException("RescInputHandler only supports RescWorldWindows");
        }
    }

    @Override
    public WorldWindow getEventSource() {
        return rescWorldWindow;
    }

    @Override
    public void setSmoothViewChanges(boolean smoothViewChanges) {
        /*
         * Do not allow smooth view changes. This looks nice but interferes with
         * the touch interface implementation.
         * 
         * To get more technical, it interferes with zooming using the right
         * mouse button. Mouse-wheel zooming had to be reimplemented for JavaFX
         * because the touch-scrolling generates AWT mouse wheel events. As a
         * result, we bypass the ViewInputHandler stuff and catch JavaFX scroll
         * events and use them to modify the zoom level directly.
         * 
         * When the ViewInputHandler is generating smooth (i.e. inertial)
         * zooming (triggered here from a right-button + up/down mouse
         * movement), it keeps track of the zoom level itself and then calls the
         * View.setZoom() method. However, we don't keep track of this for our
         * zooming, instead delegating to the LinkedView.changeZoom() method
         * (which in turn does a getZoom and a setZoom).
         * 
         * For one reason or another this means that if you trigger a zoom right
         * the right-click method, inertial zooming will means that the view
         * keeps zooming (long) after the mouse button has been released. Then
         * mouse wheel zooming doesn't work, instead getting reset to the zoom
         * level set me the ViewInputHandler.
         * 
         * tldr: Smooth view changes don't work in conjunction with the input
         * handling we need to do to interpret touch events.
         */
    }
}

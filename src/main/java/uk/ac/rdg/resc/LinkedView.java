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

import gov.nasa.worldwind.View;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.view.orbit.BasicOrbitView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A {@link View} which is linked to another view. It delegates all behaviour to
 * the other view, apart from the setHeading and setPitch methods which it
 * adjusts to ensure that this view displays the opposite hemisphere of the
 * earth to the one it is linked to.
 * 
 * @author Guy Griffiths
 */
public class LinkedView extends BasicOrbitView {
    /**
     * The views which this view synchronises with
     */
    private Set<LinkedView> linkedViews = new HashSet<>();

    /**
     * The three states a linked view can have.
     * 
     * UNLINKED means that the view is independent of other views it is linked
     * with
     * 
     * LINKED and ANTILINKED views always move in sync with one another. If two
     * views are both LINKED, they will show the same view. If one is LINKED and
     * one is ANTILINKED they will show opposite hemispheres
     */
    public enum LinkedViewState {
        LINKED, ANTILINKED, UNLINKED
    }

    /** The {@link LinkedViewState} which this view is currently in */
    private LinkedViewState linkState = LinkedViewState.LINKED;

    public void setLinkState(LinkedViewState linkState) {
        this.linkState = linkState;
        if (linkState == LinkedViewState.LINKED || linkState == LinkedViewState.ANTILINKED) {
            /*
             * If we're switching back to a linked view, sync with the other
             * linked views
             */
            Iterator<LinkedView> iterator = linkedViews.iterator();
            while (iterator.hasNext()) {
                LinkedView linkedView = iterator.next();
                if (linkedView.linkState == linkState) {
                    setLinkedCenterPosition(linkedView.getCenterPosition());
                    setLinkedZoom(linkedView.getZoom());
                    setLinkedHeading(linkedView.getHeading());
                    setLinkedPitch(linkedView.getPitch());
                    break;
                }
            }
        }
        firePropertyChange(AVKey.VIEW, null, this);
    }

    /**
     * @return The {@link LinkedViewState} of this view
     */
    public LinkedViewState getLinkedViewState() {
        return linkState;
    }

    /**
     * Links views together.
     * 
     * @param view
     *            The {@link LinkedView} to link with this one
     */
    public void addLinkedView(LinkedView view) {
        /*
         * Don't allow views to link with themselves
         */
        if (this != view) {
            /*
             * Start the view in a linked state, and set it's
             * position/heading/pitch/zoom
             */
            linkedViews.add(view);
            view.linkedViews.add(this);
            view.linkState = LinkedViewState.LINKED;
            view.setCenterPosition(this.center);
            view.setHeading(this.heading);
            view.setPitch(this.pitch);
            view.setZoom(this.zoom);
        }
    }

    @Override
    public void setCenterPosition(Position center) {
        if (linkState == null) {
            /*
             * This is necessary since setCenterPosition gets called during
             * BasicOrbitView instantiation (i.e. before linkState has been
             * set...)
             */
            linkState = LinkedViewState.UNLINKED;
        }
        /*
         * Set the position as per BasicOrbitView
         */
        super.setCenterPosition(center);
        /*
         * Now set the centre position on all linked views
         */
        switch (linkState) {
        case LINKED:
            for (LinkedView view : linkedViews) {
                view.setLinkedCenterPosition(center);
            }
            break;
        case ANTILINKED:
            center = Position.fromDegrees(-center.latitude.degrees,
                    180.0 + center.longitude.degrees, center.elevation);
            for (LinkedView view : linkedViews) {
                view.setLinkedCenterPosition(center);
            }
            break;
        case UNLINKED:
            break;
        }
    }

    /**
     * Called by other {@link LinkedView}s when the centre of their view changes
     * 
     * @param center
     *            The {@link Position} of the view centre
     */
    private void setLinkedCenterPosition(Position center) {
        switch (linkState) {
        case ANTILINKED:
            /*
             * If another view is linked, we want to switch the centre position
             * to the opposite hemisphere.
             * 
             * If the other view which called this method is also antilinked,
             * the centre position will be switched twice so this will match it
             */
            center = Position.fromDegrees(-center.latitude.degrees,
                    180.0 + center.longitude.degrees, center.elevation);
            /*
             * Note the use of switch here - no break is required
             */
        case LINKED:
            /*
             * Now set the centre position and redraw
             */
            super.setCenterPosition(center);
            firePropertyChange(AVKey.VIEW, null, this);
            break;
        case UNLINKED:
            break;
        }
    }

    @Override
    public void setHeading(Angle heading) {
        super.setHeading(heading);
        if (linkState != LinkedViewState.UNLINKED) {
            /*
             * Heading is the same for linked/antilinked views.
             */
            for (LinkedView view : linkedViews) {
                view.setLinkedHeading(heading);
            }
        }
    }

    public void changeHeading(Angle heading) {
        setHeading(getHeading().add(heading));
    }

    /**
     * Called by other {@link LinkedView}s when their heading changes
     * 
     * @param heading
     *            The new heading
     */
    private void setLinkedHeading(Angle heading) {
        if (linkState != LinkedViewState.UNLINKED) {
            /*
             * Heading is the same for linked/antilinked views
             */
            super.setHeading(heading);
            firePropertyChange(AVKey.VIEW, null, this);
        }
    }

    @Override
    public void setPitch(Angle pitch) {
        super.setPitch(pitch);
        if (linkState != LinkedViewState.UNLINKED) {
            for (LinkedView view : linkedViews) {
                view.setLinkedPitch(pitch);
            }
        }
    }

    public void changePitch(Angle pitch) {
        setPitch(pitch.add(getPitch()));
    }

    /**
     * Called by other {@link LinkedView}s when their pitch changes
     * 
     * @param pitch
     *            The new pitch
     */
    private void setLinkedPitch(Angle pitch) {
        if (linkState != LinkedViewState.UNLINKED) {
            /*
             * Pitch is the same for linked/antilinked views
             */
            super.setPitch(pitch);
            firePropertyChange(AVKey.VIEW, null, this);
        }
    }

    @Override
    public void setZoom(double zoom) {
        super.setZoom(zoom);
        if (linkState != LinkedViewState.UNLINKED) {
            /*
             * Zoom is the same for linked/antilinked views
             */
            for (LinkedView view : linkedViews) {
                view.setLinkedZoom(zoom);
            }
        }
    }

    public void changeZoom(double zoomFactor) {
        setZoom(getZoom() * zoomFactor);
    }

    /**
     * Called by other {@link LinkedView}s when their zoom changes
     * 
     * @param zoom
     *            The new zoom
     */
    private void setLinkedZoom(double zoom) {
        if (linkState != LinkedViewState.UNLINKED) {
            super.setZoom(zoom);
            firePropertyChange(AVKey.VIEW, null, this);
        }
    }
}

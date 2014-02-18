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
 * @author Guy
 */
public class LinkedView extends BasicOrbitView {
    private Set<LinkedView> linkedViews = new HashSet<>();

    public enum LinkedViewState {
        LINKED, ANTILINKED, UNLINKED
    }

    private LinkedViewState linkState = LinkedViewState.UNLINKED;

    public void setLinkState(LinkedViewState linkState) {
        this.linkState = linkState;
        boolean done = false;
        if (linkState == LinkedViewState.LINKED || linkState == LinkedViewState.ANTILINKED) {
            /*
             * If we're switching back to a linked view, sync with the other
             * linked views
             */
            Iterator<LinkedView> iterator = linkedViews.iterator();
            while (iterator.hasNext()) {
                LinkedView linkedView = iterator.next();
                if (linkedView.linkState == LinkedViewState.LINKED) {
                    setLinkedCenterPosition(linkedView.getCenterPosition());
                    setLinkedZoom(linkedView.getZoom());
                    setLinkedHeading(linkedView.getHeading());
                    setLinkedPitch(linkedView.getPitch());
                    done = true;
                    break;
                }
            }
            if (!done) {
                /*
                 * We didn't find any other linked views, so we need to look for
                 * antilinked views instead
                 */
            }
        }
        firePropertyChange(AVKey.VIEW, null, this);
    }

    /**
     * Links views together.
     * 
     * @param view
     */
    public void addLinkedView(LinkedView view) {
        if (this != view) {
            linkedViews.add(view);
            view.linkedViews.add(this);
            linkState = LinkedViewState.LINKED;
            view.linkState = LinkedViewState.LINKED;
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
        super.setCenterPosition(center);
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

    private void setLinkedCenterPosition(Position center) {
        switch (linkState) {
        case ANTILINKED:
            center = Position.fromDegrees(-center.latitude.degrees,
                    180.0 + center.longitude.degrees, center.elevation);
        case LINKED:
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
        switch (linkState) {
        case LINKED:
        case ANTILINKED:
            /*
             * Heading is the same for linked/antilinked views.
             */
            for (LinkedView view : linkedViews) {
                view.setLinkedHeading(heading);
            }
            break;
        case UNLINKED:
            /*
             * For unlinked, we don't set the other view's headings
             */
            break;
        }
    }

    private void setLinkedHeading(Angle heading) {
        switch (linkState) {
        case LINKED:
        case ANTILINKED:
            super.setHeading(heading);
            firePropertyChange(AVKey.VIEW, null, this);
            break;
        case UNLINKED:
            /*
             * For unlinked, we don't allow other views to set our heading
             */
            break;
        }
    }

    @Override
    public void setPitch(Angle pitch) {
        super.setPitch(pitch);
        switch (linkState) {
        case ANTILINKED:
        case LINKED:
            for (LinkedView view : linkedViews) {
                view.setLinkedPitch(pitch);
            }
            break;
        case UNLINKED:
            break;
        }
    }

    private void setLinkedPitch(Angle pitch) {
        if (linkState != LinkedViewState.UNLINKED) {
            super.setPitch(pitch);
            firePropertyChange(AVKey.VIEW, null, this);
        }
    }

    @Override
    public void setZoom(double zoom) {
        super.setZoom(zoom);
        switch (linkState) {
        case LINKED:
        case ANTILINKED:
            for (LinkedView view : linkedViews) {
                view.setLinkedZoom(zoom);
            }
            break;
        case UNLINKED:
            break;
        }
    }

    private void setLinkedZoom(double zoom) {
        switch (linkState) {
        case LINKED:
        case ANTILINKED:
            super.setZoom(zoom);
            firePropertyChange(AVKey.VIEW, null, this);
            break;
        case UNLINKED:
            break;
        }
    }
}

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

import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import uk.ac.rdg.resc.LinkedView.LinkedViewState;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Position;

@SuppressWarnings("serial")
public class RescWorldWindow extends WorldWindowGLCanvas {
    
    private final LinkedView linkedView;
    
    public RescWorldWindow(LinkedView linkedView) {
        super();
        
        this.linkedView = linkedView;
        setView(this.linkedView);
        
        addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                Position currentPosition = getCurrentPosition();
                if(currentPosition != null) {
                    getModel().showFeatureInfo(currentPosition);
                }
            }
            
            @Override
            public void mouseReleased(MouseEvent e) {}
            
            @Override
            public void mousePressed(MouseEvent e) {}
            
            @Override
            public void mouseExited(MouseEvent e) {}
            
            @Override
            public void mouseEntered(MouseEvent e) {}
        });
    }
    
    public LinkedView getLinkedView() {
        return linkedView;
    }
    
    public void setLinkedView() {
        linkedView.setLinkState(LinkedViewState.LINKED);
    }
    
    public void setUnlinkedView() {
        linkedView.setLinkState(LinkedViewState.UNLINKED);
    }
    
    public void setAntilinkedView() {
        linkedView.setLinkState(LinkedViewState.ANTILINKED);
    }
    
    @Override
    public RescModel getModel() {
        return (RescModel) super.getModel();
    }

    public void setModel(RescModel model) {
        if(!(model instanceof RescModel)) {
            throw new IllegalArgumentException("Only RescModels can be used in RescWorldWindows");
        }
        super.setModel(model);
    }
}

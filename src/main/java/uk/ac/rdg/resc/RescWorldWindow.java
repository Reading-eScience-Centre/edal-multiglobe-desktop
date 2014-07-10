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

import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.awt.WorldWindowGLJPanel;
import javafx.embed.swing.SwingNode;
import uk.ac.rdg.resc.input.RescInputHandler;

/**
 * A {@link WorldWindow} which guarantees that the {@link Model} it contains is
 * an instance of a {@link RescModel}, and the {@link View} is an instance of
 * {@link LinkedView} (the config option to control the view type is also
 * overridden in the main {@link VideoWall} class)
 * 
 * @author Guy Griffiths
 */
@SuppressWarnings("serial")
public class RescWorldWindow extends WorldWindowGLJPanel {
    /**
     * Instantiates a new {@link RescWorldWindow}.
     * 
     * @param container
     *            The {@link SwingNode} which will contain this
     *            {@link RescWorldWindow}. The {@link RescInputHandler}
     *            associated with this {@link RescWorldWindow} will register
     *            callbacks to this {@link SwingNode} to deal with touch events.
     */
    public RescWorldWindow(SwingNode container) {
        super();

        /*
         * We must set this prior to adding the mouse listener, otherwise there
         * are problems with the FeatureInfoBalloon behaviour (a new
         * mouseClicked event gets created in the previous position when we
         * click close - so a feature balloon can never be removed...)
         */

        RescInputHandler rescInputHandler = new RescInputHandler(container, this);
        setInputHandler(rescInputHandler);

        /*
         * We need to add a listener for clicks on the globe to display
         * information and graphs when layers are clicked
         */
//        addMouseListener(new MouseListener() {
//            @Override
//            public void mouseClicked(MouseEvent e) {
//                if (!e.isConsumed()) {
//                    Position currentPosition = getCurrentPosition();
//                    if (currentPosition != null) {
//                        getModel().showFeatureInfo(currentPosition);
//                        e.consume();
//                    }
//                }
//            }
//
//            @Override
//            public void mouseReleased(MouseEvent e) {
//            }
//
//            @Override
//            public void mousePressed(MouseEvent e) {
//            }
//
//            @Override
//            public void mouseExited(MouseEvent e) {
//            }
//
//            @Override
//            public void mouseEntered(MouseEvent e) {
//            }
//        });
    }

    @Override
    public LinkedView getView() {
        return (LinkedView) super.getView();
    }

    @Override
    public void setView(View view) {
        if (!(view instanceof LinkedView)) {
            throw new IllegalArgumentException(
                    "To use a RescWorldWindow, the View must be an instance of LinkedView");
        }
        super.setView(view);
    }

    @Override
    public RescModel getModel() {
        return (RescModel) super.getModel();
    }

    @Override
    public void setModel(Model model) {
        if (!(model instanceof RescModel)) {
            throw new IllegalArgumentException(
                    "To use a RescWorldWindow, the Model must be an instance of RescModel");
        }
        super.setModel(model);
    }
}

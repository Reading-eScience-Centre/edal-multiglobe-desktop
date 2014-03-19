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
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.render.AnnotationFlowLayout;
import gov.nasa.worldwind.render.ScreenAnnotation;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;

/**
 * A widget for selecting WMS layers from a hierarchical tree. This is a big
 * screen annotation, designed to fill an entire globe panel.
 * 
 * @author Guy
 */
public class LayerSelectorWidget extends ScreenAnnotation implements SelectListener {

    private RescWorldWindow wwd;
    
    private LayerMenuItem rootItem;
    private LayerMenuItem currentItem;

    /*
     * This is the panel
     */
    private ScreenAnnotation menuItemPanel;

    private ScreenAnnotation backButton;

    private ScreenAnnotation titleAnnotation;
    //    private List<ScreenAnnotation> pickableItems;
    private Map<ScreenAnnotation, Runnable> pickableItems;


    public LayerSelectorWidget(RescWorldWindow wwd) {
        super("", new Point());
        
        this.wwd = wwd;

        titleAnnotation = new ScreenAnnotation("Datasets", new Point());
        //        pickableItems = new ArrayList<>();
        pickableItems = new HashMap<>();

        this.setLayout(new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER, 4, 0));

        menuItemPanel = new ScreenAnnotation("", new Point());
        menuItemPanel.setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, 4));

        menuItemPanel.addChild(titleAnnotation);

        backButton = new ScreenAnnotation("Back", new Point());
        this.addChild(backButton);
        this.addChild(menuItemPanel);
    }

    public void populateLayerSelector(LayerMenuItem rootItem) {
        this.rootItem = rootItem;
    }

    /**
     * Sets the layer selector to display the datasets - i.e. the top level
     * items in the menu
     */
    public void displayDatasets() {
//        showItems("Datasets", rootItem.getChildren());
        showItems(rootItem);
    }

    private void showItems(LayerMenuItem parentItem) {
        currentItem = parentItem;
        /*
         * We could keep track of all the non-title elements and remove them
         * here, but it's simpler to remove everything and then always re-add
         * the title
         * 
         * TODO maybe not, since we need the list anyway...
         */
        menuItemPanel.removeAllChildren();
        pickableItems.clear();

        titleAnnotation.setText(parentItem.getTitle());
        menuItemPanel.addChild(titleAnnotation);

        for (final LayerMenuItem item : parentItem.getChildren()) {
            ScreenAnnotation menuItem = null;
            if (item.isPlottable()) {
                if (item.isLeaf()) {
                    menuItem = new ScreenAnnotation(item.getTitle(), new Point());
                    pickableItems.put(menuItem, new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("Will plot layer: " + item.getTitle());
                            plot(item.getId());
                        }
                    });
                } else {
                    menuItem = new ScreenAnnotation("", new Point());
                    menuItem.setLayout(new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER, 4, 0));
                    ScreenAnnotation plot = new ScreenAnnotation(item.getTitle(), new Point());
                    ScreenAnnotation expand = new ScreenAnnotation("+", new Point());
                    pickableItems.put(plot, new Runnable() {
                        @Override
                        public void run() {
                            System.out.println("Will plot layer: " + item.getTitle());
                            plot(item.getId());
                        }
                    });
                    pickableItems.put(expand, new Runnable() {
                        @Override
                        public void run() {
                            showItems(item);
                        }
                    });
                    menuItem.addChild(plot);
                    menuItem.addChild(expand);
                }
            } else if (!item.isLeaf()) {
                menuItem = new ScreenAnnotation(item.getTitle(), new Point());
                pickableItems.put(menuItem, new Runnable() {
                    @Override
                    public void run() {
                        showItems(item);
                    }
                });
            } else {
                /*
                 * A non-plottable, non-expandable element. Should never get
                 * here.
                 * 
                 * TODO grey it out or similar in case we get this situation
                 * (perhaps it will happen when layers are still loading??)
                 */
                menuItem = new ScreenAnnotation(item.getTitle(), new Point());
            }
            menuItemPanel.addChild(menuItem);
        }
    }
    
    private void plot(String layer) {
        if(wwd != null && wwd.getModel() != null) {
            wwd.getModel().setDataLayer(layer);
        }
        getAttributes().setVisible(false);
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            Object pickedObj = event.getTopObject();
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                if (pickedObj == backButton) {
                    if(currentItem != null && currentItem.getParent() != null) {
                        showItems(currentItem.getParent());
                    }
                } else {
                    Runnable run = null;
                    for (ScreenAnnotation pickable : pickableItems.keySet()) {
                        if (pickedObj == pickable) {
                            run = pickableItems.get(pickable);
                            break;
                        }
                    }
                    if (run != null) {
                        run.run();
                    }
                }
            }
        }
    }
}

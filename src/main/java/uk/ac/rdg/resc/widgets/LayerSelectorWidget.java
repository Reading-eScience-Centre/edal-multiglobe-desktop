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
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.render.Annotation;
import gov.nasa.worldwind.render.AnnotationFlowLayout;
import gov.nasa.worldwind.render.AnnotationNullLayout;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import uk.ac.rdg.resc.RescWorldWindow;
import uk.ac.rdg.resc.edal.exceptions.VariableNotFoundException;
import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;
import uk.ac.rdg.resc.logging.RescLogging;

/**
 * A widget for selecting WMS layers from a hierarchical tree. This is a big
 * screen annotation, designed to fill an entire globe panel.
 * 
 * This selector displays a list of available datasets/layers/sublayers.
 * Clicking on any of them will either display sublayers or will select the data
 * layer. This is a simple interface but well-suited to touchscreens
 * 
 * @author Guy Griffiths
 */
public class LayerSelectorWidget extends ScreenAnnotation implements SelectListener {
    /*
     * The horizontal gap to use between items in the menu
     */
    private static final int HGAP = 4;

    /*
     * Images for buttons in the layer selector
     */
    private static final String EXPAND_IMAGE = "images/expand_button.png";
    private static final String BACK_IMAGE = "images/back_button.png";
    private static final String CLOSE_IMAGE = "images/close_button.png";

    /** The {@link RescWorldWindow} on which to set the selected layer */
    private RescWorldWindow wwd;

    /** The root of the menu tree */
    private LayerMenuItem rootItem;
    /** The root of the currently displayed menu item */
    private LayerMenuItem currentItem;

    /** The panel containing all menu items */
    private ScreenAnnotation menuItemPanel;
    /** The button to go up a level/close the window */
    private ScreenAnnotation backButton;
    /** The title of the current level */
    private ScreenAnnotation titleAnnotation;
    /**
     * Items which are selectable, mapped to the action to perform when they are
     * selected
     */
    private Map<ScreenAnnotation, Runnable> pickableItems;

    /**
     * Create a new {@link LayerSelectorWidget}
     * 
     * @param wwd
     *            The {@link RescWorldWindow} to select data layers for
     */
    public LayerSelectorWidget(RescWorldWindow wwd) {
        super("", new Point());

        this.wwd = wwd;

        /*
         * Set up initial components
         */
        titleAnnotation = new ScreenAnnotation("Datasets", new Point());
        WidgetUtils.setDefaultButtonAttributes(titleAnnotation.getAttributes());
        titleAnnotation.getAttributes().setFont(new Font("SansSerif", Font.BOLD, 16));

        setLayout(new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER, HGAP, 0));

        menuItemPanel = new ScreenAnnotation("", new Point());
        AnnotationFlowLayout layout = new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.RIGHT, 0, 4);
        menuItemPanel.setLayout(layout);
        WidgetUtils.setupContainerAttributes(menuItemPanel.getAttributes());

        menuItemPanel.addChild(titleAnnotation);

        backButton = new ImageAnnotation(CLOSE_IMAGE);
        backButton.getAttributes().setBackgroundColor(Color.black);
        backButton.getAttributes().setInsets(null);
        backButton.setPickEnabled(true);

        addChild(backButton);
        addChild(menuItemPanel);

        getAttributes().setBackgroundColor(new Color(0, 0, 0, 64));
        getAttributes().setCornerRadius(10);
        getAttributes().setOpacity(0.8);

        pickableItems = new HashMap<>();
    }

    /**
     * Sets the menu to display in this {@link LayerSelectorWidget}
     * 
     * @param rootItem
     *            The root item of the menu tree
     */
    public void populateLayerSelector(LayerMenuItem rootItem) {
        this.rootItem = rootItem;
    }

    /**
     * Sets the layer selector to display the datasets - i.e. the top level
     * items in the menu
     */
    public void displayDatasets() {
        showItems(rootItem);
    }

    /**
     * Sets the visible items which may be selected
     * 
     * @param parentItem
     *            The parent node of all items to display
     */
    private void showItems(LayerMenuItem parentItem) {
        /*
         * If we are at the top level, we want to allow the user to close the
         * layer selector, otherwise they can go up a level
         */
        if (parentItem == rootItem) {
            backButton.getAttributes().setImageSource(CLOSE_IMAGE);
        } else {
            backButton.getAttributes().setImageSource(BACK_IMAGE);
        }

        currentItem = parentItem;
        /*
         * We could keep track of all the non-title elements and remove them
         * here, but it's simple to remove everything and then always re-add the
         * title
         */
        menuItemPanel.removeAllChildren();
        pickableItems.clear();

        titleAnnotation.setText(parentItem.getTitle());
        menuItemPanel.addChild(titleAnnotation);

        if (!parentItem.isLeaf()) {
            for (final LayerMenuItem item : parentItem.getChildren()) {
                /*
                 * TODO add preview images to each button? May make them too
                 * big, and it's an optional extra rather than a must-have
                 */
                ScreenAnnotation menuItem = null;
                if (item.isPlottable()) {
                    /*
                     * We have a plottable item, so we want to set the data
                     * layer when we click it.
                     */
                    menuItem = new ScreenAnnotation(item.getTitle(), new Point());
                    WidgetUtils.setDefaultButtonAttributes(menuItem.getAttributes());
                    /*
                     * Add an action to show the data layer in the
                     * RescWorldWindow if clicked
                     */
                    pickableItems.put(menuItem, new Runnable() {
                        @Override
                        public void run() {
                            showLayer(item.getId());
                        }
                    });

                    if (!item.isLeaf()) {
                        /*
                         * This item is also expandable, so we add a clickable
                         * expand link
                         */
                        menuItem.setLayout(new AnnotationNullLayout());
                        ScreenAnnotation expand = new ImageAnnotation(EXPAND_IMAGE);
                        expand.setPickEnabled(true);
                        /*
                         * Add an action to expand display child items if
                         * clicked
                         */
                        pickableItems.put(expand, new Runnable() {
                            @Override
                            public void run() {
                                showItems(item);
                            }
                        });
                        menuItem.addChild(expand);
                    }
                } else if (!item.isLeaf()) {
                    /*
                     * This item is just a container - it has children but it
                     * doesn't represent a plottable layer
                     */
                    menuItem = new ScreenAnnotation(item.getTitle(), new Point());
                    WidgetUtils.setDefaultButtonAttributes(menuItem.getAttributes());
                    /*
                     * Add an action to expand display child items if clicked
                     */
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
                menuItem.getAttributes().setBackgroundColor(new Color(0, 0, 0, 150));
                menuItemPanel.addChild(menuItem);
            }
        }
    }

    /**
     * Displays the data layer corresponding to the given layer ID
     * 
     * @param layer
     *            The layer ID to show
     */
    private void showLayer(String layer) {
        if (wwd != null && wwd.getModel() != null) {
            try {
                wwd.getModel().setDataLayer(layer);
            } catch (VariableNotFoundException e) {
                /*
                 * The layer has not been found. We can't do anything about
                 * this, but it shouldn't happen in normal operation (where did
                 * the user get the layer ID from?)
                 */
                String message = RescLogging.getMessage("resc.NoLayer", layer);
                Logging.logger().warning(message);
                e.printStackTrace();
                return;
            }
        }
        /*
         * Hide this layer selector since a layer has been selected
         */
        getAttributes().setVisible(false);
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            Object pickedObj = event.getTopObject();
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                /*
                 * If the back button was clicked, go up a level, otherwise
                 * check if we have clicked a pickable item
                 */
                if (pickedObj == backButton) {
                    if (currentItem != null && currentItem.getParent() != null) {
                        showItems(currentItem.getParent());
                    } else {
                        getAttributes().setVisible(false);
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
            } else if (event.getEventAction().equals(SelectEvent.ROLLOVER)) {
                /*
                 * TODO centralise this?    
                 */
                if (pickableItems.containsKey(pickedObj) || pickedObj == backButton) {
                    ((Component) wwd).setCursor(new Cursor(Cursor.HAND_CURSOR));
                    event.consume();
                } else {
                    ((Component) wwd).setCursor(Cursor.getDefaultCursor());
                }
            }
        }
    }

    @Override
    public void render(DrawContext dc) {
        super.render(dc);
        Dimension size = getAttributes().getSize();

        int backWidth = backButton.getPreferredSize(dc).width;

        for (Annotation child : menuItemPanel.getChildren()) {
            /*
             * Set the size of menu items
             */
            child.getAttributes().setSize(new Dimension(size.width - backWidth * 2, 0));
            if (child.getChildren().size() > 0) {
                /*
                 * This means that we have an expand button.
                 */
                Annotation expand = child.getChildren().get(0);

                Dimension expandSize = expand.getPreferredSize(dc);

                expand.getAttributes().setDrawOffset(
                        new Point(size.width - backWidth * 2 - expandSize.width - 2 * HGAP, 0));
            }
        }

    }

}

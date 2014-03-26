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
import gov.nasa.worldwind.render.AnnotationAttributes;
import gov.nasa.worldwind.render.AnnotationFlowLayout;
import gov.nasa.worldwind.render.AnnotationNullLayout;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.util.HashMap;
import java.util.Map;

import uk.ac.rdg.resc.RescWorldWindow;
import uk.ac.rdg.resc.edal.wms.exceptions.WmsLayerNotFoundException;
import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;

/**
 * A widget for selecting WMS layers from a hierarchical tree. This is a big
 * screen annotation, designed to fill an entire globe panel.
 * 
 * @author Guy
 */
public class LayerSelectorWidget extends ScreenAnnotation implements SelectListener {

    /*
     * The horizontal gap to use in annotation flow layouts
     */
    private static final int HGAP = 4;

    private static final String EXPAND_IMAGE = "images/expand_button.png";
    private static final String BACK_IMAGE = "images/back_button.png";
    private static final String CLOSE_IMAGE = "images/close_button.png";

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
        setDefaultButtonAttributes(titleAnnotation.getAttributes());
        titleAnnotation.getAttributes().setFont(new Font("SansSerif", Font.BOLD, 16));

        pickableItems = new HashMap<>();

        this.setLayout(new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER, HGAP, 0));

        menuItemPanel = new ScreenAnnotation("", new Point());
        AnnotationFlowLayout layout = new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.RIGHT, 0, 4);
        menuItemPanel.setLayout(layout);
        WidgetUtils.setupContainerAttributes(menuItemPanel.getAttributes());

        menuItemPanel.addChild(titleAnnotation);

        backButton = new ImageAnnotation(CLOSE_IMAGE);
        backButton.getAttributes().setBackgroundColor(Color.black);
        backButton.getAttributes().setInsets(null);
        backButton.setPickEnabled(true);

        this.addChild(backButton);
        this.addChild(menuItemPanel);

        getAttributes().setBackgroundColor(new Color(0, 0, 0, 64));
        getAttributes().setCornerRadius(10);
        getAttributes().setOpacity(0.8);
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
        if (parentItem == rootItem) {
            backButton.getAttributes().setImageSource(CLOSE_IMAGE);
        } else {
            backButton.getAttributes().setImageSource(BACK_IMAGE);
        }

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
            /*
             * TODO add preview images to each button? May make them too big,
             * and it's an optional extra rather than a must-have
             */
            ScreenAnnotation menuItem = null;
            if (item.isPlottable()) {
                /*
                 * We have a plottable item, so we want to set the data layer
                 * when we click it.
                 */
                menuItem = new ScreenAnnotation(item.getTitle(), new Point());
                setDefaultButtonAttributes(menuItem.getAttributes());
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
                    pickableItems.put(expand, new Runnable() {
                        @Override
                        public void run() {
                            showItems(item);
                        }
                    });
                    menuItem.addChild(expand);
                }
            } else if (!item.isLeaf()) {
                menuItem = new ScreenAnnotation(item.getTitle(), new Point());
                setDefaultButtonAttributes(menuItem.getAttributes());
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

    private void showLayer(String layer) {
        if (wwd != null && wwd.getModel() != null) {
            try {
                wwd.getModel().setDataLayer(layer);
            } catch (WmsLayerNotFoundException e) {
                /*
                 * TODO Log this. The layer has not been found. We can't do
                 * anything about this, but it shouldn't happen in normal
                 * operation (where did the user get the layer ID from?)
                 */
                e.printStackTrace();
                return;
            }
        }
        getAttributes().setVisible(false);
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            Object pickedObj = event.getTopObject();
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
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

    private static void setDefaultButtonAttributes(AnnotationAttributes attrs) {
        attrs.setAdjustWidthToText(AVKey.SIZE_FIXED);
        attrs.setTextAlign(AVKey.CENTER);
        attrs.setBackgroundColor(Color.black);
        attrs.setTextColor(Color.white);
        attrs.setCornerRadius(10);
        attrs.setBorderWidth(1);
        attrs.setInsets(new Insets(10, 0, 10, 0));
    }
}

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

import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

import uk.ac.rdg.resc.LinkedView.LinkedViewState;
import uk.ac.rdg.resc.widgets.LayerSelectorWidget;

/**
 * Currently just displays a cog and then a layer selector. Functional, but
 * needs to be made nicer (big icons for touchscreen - a Friday afternoon job)
 * 
 * @author Guy
 */
public class EdalConfigLayer extends RenderableLayer implements SelectListener {
//    private final static String CONFIG_BUTTON = "images/config_button.png";
    private final static String LAYERS_BUTTON = "images/layers_button.png";
    private final static String LINK_BUTTON = "images/link_button.png";
    private final static String UNLINK_BUTTON = "images/unlink_button.png";
    private final static String ANTILINK_BUTTON = "images/antilink_button.png";
    private final static String GLOBE_BUTTON = "images/flat_globe.png";
    private final static String MAP_BUTTON = "images/flat_map.png";

    protected RescWorldWindow wwd;
    protected VideoWallCatalogue catalogue;

    private ImageAnnotation layersButton;

    private ImageAnnotation linkStateButton;
    private ImageAnnotation linkButton;
    private ImageAnnotation antilinkButton;
    private ImageAnnotation unlinkButton;

    private ImageAnnotation flatButton;

    private LayerSelectorWidget layerSelector;

    private int borderWidth = 20;

    public EdalConfigLayer(RescWorldWindow wwd, VideoWallCatalogue catalogue) {
        if (wwd == null) {
            String msg = Logging.getMessage("nullValue.WorldWindow");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }
        if (catalogue == null) {
            String msg = Logging.getMessage("nullValue.VideoWallCatalogue");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        this.wwd = wwd;
        this.catalogue = catalogue;
        this.initialize();
    }

    protected void initialize() {
        layersButton = new ImageAnnotation(LAYERS_BUTTON);
        layersButton.setScreenPoint(new Point(0, 0));
        layersButton.setPickEnabled(true);
        addRenderable(layersButton);

        linkStateButton = new ImageAnnotation(LINK_BUTTON);
        linkStateButton.setPickEnabled(true);
        addRenderable(linkStateButton);

        linkButton = new ImageAnnotation(LINK_BUTTON);
        linkButton.setPickEnabled(true);

        antilinkButton = new ImageAnnotation(ANTILINK_BUTTON);
        antilinkButton.setPickEnabled(true);

        unlinkButton = new ImageAnnotation(UNLINK_BUTTON);
        unlinkButton.setPickEnabled(true);

        flatButton = new ImageAnnotation(GLOBE_BUTTON);
        flatButton.setPickEnabled(true);
        addRenderable(flatButton);

        this.layerSelector = new LayerSelectorWidget(wwd);
        layerSelector.getAttributes().setVisible(false);
        addRenderable(layerSelector);
        wwd.addSelectListener(layerSelector);

        // Listen to world window for select event
        this.wwd.addSelectListener(this);
    }

    private void displayLayerSelector() {
        layerSelector.populateLayerSelector(catalogue.getEdalLayers());
        layerSelector.displayDatasets();
        layerSelector.getAttributes().setVisible(true);
    }

    /**
     * <code>SelectListener</code> implementation.
     * 
     * @param event
     *            the current <code>SelectEvent</code>
     */
    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            if (event.getTopObject() == layersButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    displayLayerSelector();
                }
            } else if (event.getTopObject() == linkStateButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    removeRenderable(linkStateButton);
                    addRenderable(linkButton);
                    addRenderable(antilinkButton);
                    addRenderable(unlinkButton);
                }
            } else if (event.getTopObject() == linkButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    wwd.getLinkedView().setLinkState(LinkedViewState.LINKED);
                    linkStateButton.setImageSource(LINK_BUTTON);
                    removeRenderable(linkButton);
                    removeRenderable(antilinkButton);
                    removeRenderable(unlinkButton);
                    addRenderable(linkStateButton);
                }
            } else if (event.getTopObject() == antilinkButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    wwd.getLinkedView().setLinkState(LinkedViewState.ANTILINKED);
                    linkStateButton.setImageSource(ANTILINK_BUTTON);
                    removeRenderable(linkButton);
                    removeRenderable(antilinkButton);
                    removeRenderable(unlinkButton);
                    addRenderable(linkStateButton);
                }
            } else if (event.getTopObject() == unlinkButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    wwd.getLinkedView().setLinkState(LinkedViewState.UNLINKED);
                    linkStateButton.setImageSource(UNLINK_BUTTON);
                    removeRenderable(linkButton);
                    removeRenderable(antilinkButton);
                    removeRenderable(unlinkButton);
                    addRenderable(linkStateButton);
                }
            } else if (event.getTopObject() == flatButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    RescModel model = wwd.getModel();
                    model.setFlat(!model.isFlat());
                    if (model.isFlat()) {
                        flatButton.setImageSource(MAP_BUTTON);
                    } else {
                        flatButton.setImageSource(GLOBE_BUTTON);
                    }
                }
                //        } else if (event.getEventAction().equals(SelectEvent.ROLLOVER)
                //                && this.layerSelector.getAttributes().isHighlighted()) {
                //            hideLayerSelector();
                //            // de-highlight annotation
                //            this.layerSelector.getAttributes().setHighlighted(false);
                //            ((Component) this.wwd).setCursor(Cursor.getDefaultCursor());
            }
        }
    }

    protected static String encodeHTMLColor(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    public void render(DrawContext dc) {
        Rectangle viewport = dc.getView().getViewport();
        this.layerSelector.getAttributes().setSize(new Dimension(viewport.width - borderWidth, 0));
        Dimension layerSelectorActualSize = layerSelector.getPreferredSize(dc);
        this.layerSelector.setScreenPoint(new Point(viewport.x + viewport.width / 2, viewport.y
                + borderWidth / 2 + (viewport.height - layerSelectorActualSize.height) / 2));
        //        this.layerSelector.setScreenPoint(computeLocation(dc.getView().getViewport()));
        this.layersButton.setScreenPoint(getButtonLocation(dc, 0, 0));
        int x = 70;
        this.linkStateButton.setScreenPoint(getButtonLocation(dc, x, 0));
        this.linkButton.setScreenPoint(getButtonLocation(dc, x, 0));
        this.antilinkButton.setScreenPoint(getButtonLocation(dc, x, -70));
        this.unlinkButton.setScreenPoint(getButtonLocation(dc, x, -140));
        //        x += 70;
        this.flatButton.setScreenPoint(getButtonLocation(dc, 0, -70));
        super.render(dc);
    }

    private Point getButtonLocation(DrawContext dc, int xOffset, int yOffset) {
        int x = this.borderWidth + layersButton.getPreferredSize(dc).width / 2 + xOffset;
        int y = (int) layersButton.getPreferredSize(dc).height / 2 - this.borderWidth - yOffset;
        return new Point(x, y);
    }

    @Override
    public String toString() {
        return Logging.getMessage("layers.LayerManagerLayer.Name");
    }

}

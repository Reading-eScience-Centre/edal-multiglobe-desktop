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
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import uk.ac.rdg.resc.LinkedView.LinkedViewState;
import uk.ac.rdg.resc.edal.graphics.style.util.PlottingStyleParameters;
import uk.ac.rdg.resc.widgets.LayerSelectorWidget;
import uk.ac.rdg.resc.widgets.PaletteSelectorWidget;
import uk.ac.rdg.resc.widgets.PaletteSelectorWidget.PaletteSelectionHandler;

/**
 * This {@link Layer} contains buttons for:
 * 
 * <li>Switching between a flat map and a globe
 * 
 * <li>Displaying the {@link LayerSelectorWidget}
 * 
 * <li>Changing how a view links with other views
 * 
 * <li>Displaying the {@link PaletteSelectorWidget} (and displaying the legend)
 * 
 * @author Guy Griffiths
 */
public class EdalConfigLayer extends RenderableLayer implements SelectListener {
    /*
     * Locations of images for the buttons
     */
    private final static String LAYERS_BUTTON = "images/layers_button.png";
    private final static String LINK_BUTTON = "images/link_button.png";
    private final static String UNLINK_BUTTON = "images/unlink_button.png";
    private final static String ANTILINK_BUTTON = "images/antilink_button.png";
    private final static String GLOBE_BUTTON = "images/flat_globe.png";
    private final static String MAP_BUTTON = "images/flat_map.png";

    /** The width of the border around various objects */
    private static final int BORDER_WIDTH = 20;

    /** The {@link RescWorldWindow} which this layer can make changes to */
    protected RescWorldWindow wwd;
    /**
     * The {@link VideoWallCatalogue} used to populate the
     * {@link LayerSelectorWidget}
     */
    protected VideoWallCatalogue catalogue;

    /** The button to display the {@link LayerSelectorWidget} */
    private ImageAnnotation layersButton;

    /**
     * The button containing the current link state. Clicking this will make all
     * link choice buttons appear
     */
    private ImageAnnotation linkStateButton;
    /** The button to select the LINKED state */
    private ImageAnnotation linkButton;
    /** The button to select the ANTILINKED state */
    private ImageAnnotation antilinkButton;
    /** The button to select the UNLINKED state */
    private ImageAnnotation unlinkButton;

    /**
     * The button to switch between globe/flat map, and to toggle flat map
     * projections
     */
    private ImageAnnotation flatButton;

    /** The {@link LayerSelectorWidget} to allow for layer selection */
    private LayerSelectorWidget layerSelector;

    /*
     * Colourbar/legend related objects
     */

    /** The image representing the colour scale used */
    private ImageAnnotation legendAnnotation = null;

    /**
     * We store the viewport height so that if it changes we can re-generated
     * the legend
     */
    private int lastViewportHeight = -1;

    /**
     * Whether the current legend needs to be re-generated (e.g. when the colour
     * scale changes, or when the size changes)
     */
    private boolean legendRefresh = false;
    /**
     * Whether or not the legend is currently shrunk. This only applies to
     * legends for >1D fields
     * 
     * TODO Such legends need better handling - currently colour scale etc.
     * can't be adjusted
     */
    private boolean legendShrunk = false;

    /** The {@link PaletteSelectorWidget} to adjust colour scale */
    private PaletteSelectorWidget paletteSelector = null;

    /** The {@link EdalDataLayer} being used to generate the legend */
    private EdalDataLayer edalDataLayer;

    /**
     * Instantiate a new {@link EdalConfigLayer}
     * 
     * @param wwd
     *            The {@link RescWorldWindow} which this layer is attached to
     * @param catalogue
     *            The {@link VideoWallCatalogue} which will supply the layer
     *            menu
     */
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
        this.initialise();
    }

    /**
     * Initialise the required images etc.
     */
    protected void initialise() {
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

        legendAnnotation = new ImageAnnotation();
        legendAnnotation.setPickEnabled(true);
        legendAnnotation.getAttributes().setVisible(false);
        addRenderable(legendAnnotation);

        layerSelector = new LayerSelectorWidget(wwd);
        layerSelector.getAttributes().setVisible(false);
        addRenderable(layerSelector);

        paletteSelector = new PaletteSelectorWidget(wwd, this);
        paletteSelector.getAttributes().setVisible(false);
        paletteSelector.getAttributes().setOpacity(1.0);
        addRenderable(paletteSelector);

        /* Add to WorldWindow to get select events */
        wwd.addSelectListener(this);
        wwd.addSelectListener(layerSelector);
        wwd.addSelectListener(paletteSelector);
    }

    /**
     * Sets the {@link PaletteSelectionHandler} which will receive events when
     * the palette changes
     * 
     * @param handler
     */
    public void setPaletteHandler(PaletteSelectionHandler handler) {
        if (paletteSelector != null) {
            paletteSelector.setPaletteSelectionHandler(handler);
        }
    }

    /**
     * Displays the {@link PaletteSelectorWidget}
     */
    private void displayPaletteSelector() {
        /*
         * Save the palette state so that is can be restored if a user clicks
         * cancel
         */
        paletteSelector.setOpened();
        /*
         * Hide the legend (changes in the palette selector will make it
         * invalid)
         */
        legendAnnotation.getAttributes().setVisible(false);
        paletteSelector.getAttributes().setVisible(true);
    }

    /**
     * Hides the {@link PaletteSelectorWidget} if it is visible
     */
    public void hidePaletteSelector() {
        /*
         * Reshow the legend and set it to be redrawn when it is next visible
         */
        legendAnnotation.getAttributes().setVisible(true);
        paletteSelector.getAttributes().setVisible(false);
        legendRefresh = true;
    }

    public PaletteSelectorWidget getPaletteSelector() {
        return paletteSelector;
    }

    /**
     * Sets the legend to match a given {@link EdalDataLayer}
     * 
     * @param edalDataLayer
     *            The {@link EdalDataLayer} which will provide the legend
     */
    public void setLegend(EdalDataLayer edalDataLayer) {
        this.edalDataLayer = edalDataLayer;
        PlottingStyleParameters plottingMetadata = edalDataLayer.getPlottingMetadata();
        if (plottingMetadata != null) {
            paletteSelector.setPaletteProperties(plottingMetadata);
        }
        legendRefresh = true;
        legendAnnotation.getAttributes().setVisible(true);
    }

    /**
     * Populates the {@link LayerSelectorWidget} with the latest menu and
     * displays it
     */
    private void displayLayerSelector() {
        layerSelector.populateLayerSelector(catalogue.getLayerMenu());
        layerSelector.displayDatasets();
        layerSelector.getAttributes().setVisible(true);
        /*
         * We don't want both the layer and palette selectors visible
         * simultaneously
         */
        paletteSelector.getAttributes().setVisible(false);
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
                    setLinkState(LinkedViewState.LINKED);
                    removeRenderable(linkButton);
                    removeRenderable(antilinkButton);
                    removeRenderable(unlinkButton);
                    addRenderable(linkStateButton);
                }
            } else if (event.getTopObject() == antilinkButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    setLinkState(LinkedViewState.ANTILINKED);
                    removeRenderable(linkButton);
                    removeRenderable(antilinkButton);
                    removeRenderable(unlinkButton);
                    addRenderable(linkStateButton);
                }
            } else if (event.getTopObject() == unlinkButton) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    setLinkState(LinkedViewState.UNLINKED);
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
                } else if (event.getEventAction().equals(SelectEvent.RIGHT_CLICK)) {
                    RescModel model = wwd.getModel();
                    if (model.isFlat()) {
                        model.cycleProjection();
                    }
                }
            } else if (event.getTopObject() == legendAnnotation) {
                if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                    if (legendShrunk) {
                        /*
                         * We have a shrunk version of the legend, in which case
                         * we want to expand it
                         */
                        if (legendAnnotation.getAttributes().getImageScale() == 1.0) {
                            shrinkLegend(100);
                        } else {
                            legendAnnotation.getAttributes().setImageScale(1.0);
                            legendAnnotation.getAttributes().getDrawOffset().y = 0;
                        }
                    } else {
                        /*
                         * We have a normal legend, so we want to bring up the
                         * scale configuration dialog
                         */
                        displayPaletteSelector();
                    }
                }
                /*
                 * TODO Make the cursor appear as a hand on rollover - I think
                 * this needs to be co-ordinated centrally for all elements
                 * which require it
                 */
            }
        }
    }

    public void setLinkState(LinkedViewState linkedViewState) {
        wwd.getView().setLinkState(linkedViewState);
        wwd.redrawNow();
        switch (linkedViewState) {
        case ANTILINKED:
            linkStateButton.setImageSource(ANTILINK_BUTTON);
            break;
        case LINKED:
            linkStateButton.setImageSource(LINK_BUTTON);
            break;
        case UNLINKED:
            linkStateButton.setImageSource(UNLINK_BUTTON);
            break;
        }
    }

    /**
     * The minimum size of the {@link PaletteSelectorWidget}. Generally we set
     * the {@link PaletteSelectorWidget} width to be half the viewport size, but
     * not if that is smaller than this value
     */
    private static final int PALETTE_SELECTOR_MIN_SIZE = 250;

    @Override
    public void render(DrawContext dc) {
        Rectangle viewport = dc.getView().getViewport();

        /*
         * Config buttons
         */
        this.layersButton.setScreenPoint(getButtonLocation(dc, 0, 0));
        this.linkStateButton.setScreenPoint(getButtonLocation(dc, 70, 0));
        this.linkButton.setScreenPoint(getButtonLocation(dc, 70, 0));
        this.antilinkButton.setScreenPoint(getButtonLocation(dc, 70, -70));
        this.unlinkButton.setScreenPoint(getButtonLocation(dc, 70, -140));
        this.flatButton.setScreenPoint(getButtonLocation(dc, 0, -70));

        /*
         * Layer selector (if visible)
         */
        if (this.layerSelector.getAttributes().isVisible()) {
            this.layerSelector.getAttributes().setSize(
                    new Dimension(viewport.width - BORDER_WIDTH, 0));
            Dimension layerSelectorActualSize = layerSelector.getPreferredSize(dc);
            this.layerSelector.setScreenPoint(new Point(viewport.x + viewport.width / 2, viewport.y
                    + BORDER_WIDTH / 2 + (viewport.height - layerSelectorActualSize.height) / 2));
        }

        /*
         * Legend
         */
        if (viewport.height != lastViewportHeight || legendRefresh) {
            lastViewportHeight = viewport.height;
            addLegend(viewport.height / 2);
        }
        if (legendAnnotation != null && legendAnnotation.getImageSource() != null) {
            int width = (int) (((BufferedImage) legendAnnotation.getImageSource()).getWidth() * legendAnnotation
                    .getAttributes().getImageScale());
            int height = (int) (((BufferedImage) legendAnnotation.getImageSource()).getHeight() * legendAnnotation
                    .getAttributes().getImageScale());

            int xOffset = legendShrunk ? -10 : 10;
            int yOffset = legendShrunk ? 10 : height / 2;

            legendAnnotation.setScreenPoint(new Point(
                    viewport.x + viewport.width - width + xOffset, viewport.y + yOffset));
        }

        /*
         * Palette selector (if visible)
         */
        if (this.paletteSelector != null && this.paletteSelector.getAttributes().isVisible()) {
            int paletteSize = viewport.width / 2;
            if (paletteSize < PALETTE_SELECTOR_MIN_SIZE) {
                if (PALETTE_SELECTOR_MIN_SIZE > viewport.width - BORDER_WIDTH) {
                    paletteSize = viewport.width - BORDER_WIDTH;
                } else {
                    paletteSize = PALETTE_SELECTOR_MIN_SIZE;
                }
            }
            this.paletteSelector.getAttributes().setSize(new Dimension(paletteSize, 0));
            Dimension paletteSelectorActualSize = paletteSelector.getPreferredSize(dc);
            this.paletteSelector.setScreenPoint(new Point(viewport.x + viewport.width / 2,
                    viewport.y + BORDER_WIDTH / 2
                            + (viewport.height - paletteSelectorActualSize.height) / 2));
        }

        super.render(dc);
    }

    /**
     * Used to calculate the position for the buttons
     * 
     * @param dc
     *            The current {@link DrawContext}
     * @param xOffset
     *            The offset in pixels in the x direction
     * @param yOffset
     *            The offset in pixels in the y direction
     * @return A {@link Point} representing the absolute position of the button
     */
    private Point getButtonLocation(DrawContext dc, int xOffset, int yOffset) {
        int x = BORDER_WIDTH + layersButton.getPreferredSize(dc).width / 2 + xOffset;
        int y = BORDER_WIDTH - yOffset;
        return new Point(x, y);
    }

    /**
     * Retrieves the legend image and sets it on the annotation
     * 
     * @param size
     *            The size of the legend to generate
     */
    private void addLegend(int size) {
        if (edalDataLayer != null) {
            BufferedImage legend = edalDataLayer.getLegend(size, true);
            if (legend != null) {
                if (legend.getWidth() > size) {
                    /*
                     * We have a big legend - i.e. a multidimensional field
                     */

                    /*
                     * First get a new legend with field labels
                     */
                    legend = edalDataLayer.getLegend(size, true);
                    legendAnnotation.setImageSource(legend);
                    legendShrunk = true;
                    shrinkLegend(100);
                } else {
                    legendAnnotation.setImageSource(legend);
                    legendShrunk = false;
                }
                legendRefresh = false;
            }
        }
    }

    /**
     * Scales the legend
     * 
     * @param desiredSize
     *            The final size of the scaled image
     */
    private void shrinkLegend(int desiredSize) {
        BufferedImage legend = (BufferedImage) legendAnnotation.getAttributes().getImageSource();
        legendAnnotation.getAttributes().setImageScale((double) desiredSize / legend.getWidth());
        legendAnnotation.getAttributes().setDrawOffset(
                new Point(legend.getWidth() / 2, -legend.getHeight() + desiredSize));
    }
}

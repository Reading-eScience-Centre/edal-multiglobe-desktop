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

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;

import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;
import gov.nasa.worldwind.WorldWindow;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.event.SelectListener;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;
import gov.nasa.worldwind.layers.RenderableLayer;
import gov.nasa.worldwind.pick.PickedObject;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

/**
 * Currently just displays a cog and then a layer selector. Functional, but
 * needs to be made nicer (big icons for touchscreen - a Friday afternoon job)
 * 
 * @author Guy
 */
public class EdalConfigLayer extends RenderableLayer implements SelectListener {
    protected RescWorldWindow wwd;
    protected boolean update = true;
    protected VideoWallCatalogue catalogue;

    private ImageAnnotation configButton;
    private ImageAnnotation linkButton;
    private ImageAnnotation antilinkButton;
    private ImageAnnotation unlinkButton;

    private ScreenAnnotation layerSelector;
    private String selectedId = "";

    protected Dimension layerSelectorSize;
    private Color color = Color.decode("#b0b0b0");
    private Color highlightColor = Color.decode("#ffffff");
    private char layerEnabledSymbol = '\u25a0';
    private char layerDisabledSymbol = '\u25a1';
    private Font font = new Font("Monospace", Font.PLAIN, 24);
    private int borderWidth = 20;
    private String position = AVKey.NORTHWEST;
    private Vec4 locationCenter = null;
    private Vec4 locationOffset = null;

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
        configButton = new ImageAnnotation("images/config.png");
        configButton.setScreenPoint(new Point(0, 0));
        configButton.setPickEnabled(true);
        addRenderable(configButton);

        linkButton = new ImageAnnotation("images/link.png");
        linkButton.setScreenPoint(new Point(50, 0));
        linkButton.setPickEnabled(true);
        addRenderable(linkButton);

        antilinkButton = new ImageAnnotation("images/antilink.png");
        antilinkButton.setScreenPoint(new Point(100, 0));
        antilinkButton.setPickEnabled(true);
        addRenderable(antilinkButton);

        unlinkButton = new ImageAnnotation("images/unlink.png");
        unlinkButton.setScreenPoint(new Point(150, 0));
        unlinkButton.setPickEnabled(true);
        addRenderable(unlinkButton);

        // Set up screen annotation that will display the layer list
        this.layerSelector = new ScreenAnnotation("", new Point(0, 0));

        // Set annotation so that it will not force text to wrap (large width) and will adjust it's width to
        // that of the text. A height of zero will have the annotation height follow that of the text too.
        this.layerSelector.getAttributes().setSize(new Dimension(Integer.MAX_VALUE, 0));
        this.layerSelector.getAttributes().setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);

        // Set appearance attributes
        this.layerSelector.getAttributes().setCornerRadius(0);
        this.layerSelector.getAttributes().setFont(this.font);
        this.layerSelector.getAttributes().setHighlightScale(1);
        this.layerSelector.getAttributes().setTextColor(Color.WHITE);
        this.layerSelector.getAttributes().setBackgroundColor(new Color(0f, 0f, 0f, .5f));
        this.layerSelector.getAttributes().setInsets(new Insets(6, 6, 6, 6));
        this.layerSelector.getAttributes().setBorderWidth(1);

        // Listen to world window for select event
        this.wwd.addSelectListener(this);
    }

    private void displayConfigButton() {
        addRenderable(configButton);
        removeRenderable(layerSelector);
    }

    private void displayLayerSelector() {
        addRenderable(layerSelector);
        removeRenderable(configButton);
    }

    /**
     * <code>SelectListener</code> implementation.
     * 
     * @param event
     *            the current <code>SelectEvent</code>
     */
    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects() && event.getTopObject() == this.layerSelector) {
            /*
             * We are in the layer selector and something has happened.
             */
            boolean update = false;
            if (event.getEventAction().equals(SelectEvent.ROLLOVER)
                    || event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                // Highlight annotation
                if (!this.layerSelector.getAttributes().isHighlighted()) {
                    this.layerSelector.getAttributes().setHighlighted(true);
                    update = true;
                }
                // Check for text or url
                PickedObject po = event.getTopPickedObject();
                if (po.getValue(AVKey.URL) != null) {
                    // Set cursor hand on hyperlinks
                    ((Component) this.wwd)
                            .setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    String layerId = (String) po.getValue(AVKey.URL);
                    // Enable/disable layer on left click
                    if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                        if (layerId.startsWith("expand:")) {
                            String idToExpand = layerId.substring(layerId.indexOf(":") + 1);
                            toggleExpand(idToExpand, catalogue.getEdalLayers());
                        } else if (selectedId.equals(layerId)) {
                            selectedId = null;
                            wwd.getModel().setDataLayer(null);
                            displayConfigButton();
                        } else {
                            selectedId = layerId;
                            wwd.getModel().setDataLayer(selectedId);
                            displayConfigButton();
                        }
                        update = true;
                    }
                } else {
                    /*
                     * Clicked, but didn't hit a URL.  Do nothing (for now...)
                     */
                }
            }
            /* Redraw config components if needed */
            if (update) {
                this.update();
            }
        } else if (event.hasObjects() && event.getTopObject() == configButton) {
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                displayLayerSelector();
            }
        } else if (event.hasObjects() && event.getTopObject() == linkButton) {
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                wwd.setLinkedView();
            }
        } else if (event.hasObjects() && event.getTopObject() == antilinkButton) {
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                wwd.setAntilinkedView();
            }
        } else if (event.hasObjects() && event.getTopObject() == unlinkButton) {
            if (event.getEventAction().equals(SelectEvent.LEFT_CLICK)) {
                wwd.setUnlinkedView();
            }
        } else if (event.getEventAction().equals(SelectEvent.ROLLOVER)
                && this.layerSelector.getAttributes().isHighlighted()) {
            displayConfigButton();
            // de-highlight annotation
            this.layerSelector.getAttributes().setHighlighted(false);
            ((Component) this.wwd).setCursor(Cursor.getDefaultCursor());
            this.update();
        }
    }

    private void toggleExpand(String idToExpand, ActiveLayerMenuItem edalLayers) {
        for (ActiveLayerMenuItem child : edalLayers.getChildren()) {
            if (child.getId().equals(idToExpand)) {
                child.setExpanded(!child.isExpanded());
                return;
            }
            toggleExpand(idToExpand, child);
        }
    }

    /**
     * Compose the annotation text from the given <code>LayerList</code>.
     * 
     * @param layers
     *            the <code>LayerList</code> to draw names from.
     * 
     * @return the annotation text to be displayed.
     */
    protected String createLayerMenu(ActiveLayerMenuItem rootNode) {
        if (rootNode == null || rootNode.getChildren() == null) {
            return "No layers loaded yet";
        }
        // Compose html text
        StringBuilder text = new StringBuilder();
        for (ActiveLayerMenuItem layer : rootNode.getChildren()) {
            addChildNodes(text, layer, 0);
        }
        if (text.length() == 0) {
            return "No layers loaded yet";
        }
        return text.toString();
    }

    /** Schedule the layer list for redrawing before the next render pass. */
    public void update() {
        this.update = true;
        this.wwd.redraw();
    }

    /**
     * Set the <code>Font</code> used to draw the layer list text.
     * 
     * @param font
     *            the <code>Font</code> used to draw the layer list text.
     */
    public void setFont(Font font) {
        if (font == null) {
            String message = Logging.getMessage("nullValue.FontIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }

        if (!this.font.equals(font)) {
            this.font = font;
            this.layerSelector.getAttributes().setFont(font);
            this.update();
        }
    }

    /**
     * Set the <code>Color</code> used to draw the layer names and the frame
     * border when they are not highlighted.
     * 
     * @param color
     *            the <code>Color</code> used to draw the layer names and the
     *            frame border when they are not highlighted.
     */
    public void setColor(Color color) {
        if (color == null) {
            String msg = Logging.getMessage("nullValue.ColorIsNull");
            Logging.logger().severe(msg);
            throw new IllegalArgumentException(msg);
        }

        this.color = color;
        this.update();
    }

    /**
     * Set the character used to denote an enabled layer.
     * 
     * @param c
     *            the character used to denote an enabled layer.
     */
    public void setLayerEnabledSymbol(char c) {
        this.layerEnabledSymbol = c;
        this.update();
    }

    /**
     * Set the character used to denote a disabled layer.
     * 
     * @param c
     *            the character used to denote a disabled layer.
     */
    public void setLayerDisabledSymbol(char c) {
        this.layerDisabledSymbol = c;
        this.update();
    }

    /**
     * Sets the layer manager frame offset from the viewport borders.
     * 
     * @param borderWidth
     *            the number of pixels to offset the layer manager frame from
     *            the borders indicated by {@link #setPosition(String)}.
     */
    public void setBorderWidth(int borderWidth) {
        this.borderWidth = borderWidth;
        this.update();
    }

    /**
     * Sets the relative viewport location to display the layer manager. Can be
     * one of {@link AVKey#NORTHEAST} (the default), {@link AVKey#NORTHWEST},
     * {@link AVKey#SOUTHEAST}, or {@link AVKey#SOUTHWEST}. These indicate the
     * corner of the viewport to place the frame.
     * 
     * @param position
     *            the desired layer manager position
     */
    public void setPosition(String position) {
        if (position == null) {
            String message = Logging.getMessage("nullValue.ScreenPositionIsNull");
            Logging.logger().severe(message);
            throw new IllegalArgumentException(message);
        }
        this.position = position;
        this.update();
    }

    /**
     * Specifies the screen location of the layer manager, relative to it's
     * frame center. May be null. If this value is non-null, it overrides the
     * position specified by #setPosition. The location is specified in pixels.
     * The origin is the window's lower left corner. Positive X values are to
     * the right of the origin, positive Y values are upwards from the origin.
     * The final frame location will be affected by the currently specified
     * location offset if a non-null location offset has been specified (see
     * {@link #setLocationOffset(Vec4)}).
     * 
     * @param locationCenter
     *            the location center. May be null.
     * 
     * @see #setPosition(String)
     * @see #setLocationOffset(gov.nasa.worldwind.geom.Vec4)
     */
    public void setLocationCenter(Vec4 locationCenter) {
        this.locationCenter = locationCenter;
        this.update();
    }

    /**
     * Force the layer list to redraw itself from the current <code>Model</code>
     * with the current highlighted state and selected layer colors and opacity.
     * 
     * @param dc
     *            the current {@link DrawContext}.
     * 
     * @see #setMinOpacity(double)
     * @see #setMaxOpacity(double)
     * @see #setColor(java.awt.Color)
     * @see #setHighlightColor(java.awt.Color)
     */
    public void updateNow(DrawContext dc) {
        // Compose html text
        String text = this.createLayerMenu(catalogue.getEdalLayers());
        this.layerSelector.setText(text);

        // Update current size and adjust annotation draw offset according to it's width
        // TODO: handle annotation scaling
        this.layerSelectorSize = this.layerSelector.getPreferredSize(dc);
        this.layerSelector.getAttributes().setDrawOffset(
                new Point(this.layerSelectorSize.width / 2, 0));

        // Clear update flag
        this.update = false;
    }

    private void addChildNodes(StringBuilder text, ActiveLayerMenuItem parent, int level) {
        if (parent == null) {
            return;
        }
        Color color = (false) ? this.highlightColor : this.color;
        text.append("<font color=\"");
        text.append(encodeHTMLColor(new Color(0, true)));
        text.append("\">");
        for (int i = 0; i < level; i++) {
            text.append("<img src=\"images/config.png\" width=\"10\"/> ");
        }
        text.append("<font color=\"");
        text.append(encodeHTMLColor(color));
        text.append("\">");
        if (parent.getChildren() != null && parent.getChildren().size() > 0) {
            text.append("<a href=\"expand:");
            text.append(parent.getId());
            text.append("\">");
            text.append("<font color=\"");
            text.append(encodeHTMLColor(color));
            text.append("\">");
            if (parent.isExpanded()) {
                text.append("-");
            } else {
                text.append("+");
            }
            text.append("</a>");
        }
        if (parent.isPlottable()) {
            text.append("<a href=\"");
            text.append(parent.getId());
            text.append("\">");
            text.append("<font color=\"");
            text.append(encodeHTMLColor(color));
            text.append("\">");
        }
        if (parent.isPlottable()) {
            text.append((parent.getId().equals(selectedId) ? layerEnabledSymbol
                    : layerDisabledSymbol));
        }
        text.append("<font color=\"");
        text.append(encodeHTMLColor(color));
        text.append("\">");
        text.append(" ");
        text.append(parent.getTitle());
        text.append("</a><br />");
        if (parent.getChildren() != null && parent.isExpanded()) {
            for (ActiveLayerMenuItem child : parent.getChildren()) {
                addChildNodes(text, child, level + 1);
            }
        }
    }

    protected static String encodeHTMLColor(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    @Override
    public void render(DrawContext dc) {
        if (this.update)
            this.updateNow(dc);

        /*
         * TODO Add the other buttons
         */
        this.layerSelector.setScreenPoint(computeLocation(dc.getView().getViewport()));
        this.configButton.setScreenPoint(getButtonLocation(dc, 0));
        this.linkButton.setScreenPoint(getButtonLocation(dc, 50));
        this.antilinkButton.setScreenPoint(getButtonLocation(dc, 100));
        this.unlinkButton.setScreenPoint(getButtonLocation(dc, 150));
        super.render(dc);
    }

    private Point getButtonLocation(DrawContext dc, int offset) {
        int x = this.borderWidth + configButton.getPreferredSize(dc).width + offset;
        int y = (int) dc.getView().getViewport().getHeight()
                - configButton.getPreferredSize(dc).height - this.borderWidth;
        return new Point(x, y);
    }

    /**
     * Compute the draw frame south-west corner screen location according to
     * it's position - see {@link #setPosition(String)}, location offset - see
     * {@link #setLocationOffset(Vec4)}, or location center - see
     * {@link #setLocationCenter(Vec4)}, and border distance from the viewport
     * edges - see {@link #setBorderWidth(int)}.
     * 
     * @param viewport
     *            the current <code>Viewport</code> rectangle.
     * 
     * @return the draw frame south-west corner screen location.
     */
    protected Point computeLocation(Rectangle viewport) {
        // TODO: handle annotation scaling
        int width = this.layerSelectorSize.width;
        int height = this.layerSelectorSize.height;

        int x;
        int y;

        if (this.locationCenter != null) {
            x = (int) this.locationCenter.x - width / 2;
            y = (int) this.locationCenter.y - height / 2;
        } else if (this.position.equals(AVKey.NORTHEAST)) {
            x = (int) viewport.getWidth() - width - this.borderWidth;
            y = (int) viewport.getHeight() - height - this.borderWidth;
        } else if (this.position.equals(AVKey.SOUTHEAST)) {
            x = (int) viewport.getWidth() - width - this.borderWidth;
            //noinspection SuspiciousNameCombination
            y = this.borderWidth;
        } else if (this.position.equals(AVKey.NORTHWEST)) {
            x = this.borderWidth;
            y = (int) viewport.getHeight() - height - this.borderWidth;
        } else if (this.position.equals(AVKey.SOUTHWEST)) {
            x = this.borderWidth;
            //noinspection SuspiciousNameCombination
            y = this.borderWidth;
        } else // use North East as default
        {
            x = (int) viewport.getWidth() - width - this.borderWidth;
            y = (int) viewport.getHeight() - height - this.borderWidth;
        }

        if (this.locationOffset != null) {
            x += this.locationOffset.x;
            y += this.locationOffset.y;
        }

        return new Point(x, y);
    }

    @Override
    public String toString() {
        return Logging.getMessage("layers.LayerManagerLayer.Name");
    }

}

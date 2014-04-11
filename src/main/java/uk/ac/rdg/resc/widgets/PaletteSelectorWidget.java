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
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.ScreenAnnotation;
import gov.nasa.worldwindx.examples.util.ImageAnnotation;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

import uk.ac.rdg.resc.EdalConfigLayer;
import uk.ac.rdg.resc.RescWorldWindow;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.graphics.style.ColourScale;
import uk.ac.rdg.resc.edal.graphics.style.ColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.SegmentColourScheme;
import uk.ac.rdg.resc.edal.graphics.style.util.ColourPalette;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.wms.WmsLayerMetadata;

/**
 * A widget for selecting colour scale settings, including palette type, scale
 * limits, and logarithmic scaling
 * 
 * @author Guy
 */
public class PaletteSelectorWidget extends ScreenAnnotation implements SelectListener {

    /** The gap to use between elements */
    private static final int GAP = 4;
    /** The width of the colourbar in the main palette window */
    private static final int COLOURBAR_WIDTH = 35;
    /** The border to use at the edges */
    private static final int BORDER = 10;

    /*
     * Button images
     */
    private static final String OK_IMAGE = "images/ok_button.png";
    private static final String CLOSE_IMAGE = "images/close_button.png";

    /** Transparent, for convenience */
    private static final Color TRANSPARENT_COLOR = new Color(0, true);

    /**
     * The object which will receive events from this
     * {@link PaletteSelectorWidget}
     */
    private PaletteSelectionHandler handler;
    /** The {@link EdalConfigLayer} containing this {@link PaletteSelectorWidget} */
    private EdalConfigLayer parentLayer;

    /**
     * The last width of the {@link PaletteSelectorWidget}, used to recalculate
     * colour bars if it changes
     */
    private int lastItemWidth = -1;
    /** Used to modify values by dragging */
    private Point lastDragPoint = null;
    /** Used to trigger a redraw if the palette changes */
    private boolean paletteChanged = false;

    /*
     * Various elements of the palette selector
     */
    private ScreenAnnotation titleAnnotation;

    private ScreenAnnotation colourBarPanel;
    private ImageAnnotation colourBar;

    private ScreenAnnotation aboveMax;
    private ExtremeState aboveMaxState = ExtremeState.BLACK;
    private ScreenAnnotation belowMin;
    private ExtremeState belowMinState = ExtremeState.BLACK;

    private ScreenAnnotation labelsPanel;
    private ScreenAnnotation minLabel;
    private ScreenAnnotation opacityLabel;
    private int opacity = 100;
    private ScreenAnnotation maxLabel;

    private ScreenAnnotation buttonsPanel;
    private ScreenAnnotation cancelButton;
    private ScreenAnnotation okButton;

    private ScreenAnnotation paletteChooserPanel;
    private ScreenAnnotation paletteChooser;
    private Map<ImageAnnotation, Runnable> paletteImages = new HashMap<>();

    private ScreenAnnotation nColoursPanel;
    private Map<ScreenAnnotation, Integer> nColoursLabels = new HashMap<>();

    /*
     * The current state of the palette
     */
    private Float minScale = -Float.MAX_VALUE;
    private Float maxScale = Float.MAX_VALUE;
    private String paletteName = null;
    private boolean logScaling = false;
    private int numColourBands = 250;
    private Color belowMinColour = Color.black;
    private Color aboveMaxColour = Color.black;

    /*
     * The state of the palette when it was first displayed (used to reset if a
     * user cancels)
     */
    private Float lastMinScale = -Float.MAX_VALUE;
    private Float lastMaxScale = Float.MAX_VALUE;
    private String lastPaletteName = ColourPalette.DEFAULT_PALETTE_NAME;
    private boolean lastLogScaling = false;
    private int lastNumColourBands = ColourPalette.MAX_NUM_COLOURS;
    private Color lastBelowMinColour = Color.black;
    private Color lastAboveMaxColour = Color.black;

    /**
     * The {@link ColourScheme} which encapsulates the palette settings. Used to
     * generate colourbars
     */
    private ColourScheme colourScheme;

    /**
     * Creates a new {@link PaletteSelectorWidget}
     * 
     * @param wwd
     *            The containing {@link RescWorldWindow}
     * @param parentLayer
     *            The {@link EdalConfigLayer} to which this
     *            {@link PaletteSelectorWidget} belongs
     */
    public PaletteSelectorWidget(RescWorldWindow wwd, EdalConfigLayer parentLayer) {
        super("", new Point());

        this.parentLayer = parentLayer;

        initialise();
    }

    /**
     * Initialises and lays out the palette selector components
     */
    private void initialise() {
        titleAnnotation = new ScreenAnnotation("Adjust Colour Scale", new Point());
        WidgetUtils.setDefaultButtonAttributes(titleAnnotation.getAttributes());
        titleAnnotation.getAttributes().setFont(new Font("SansSerif", Font.BOLD, 16));

        setLayout(new AnnotationFlowLayout(AVKey.VERTICAL, AVKey.CENTER, 0, GAP));

        colourBarPanel = new ScreenAnnotation("", new Point());
        AnnotationFlowLayout colourBarLayout = new AnnotationFlowLayout(AVKey.HORIZONTAL,
                AVKey.RIGHT, 1, 0);
        colourBarPanel.setLayout(colourBarLayout);
        WidgetUtils.setupContainerAttributes(colourBarPanel.getAttributes());

        colourBarPanel.getAttributes().setBackgroundColor(new Color(0, 0, 0, 64));
        colourBarPanel.getAttributes().setBorderWidth(1.0);
        colourBarPanel.getAttributes().setBorderColor(Color.lightGray);

        colourBar = new ImageAnnotation();
        colourBar.getAttributes().setBorderWidth(0.0);
        colourBar.getAttributes().setOpacity(1.0);
        colourBar.setPickEnabled(true);
        belowMin = new ScreenAnnotation("", new Point());
        belowMin.setPickEnabled(true);
        aboveMax = new ScreenAnnotation("", new Point());
        aboveMax.setPickEnabled(true);
        setExtremeColourAttrs(belowMin.getAttributes());
        setExtremeColourAttrs(aboveMax.getAttributes());
        colourBarPanel.addChild(belowMin);
        colourBarPanel.addChild(colourBar);
        colourBarPanel.addChild(aboveMax);

        labelsPanel = new ScreenAnnotation("", new Point());
        AnnotationFlowLayout labels = new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.RIGHT, 0, 4);
        labelsPanel.setLayout(labels);
        WidgetUtils.setupContainerAttributes(labelsPanel.getAttributes());
        labelsPanel.getAttributes().setBackgroundColor(new Color(0, true));

        minLabel = new ScreenAnnotation("", new Point());
        minLabel.setPickEnabled(true);
        WidgetUtils.setDefaultButtonAttributes(minLabel.getAttributes());
        minLabel.getAttributes().setSize(new Dimension(100, 0));

        opacityLabel = new ScreenAnnotation("Opacity\n100%", new Point());
        opacityLabel.setPickEnabled(true);
        WidgetUtils.setDefaultButtonAttributes(opacityLabel.getAttributes());
        opacityLabel.getAttributes().setSize(new Dimension(50, 0));

        maxLabel = new ScreenAnnotation("", new Point());
        maxLabel.setPickEnabled(true);
        WidgetUtils.setDefaultButtonAttributes(maxLabel.getAttributes());
        maxLabel.getAttributes().setSize(new Dimension(100, 0));

        labelsPanel.addChild(minLabel);
        labelsPanel.addChild(opacityLabel);
        labelsPanel.addChild(maxLabel);

        buttonsPanel = new ScreenAnnotation("", new Point());
        AnnotationFlowLayout buttons = new AnnotationFlowLayout(AVKey.HORIZONTAL, AVKey.CENTER,
                100, 0);
        buttonsPanel.setLayout(buttons);
        WidgetUtils.setupContainerAttributes(buttonsPanel.getAttributes());
        buttonsPanel.getAttributes().setBackgroundColor(new Color(0, true));

        cancelButton = new ImageAnnotation(CLOSE_IMAGE);
        cancelButton.setPickEnabled(true);

        okButton = new ImageAnnotation(OK_IMAGE);
        okButton.setPickEnabled(true);

        buttonsPanel.addChild(cancelButton);
        buttonsPanel.addChild(okButton);

        paletteChooser = new ScreenAnnotation("", new Point());
        WidgetUtils.setupContainerAttributes(paletteChooser.getAttributes());
        AnnotationFlowLayout paletteChooserLayout = new AnnotationFlowLayout(AVKey.VERTICAL,
                AVKey.RIGHT, GAP, GAP);
        paletteChooser.setLayout(paletteChooserLayout);

        paletteChooserPanel = new ScreenAnnotation("", new Point());
        WidgetUtils.setupContainerAttributes(paletteChooserPanel.getAttributes());
        AnnotationFlowLayout paletteChooserPanelLayout = new AnnotationFlowLayout(AVKey.HORIZONTAL,
                AVKey.RIGHT, GAP, 0);
        paletteChooserPanel.setLayout(paletteChooserPanelLayout);

        nColoursPanel = new ScreenAnnotation("", new Point());
        WidgetUtils.setupContainerAttributes(nColoursPanel.getAttributes());
        AnnotationFlowLayout nColoursPanelLayout = new AnnotationFlowLayout(AVKey.HORIZONTAL,
                AVKey.LEFT, GAP, 0);
        nColoursPanel.setLayout(nColoursPanelLayout);

        ScreenAnnotation nColoursTitle = new ScreenAnnotation("Colour bands", new Point());
        WidgetUtils.setDefaultButtonAttributes(nColoursTitle.getAttributes());
        nColoursTitle.getAttributes().setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
        nColoursTitle.getAttributes().getInsets().left = 10;
        nColoursTitle.getAttributes().getInsets().right = 10;
        nColoursPanel.addChild(nColoursTitle);
        /*
         * Buttons to select the number of colour bands. I've chosen sensible
         * defaults here
         */
        for (int i : new int[] { 250, 100, 50, 20, 10, 5 }) {
            ScreenAnnotation nColoursLabel = new ScreenAnnotation(i + "", new Point());
            nColoursLabel.setPickEnabled(true);
            WidgetUtils.setDefaultButtonAttributes(nColoursLabel.getAttributes());
            nColoursLabel.getAttributes().setAdjustWidthToText(AVKey.SIZE_FIT_TEXT);
            nColoursLabel.getAttributes().getInsets().left = 10;
            nColoursLabel.getAttributes().getInsets().right = 10;
            nColoursPanel.addChild(nColoursLabel);
            nColoursLabels.put(nColoursLabel, i);
        }

        paletteChooser.addChild(paletteChooserPanel);
        populatePaletteImages();

        getAttributes().setBackgroundColor(new Color(0, 0, 0, 64));
        getAttributes().setCornerRadius(10);
        getAttributes().setOpacity(0.8);

        /*
         * Set the selector to be the main screen (as opposed to the palette
         * selection)
         */
        setMainSelector();
    }

    /**
     * Generates the colour bar images for all available palettes
     */
    private void populatePaletteImages() {
        paletteChooserPanel.removeAllChildren();
        for (final String palette : ColourPalette.getPredefinedPalettes()) {
            SegmentColourScheme cs = new SegmentColourScheme(new ColourScale(0f, 1f, logScaling),
                    belowMinColour, aboveMaxColour, TRANSPARENT_COLOR, palette, numColourBands);
            BufferedImage scaleBar = cs.getScaleBar(1, 200, 0.0f, true, false, null, null);
            ImageAnnotation paletteImage = new ImageAnnotation(scaleBar);
            paletteImage.getAttributes().setImageRepeat(AVKey.REPEAT_X);
            paletteImage.setPickEnabled(true);
            paletteChooserPanel.addChild(paletteImage);
            /*
             * If a new palette is chosen, send values to the
             * PaletteSelectionHandler
             */
            paletteImages.put(paletteImage, new Runnable() {
                @Override
                public void run() {
                    PaletteSelectorWidget.this.paletteName = palette;
                    setupColourScheme();
                    setExtremeColour(true);
                    setExtremeColour(false);
                    if (handler != null) {
                        handler.bulkChange(Extents.newExtent(minScale, maxScale), palette,
                                belowMinColour, aboveMaxColour, logScaling, numColourBands);
                    }
                }
            });
        }
    }

    /**
     * Sets the state of this {@link PaletteSelectorWidget} to just opened. This
     * means that clicking cancel will restore the state to how it was prior to
     * this call
     */
    public void setOpened() {
        lastAboveMaxColour = aboveMaxColour;
        lastBelowMinColour = belowMinColour;
        lastLogScaling = logScaling;
        lastMaxScale = maxScale;
        lastMinScale = minScale;
        //        lastNoDataColour = noDataColour;
        lastNumColourBands = numColourBands;
        lastPaletteName = paletteName;
    }

    /**
     * Sets the {@link PaletteSelectorWidget} to the main screen
     */
    private void setMainSelector() {
        this.removeAllChildren();
        this.addChild(titleAnnotation);
        this.addChild(colourBarPanel);
        this.addChild(labelsPanel);
        this.addChild(nColoursPanel);
        this.addChild(buttonsPanel);
        paletteChooser.getAttributes().setVisible(false);
    }

    /**
     * Sets the {@link PaletteSelectorWidget} to the palette chooser
     */
    private void setPaletteChooser() {
        this.removeAllChildren();
        this.addChild(paletteChooser);
        paletteChooser.getAttributes().setVisible(true);
    }

    /**
     * @param handler
     *            The {@link PaletteSelectionHandler} which will receive events
     */
    public void setPaletteSelectionHandler(PaletteSelectionHandler handler) {
        this.handler = handler;
    }

    /**
     * Sets the values of all properties of this {@link PaletteSelectorWidget}
     * 
     * @param plottingMetadata
     *            The {@link WmsLayerMetadata} representing the palette values
     * @param belowMinColour
     *            The {@link Color} to use for data whose value is below the
     *            minimum scale value
     * @param aboveMaxColour
     *            The {@link Color} to use for data whose value is above the
     *            maximum scale value
     */
    public void setPaletteProperties(WmsLayerMetadata plottingMetadata, Color belowMinColour,
            Color aboveMaxColour) {
        setMinScale(plottingMetadata.getColorScaleRange().getLow());
        setMaxScale(plottingMetadata.getColorScaleRange().getHigh());

        this.paletteName = plottingMetadata.getPalette();
        this.logScaling = plottingMetadata.isLogScaling();
        this.numColourBands = plottingMetadata.getNumColorBands();
        this.belowMinColour = belowMinColour;
        this.aboveMaxColour = aboveMaxColour;

        setupColourScheme();
        setupColourBar(1);
    }

    /**
     * @param minScale
     *            The minimum value on the colour scale
     */
    private void setMinScale(float minScale) {
        if (minScale > maxScale) {
            minScale = maxScale;
        }
        this.minScale = minScale;
        minLabel.setText(FeatureInfoBalloon.NUMBER_3DP.format(this.minScale));
    }

    /**
     * @param maxScale
     *            The maximum value on the colour scale
     */
    private void setMaxScale(float maxScale) {
        if (maxScale < minScale) {
            maxScale = minScale;
        }
        this.maxScale = maxScale;
        maxLabel.setText(FeatureInfoBalloon.NUMBER_3DP.format(this.maxScale));
    }

    /**
     * @param opacity
     *            The overall opacity of the colour scale
     */
    private void setOpacity(int opacity) {
        if (opacity < 0) {
            opacity = 0;
        }
        if (opacity > 100) {
            opacity = 100;
        }
        this.opacity = opacity;
        opacityLabel.setText("Opacity\n" + opacity + "%");
    }

    /**
     * @param numColourBands
     *            The number of colour bands to use
     */
    private void setNumColourBands(int numColourBands) {
        if (numColourBands < 1) {
            numColourBands = 1;
        }
        if (numColourBands > ColourPalette.MAX_NUM_COLOURS) {
            numColourBands = ColourPalette.MAX_NUM_COLOURS;
        }
        this.numColourBands = numColourBands;
        //        nColoursLabel.setText("Colours:\n" + numColourBands);
        populatePaletteImages();
    }

    /**
     * Recalculates the colour scheme.
     */
    private void setupColourScheme() {
        ColourScale colourScale = new ColourScale(Extents.newExtent(minScale, maxScale), logScaling);
        colourScheme = new SegmentColourScheme(colourScale, belowMinColour, aboveMaxColour,
                TRANSPARENT_COLOR, paletteName, numColourBands);
    }

    /**
     * Regenerates the colourbar
     * 
     * @param length
     *            The length to draw
     */
    private void setupColourBar(int length) {
        colourBar.setImageSource(colourScheme.getScaleBar(length, COLOURBAR_WIDTH, 0.0f, false,
                false, null, null));
        setExtremeColour(true);
        setExtremeColour(false);
    }

    /**
     * Changes the value to use outside of the normal range
     * 
     * @param belowMin
     *            <code>true</code> to setup the colour for below minimum,
     *            <code>false</code> for above maximum
     */
    private void setExtremeColour(boolean belowMin) {
        if (belowMin) {
            switch (belowMinState) {
            case EXTEND:
                belowMinColour = colourScheme.getColor(colourScheme.getScaleMin());
                break;
            case TRANSPARENT:
                belowMinColour = TRANSPARENT_COLOR;
                break;
            case BLACK:
            default:
                belowMinColour = Color.black;
                break;
            }
            this.belowMin.getAttributes().setBackgroundColor(belowMinColour);
        } else {
            switch (aboveMaxState) {
            case EXTEND:
                aboveMaxColour = colourScheme.getColor(colourScheme.getScaleMax());
                break;
            case TRANSPARENT:
                aboveMaxColour = TRANSPARENT_COLOR;
                break;
            case BLACK:
            default:
                aboveMaxColour = Color.black;
                break;
            }
            this.aboveMax.getAttributes().setBackgroundColor(aboveMaxColour);
        }
        setupColourScheme();
    }

    @Override
    public void selected(SelectEvent event) {
        if (event.hasObjects()) {
            Object pickedObj = event.getTopObject();
            if (pickedObj == minLabel || pickedObj == maxLabel || pickedObj == opacityLabel) {
                /*
                 * Min value, max value and opacity are changed by dragging
                 */
                if (event.isLeftPress()) {
                    lastDragPoint = event.getPickPoint();
                }
                if (event.isDrag()) {
                    float changePerPixel = (maxScale - minScale) / 200;
                    Point point = event.getMouseEvent().getPoint();
                    int diff = point.x - lastDragPoint.x;
                    float change = changePerPixel * diff;
                    if (pickedObj == minLabel) {
                        setMinScale(this.minScale + change);
                    } else if (pickedObj == maxLabel) {
                        setMaxScale(this.maxScale + change);
                    } else if (pickedObj == opacityLabel) {
                        setOpacity(opacity + diff / 4);
                        if (handler != null) {
                            handler.setOpacity(opacity / 100.0);
                        }
                    }
                    lastDragPoint = point;
                }
            }
            if (event.isLeftClick()) {
                if (pickedObj == cancelButton) {
                    /*
                     * Cancels the current operation and restores the state
                     */
                    parentLayer.hidePaletteSelector();
                    if (handler != null) {
                        handler.bulkChange(Extents.newExtent(lastMinScale, lastMaxScale),
                                lastPaletteName, lastBelowMinColour, lastAboveMaxColour,
                                lastLogScaling, lastNumColourBands);
                        minScale = lastMinScale;
                        maxScale = lastMaxScale;
                        paletteName = lastPaletteName;
                        belowMinColour = lastBelowMinColour;
                        aboveMaxColour = lastAboveMaxColour;
                        logScaling = lastLogScaling;
                        numColourBands = lastNumColourBands;
                    }
                } else if (pickedObj == okButton) {
                    /*
                     * Just hide the selector - all changes have already passed
                     * to the palette handler
                     */
                    parentLayer.hidePaletteSelector();
                } else if (pickedObj == aboveMax) {
                    /*
                     * Cycle through out-of-range states for above max
                     */
                    aboveMaxState = aboveMaxState.getNext();
                    setExtremeColour(false);
                    if (handler != null) {
                        handler.aboveMaxColourChanged(aboveMaxColour);
                    }
                } else if (pickedObj == belowMin) {
                    /*
                     * Cycle through out-of-range states for above max
                     */
                    belowMinState = belowMinState.getNext();
                    setExtremeColour(true);
                    if (handler != null) {
                        handler.belowMinColourChanged(belowMinColour);
                    }
                } else if (pickedObj == colourBar) {
                    /*
                     * If the colourbar is clicked, show the different available
                     * palettes
                     */
                    setPaletteChooser();
                } else if (paletteImages.containsKey(pickedObj)) {
                    /*
                     * If a new palette is chosed, go back to the main selector
                     */
                    paletteImages.get(pickedObj).run();
                    paletteChanged = true;
                    setMainSelector();
                } else if (nColoursLabels.containsKey(pickedObj)) {
                    /*
                     * If a new number of colour bands is chosen, set it.
                     */
                    setNumColourBands(nColoursLabels.get(pickedObj));
                    if (handler != null) {
                        handler.setNumColourBands(numColourBands);
                    }
                }
            } else if (event.isRollover()) {
            } else if (event.isDragEnd()) {
                /*
                 * We only change scale limits once dragging is finished
                 */
                if (handler != null) {
                    if (pickedObj == minLabel || pickedObj == maxLabel) {
                        handler.scaleLimitsChanged(Extents.newExtent(minScale, maxScale));
                    }
                }
            }
            event.consume();
        }
    }

    @Override
    public void render(DrawContext dc) {
        super.render(dc);
        Dimension size = getAttributes().getSize();
        int itemWidth = size.width - 2 * BORDER;

        if (paletteChooser.getAttributes().isVisible()) {
            /*
             * Layout the palette chooser
             */
            Annotation nColoursChooser = paletteChooser.getChildren().get(0);
            int totalUsedWidth = 0;
            for (Annotation a : nColoursChooser.getChildren()) {
                totalUsedWidth += a.getPreferredSize(dc).width;
            }
            int nItems = nColoursChooser.getChildren().size();
            ((AnnotationFlowLayout) nColoursChooser.getLayout())
                    .setHorizontalGap((itemWidth - totalUsedWidth) / (nItems - 1));
            if (!paletteChooserPanel.getChildren().isEmpty()) {
                int childWidth = itemWidth / paletteChooserPanel.getChildren().size() - GAP;
                for (Annotation a : paletteChooserPanel.getChildren()) {
                    a.getAttributes().setSize(new Dimension(childWidth, 0));
                    a.getAttributes().setInsets(new Insets(0, childWidth / 2, 0, childWidth / 2));
                }
            }
        } else {
            /*
             * Layout the main selector
             */
            if ((paletteChanged || lastItemWidth != itemWidth) && paletteName != null) {
                lastItemWidth = itemWidth;
                int colourBarLength = itemWidth - 2 * COLOURBAR_WIDTH;
                setupColourBar(colourBarLength);
            }

            titleAnnotation.getAttributes().setSize(new Dimension(itemWidth, 0));
            colourBarPanel.getAttributes().setSize(new Dimension(itemWidth, 0));

            labelsPanel.getAttributes().setSize(new Dimension(itemWidth, 0));

            /*
             * Space out the labels to fill the maximum available space
             */
            int hgap = itemWidth - minLabel.getAttributes().getSize().width
                    - maxLabel.getAttributes().getSize().width
                    - opacityLabel.getAttributes().getSize().width;
            hgap /= 2;
            if (hgap < 0) {
                hgap = 0;
            }
            ((AnnotationFlowLayout) labelsPanel.getLayout()).setHorizontalGap(hgap);
        }
    }

    private static void setExtremeColourAttrs(AnnotationAttributes attributes) {
        attributes.setBorderWidth(0.0);
        attributes.setCornerRadius(0);
        attributes.setSize(new Dimension(COLOURBAR_WIDTH, COLOURBAR_WIDTH));
    }

    /**
     * The methods which can be called if a colour scale is changed in some way
     */
    public interface PaletteSelectionHandler {
        public void scaleLimitsChanged(Extent<Float> newScaleRange);

        public void paletteChanged(String newPalette);

        public void aboveMaxColourChanged(Color aboveMax);

        public void belowMinColourChanged(Color belowMin);

        public void setNumColourBands(int numColourBands);

        public void setOpacity(double opacity);

        public void bulkChange(Extent<Float> scaleRange, String palette, Color belowMin,
                Color aboveMax, boolean logScaling, int numColourBands);
    }

    /**
     * The states used for out-of-range colours
     */
    private static enum ExtremeState {
        BLACK, EXTEND, TRANSPARENT;
        public ExtremeState getNext() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}

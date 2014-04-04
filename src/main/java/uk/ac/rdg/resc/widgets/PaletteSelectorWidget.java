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

    /* The horizontal gap to use in annotation flow layouts */
    private static final int GAP = 4;
    /* The width of the colourbar in the main palette window */
    private static final int COLOURBAR_WIDTH = 35;
    /* The border to use at the edges */
    private static final int BORDER = 10;

    private static final String OK_IMAGE = "images/ok_button.png";
    private static final String CLOSE_IMAGE = "images/close_button.png";

    private static final Color TRANSPARENT_COLOR = new Color(0, true);

    private PaletteSelectionHandler handler;
    private EdalConfigLayer parentLayer;

    private int lastItemWidth = -1;
    private Point lastDragPoint = null;
    private boolean paletteChanged = false;

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
    private Color noDataColour = TRANSPARENT_COLOR;

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
//    private Color lastNoDataColour = TRANSPARENT_COLOR;

    private ColourScheme colourScheme;

    //    private Map<ScreenAnnotation, Runnable> pickableItems;

    public PaletteSelectorWidget(RescWorldWindow wwd, EdalConfigLayer parentLayer) {
        super("", new Point());

        this.parentLayer = parentLayer;

        initialise();
    }

    private void initialise() {
        titleAnnotation = new ScreenAnnotation("Adjust Colour Scale", new Point());
        setDefaultButtonAttributes(titleAnnotation.getAttributes());
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
        setDefaultButtonAttributes(minLabel.getAttributes());
        minLabel.getAttributes().setSize(new Dimension(100, 0));

        opacityLabel = new ScreenAnnotation("Opacity\n100%", new Point());
        opacityLabel.setPickEnabled(true);
        setDefaultButtonAttributes(opacityLabel.getAttributes());
        opacityLabel.getAttributes().setSize(new Dimension(50, 0));

        maxLabel = new ScreenAnnotation("", new Point());
        maxLabel.setPickEnabled(true);
        setDefaultButtonAttributes(maxLabel.getAttributes());
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
        //        cancelButton.getAttributes().setInsets(new Insets(0, BORDER, 0, BORDER));
        cancelButton.setPickEnabled(true);

        okButton = new ImageAnnotation(OK_IMAGE);
        //        okButton.getAttributes().setInsets(new Insets(0, BORDER, 0, BORDER));
        okButton.setPickEnabled(true);

        //        setDefaultButtonAttributes(okButton.getAttributes());
        //        setDefaultButtonAttributes(cancelButton.getAttributes());

        buttonsPanel.addChild(cancelButton);
        buttonsPanel.addChild(okButton);

        //        this.addChild(mainPanel);

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

        ScreenAnnotation nColoursPanel = new ScreenAnnotation("", new Point());
        WidgetUtils.setupContainerAttributes(nColoursPanel.getAttributes());
        AnnotationFlowLayout nColoursPanelLayout = new AnnotationFlowLayout(AVKey.HORIZONTAL,
                AVKey.LEFT, GAP, 0);
        nColoursPanel.setLayout(nColoursPanelLayout);

        ScreenAnnotation nColoursTitle = new ScreenAnnotation("Colour bands", new Point());
        setDefaultButtonAttributes(nColoursTitle.getAttributes());
        nColoursPanel.addChild(nColoursTitle);
        for (int i : new int[] { 250, 100, 50, 20, 10, 5 }) {
            ScreenAnnotation nColoursLabel = new ScreenAnnotation(i + "", new Point());
            nColoursLabel.setPickEnabled(true);
            setDefaultButtonAttributes(nColoursLabel.getAttributes());
            nColoursLabel.getAttributes().setSize(new Dimension(50, 0));
            nColoursPanel.addChild(nColoursLabel);
            nColoursLabels.put(nColoursLabel, i);
        }

        paletteChooser.addChild(nColoursPanel);
        paletteChooser.addChild(paletteChooserPanel);
        populatePaletteImages();

        getAttributes().setBackgroundColor(new Color(0, 0, 0, 64));
        getAttributes().setCornerRadius(10);
        getAttributes().setOpacity(0.8);

        setMainSelector();
    }

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

    private void setMainSelector() {
        this.removeAllChildren();
        this.addChild(titleAnnotation);
        this.addChild(colourBarPanel);
        this.addChild(labelsPanel);
        this.addChild(buttonsPanel);
        paletteChooser.getAttributes().setVisible(false);
    }

    private void setPaletteChooser() {
        this.removeAllChildren();
        this.addChild(paletteChooser);
        paletteChooser.getAttributes().setVisible(true);
    }

    public void setPaletteSelectionHandler(PaletteSelectionHandler handler) {
        this.handler = handler;
    }

    public void setPaletteProperties(WmsLayerMetadata plottingMetadata, Color belowMinColour,
            Color aboveMaxColour, Color noDataColour) {
        setMinScale(plottingMetadata.getColorScaleRange().getLow());
        setMaxScale(plottingMetadata.getColorScaleRange().getHigh());

        this.paletteName = plottingMetadata.getPalette();
        this.logScaling = plottingMetadata.isLogScaling();
        this.numColourBands = plottingMetadata.getNumColorBands();
        this.belowMinColour = belowMinColour;
        this.aboveMaxColour = aboveMaxColour;
        this.noDataColour = noDataColour;

        setupColourScheme();
        setupColourBar(1);
    }

    private void setMinScale(float minScale) {
        if (minScale > maxScale) {
            minScale = maxScale;
        }
        this.minScale = minScale;
        minLabel.setText(FeatureInfoBalloon.NUMBER_3DP.format(this.minScale));
    }

    private void setMaxScale(float maxScale) {
        if (maxScale < minScale) {
            maxScale = minScale;
        }
        this.maxScale = maxScale;
        maxLabel.setText(FeatureInfoBalloon.NUMBER_3DP.format(this.maxScale));
    }

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

    private void setupColourScheme() {
        ColourScale colourScale = new ColourScale(Extents.newExtent(minScale, maxScale), logScaling);
        colourScheme = new SegmentColourScheme(colourScale, belowMinColour, aboveMaxColour,
                noDataColour, paletteName, numColourBands);
    }

    private void setupColourBar(int length) {
        colourBar.setImageSource(colourScheme.getScaleBar(length, COLOURBAR_WIDTH, 0.0f, false,
                false, null, null));
        setExtremeColour(true);
        setExtremeColour(false);
    }

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
                    parentLayer.hidePaletteSelector();
                    if (handler != null) {
                        handler.bulkChange(Extents.newExtent(lastMinScale, lastMaxScale),
                                lastPaletteName, lastBelowMinColour, lastAboveMaxColour,
                                lastLogScaling, lastNumColourBands);
                    }
                } else if (pickedObj == okButton) {
                    parentLayer.hidePaletteSelector();
                } else if (pickedObj == aboveMax) {
                    aboveMaxState = aboveMaxState.getNext();
                    setExtremeColour(false);
                    if (handler != null) {
                        handler.aboveMaxColourChanged(aboveMaxColour);
                    }
                } else if (pickedObj == belowMin) {
                    belowMinState = belowMinState.getNext();
                    setExtremeColour(true);
                    if (handler != null) {
                        handler.belowMinColourChanged(belowMinColour);
                    }
                } else if (pickedObj == colourBar) {
                    setPaletteChooser();
                } else if (paletteImages.containsKey(pickedObj)) {
                    paletteImages.get(pickedObj).run();
                    paletteChanged = true;
                    setMainSelector();
                } else if (nColoursLabels.containsKey(pickedObj)) {
                    setNumColourBands(nColoursLabels.get(pickedObj));
                    if (handler != null) {
                        handler.setNumColourBands(numColourBands);
                    }
                }
            } else if (event.isRollover()) {
            } else if (event.isDragEnd()) {
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
            if (!paletteChooserPanel.getChildren().isEmpty()) {
                int childWidth = itemWidth / paletteChooserPanel.getChildren().size() - GAP;
                for (Annotation a : paletteChooserPanel.getChildren()) {
                    a.getAttributes().setSize(new Dimension(childWidth, 0));
                    a.getAttributes().setInsets(new Insets(0, childWidth / 2, 0, childWidth / 2));
                }
            }
        } else {
            if ((paletteChanged || lastItemWidth != itemWidth) && paletteName != null) {
                lastItemWidth = itemWidth;
                int colourBarLength = itemWidth - 2 * COLOURBAR_WIDTH;
                setupColourBar(colourBarLength);
            }

            titleAnnotation.getAttributes().setSize(new Dimension(itemWidth, 0));
            colourBarPanel.getAttributes().setSize(new Dimension(itemWidth, 0));

            labelsPanel.getAttributes().setSize(new Dimension(itemWidth, 0));

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

    private static void setDefaultButtonAttributes(AnnotationAttributes attrs) {
        attrs.setAdjustWidthToText(AVKey.SIZE_FIXED);
        attrs.setTextAlign(AVKey.CENTER);
        attrs.setBackgroundColor(Color.black);
        attrs.setTextColor(Color.white);
        attrs.setCornerRadius(10);
        attrs.setBorderWidth(1);
        attrs.setInsets(new Insets(10, 0, 10, 0));
    }

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

    private static enum ExtremeState {
        BLACK, EXTEND, TRANSPARENT;
        public ExtremeState getNext() {
            return values()[(ordinal() + 1) % values().length];
        }
    }
}

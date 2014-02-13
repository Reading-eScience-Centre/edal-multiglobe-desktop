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

package uk.ac.rdg.resc.old;

import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.util.tree.BasicFrameAttributes;
import gov.nasa.worldwind.util.tree.BasicTree;
import gov.nasa.worldwind.util.tree.BasicTreeAttributes;
import gov.nasa.worldwind.util.tree.BasicTreeLayout;
import gov.nasa.worldwind.util.tree.TreeLayout;
import gov.nasa.worldwind.util.tree.TreeModel;

public class EdalTree extends BasicTree {
    /**
     * The default screen location: 20x140 pixels from the upper left screen
     * corner.
     */
    protected static final Offset DEFAULT_OFFSET = new Offset(20d, 140d, AVKey.PIXELS,
            AVKey.INSET_PIXELS);
    /** The default frame image. Appears to the left of the frame title. */
    protected static final String DEFAULT_FRAME_IMAGE = "images/layer-manager-64x64.png";

    public EdalTree() {
        super();

        init();
    }

    private void init() {
        super.setModel(new EdalTreeModel());
        setLayout(createTreeLayout(null));
        expandPath(getModel().getRoot().getPath());
    }

    @Override
    public EdalTreeModel getModel() {
        return (EdalTreeModel) super.getModel();
    }
    
    @Override
    public void setModel(TreeModel model) {
//        super.setModel(model);
        getModel().setRoot(model.getRoot());
        expandPath(model.getRoot().getPath());
    }

    /**
     * Returns a new <code>TreeLayout</code> suitable for displaying the layer
     * tree on a <code>WorldWindow</code>. If the <code>offset</code> is
     * <code>null</code> this the default value.
     * 
     * @param offset
     *            the screen location of this tree's upper left corner, or
     *            <code>null</code> to use the default.
     * 
     * @return new <code>TreeLayout</code>.
     */
    protected TreeLayout createTreeLayout(Offset offset) {
        if (offset == null)
            offset = DEFAULT_OFFSET;

        EdalTreeLayout layout = new EdalTreeLayout(this, offset);
        layout.getFrame().setFrameTitle("Data layers");
        layout.getFrame().setIconImageSource(DEFAULT_FRAME_IMAGE);
//        layout.setDrawSelectedSymbol(false);
        layout.setDrawNodeStateSymbol(true);

        BasicTreeAttributes attributes = new BasicTreeAttributes();
        attributes.setRootVisible(false);
        layout.setAttributes(attributes);

        BasicFrameAttributes frameAttributes = new BasicFrameAttributes();
        frameAttributes.setBackgroundOpacity(0.7);
        layout.getFrame().setAttributes(frameAttributes);

        BasicTreeAttributes highlightAttributes = new BasicTreeAttributes(attributes);
        layout.setHighlightAttributes(highlightAttributes);

        BasicFrameAttributes highlightFrameAttributes = new BasicFrameAttributes(frameAttributes);
        highlightFrameAttributes.setForegroundOpacity(0.8);
        highlightFrameAttributes.setBackgroundOpacity(0.8);
        layout.getFrame().setHighlightAttributes(highlightFrameAttributes);

        return layout;
    }
}

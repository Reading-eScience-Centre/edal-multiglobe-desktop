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

import gov.nasa.worldwind.event.SelectEvent;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.render.Offset;
import gov.nasa.worldwind.util.HotSpot;
import gov.nasa.worldwind.util.tree.BasicTreeLayout;
import gov.nasa.worldwind.util.tree.Tree;
import gov.nasa.worldwind.util.tree.TreeHotSpot;
import gov.nasa.worldwind.util.tree.TreeNode;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;

import javax.media.opengl.GL2;

/**
 * Extends BasicTreeLayout to create tree which is expandable but works like
 * radio buttons
 * 
 * @author Guy
 */
public class EdalTreeLayout extends BasicTreeLayout {

    public EdalTreeLayout(Tree tree, Offset screenLocation) {
        super(tree, screenLocation);
        /*
         * TODO set the required layout here
         */
    }

    @Override
    protected void toggleNodeSelection(TreeNode node) {
        if (!node.isEnabled()) {
            return;
        }
        if (node.isSelected()) {
            return;
        }

        TreeNode rootNode = tree.getModel().getRoot();
        deselectNodes(rootNode.getChildren());
        node.setSelected(true);
    }

    private void deselectNodes(Iterable<TreeNode> children) {
        for (TreeNode node : children) {
            node.setSelected(false);
            deselectNodes(node.getChildren());
        }
    }

    //    @Override
    //    protected void drawFilledCheckboxes(DrawContext dc, Iterable<NodeLayout> nodes) {
    //        super.drawCheckmarks(dc, nodes);
    //    }

    @Override
    protected void drawCheckboxes(DrawContext dc, Iterable<NodeLayout> nodes) {
        // The check boxes are drawn in three passes:
        // 1) Draw filled background for partially selected nodes
        // 2) Draw check marks for selected nodes
        // 3) Draw checkbox outlines

        GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

        Dimension symbolSize;

        if (!dc.isPickingMode()) {
            this.drawFilledCheckboxes(dc, nodes); // Draw filled boxes for partially selected nodes
            this.drawCheckmarks(dc, nodes); // Draw check marks for selected nodes

            symbolSize = this.getSelectedSymbolSize();
        } else {
            // Make the pickable area of the checkbox a little bigger than the actual box so that it is easier to hit.
            symbolSize = new Dimension(this.getSelectedSymbolSize().width
                    + this.getActiveAttributes().getIconSpace(), this.lineHeight
                    + this.getActiveAttributes().getRowSpacing());
        }

        // In picking mode all of the boxes can be drawn as filled quads. Otherwise, each box is drawn as a
        // separate line loop
        if (dc.isPickingMode()) {
            gl.glBegin(GL2.GL_QUADS);
        }
        try {
            for (NodeLayout layoutO : nodes) {
                EdalNodeLayout layout = (EdalNodeLayout) layoutO;
                int vertAdjust = layout.getBounds().height - symbolSize.height
                        - (this.lineHeight - symbolSize.height) / 2;

                int x = layout.getDrawPoint().x;
                int y = layout.getDrawPoint().y + vertAdjust;
                int width = symbolSize.width*2;

                if (!dc.isPickingMode()) {
                    // Draw a hollow box uses a line loop
                    gl.glBegin(GL2.GL_LINE_LOOP);
                    try {
                        gl.glVertex2f(x + width, y + symbolSize.height + 0.5f);
                        gl.glVertex2f(x, y + symbolSize.height + 0.5f);
                        gl.glVertex2f(x, y);
                        gl.glVertex2f(x + width, y + 0.5f);
                    } finally {
                        gl.glEnd();
                    }
                }
                // Otherwise draw a filled quad
                else {
                    Color color = dc.getUniquePickColor();
                    int colorCode = color.getRGB();
                    this.pickSupport.addPickableObject(colorCode,
                            this.createSelectControl(layout.getNode()));
                    gl.glColor3ub((byte) color.getRed(), (byte) color.getGreen(),
                            (byte) color.getBlue());

                    // If the node does not have a triangle to the left of the checkbox, make the checkbox pickable
                    // area stretch all the way to the frame on the left hand side, since this is otherwise dead space.
                    if (layout.getNode().isLeaf() || !this.isDrawNodeStateSymbol()) {
                        width = x - this.screenLocation.x + symbolSize.width;
                        x = this.screenLocation.x;
                    }

                    gl.glVertex2f(x + width, y + symbolSize.height);
                    gl.glVertex2f(x, y + symbolSize.height);
                    gl.glVertex2f(x, y);
                    gl.glVertex2f(x + width, y);
                }

                layout.getDrawPoint().x += symbolSize.width + this.getActiveAttributes().getIconSpace();
            }
        } finally {
            if (dc.isPickingMode()) {
                gl.glEnd(); // Quads
            }
        }
    }
    
    public static class EdalNodeLayout extends NodeLayout {
        protected EdalNodeLayout(TreeNode node) {
            super(node);
        }
        
        protected Rectangle getBounds() {
            return bounds;
        }
        
        protected Point getDrawPoint() {
            return drawPoint;
        }
        
        protected TreeNode getNode() {
            return node;
        }
    }

    @Override
    protected HotSpot createSelectControl(final TreeNode node) {
        return new TreeHotSpot(this.getFrame()) {
            @Override
            public void selected(SelectEvent event) {
                if (event == null || this.isConsumed(event))
                    return;

                if (event.isLeftClick() || event.isLeftDoubleClick()) {
                    toggleNodeSelection(node);
                    event.consume();
                } else {
                    super.selected(event);
                }
            }

            @Override
            public void mouseClicked(MouseEvent event) {
                super.mouseClicked(event);
                System.out.println("Mouse clicked: " + event.getX() + "," + event.getY());
            }
        };
    }
}

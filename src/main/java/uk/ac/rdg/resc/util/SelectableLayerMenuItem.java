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

package uk.ac.rdg.resc.util;

import java.util.AbstractList;
import java.util.List;

import uk.ac.rdg.resc.EdalDataLayer;
import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;
import uk.ac.rdg.resc.widgets.LayerSelectorWidget;

/**
 * An extension of {@link LayerMenuItem} which allows indiviual items to be set
 * as selected or not. This is used in {@link LayerSelectorWidget} to allow for
 * multiple layers to be selected.
 * 
 * TODO This class is currently not used, but will be useful once we implement
 * multiple {@link EdalDataLayer}s on a single globe
 * 
 * @author Guy Griffiths
 */
public class SelectableLayerMenuItem extends LayerMenuItem {
    private static final long serialVersionUID = 1L;
    private boolean selected = false;
    private boolean gridded = false;

    public SelectableLayerMenuItem(LayerMenuItem item) {
        super(item.getTitle(), item.getDescription(), item.getId(), item.isPlottable(), item
                .getWmsUrl());
        for (LayerMenuItem child : item.getChildren()) {
            addChildItem(new SelectableLayerMenuItem(child));
        }
    }

    public SelectableLayerMenuItem(String title, String id, boolean plottable) {
        super(title, id, plottable);
    }

    public SelectableLayerMenuItem(String title, String description, String id, boolean plottable,
            String wmsUrl) {
        super(title, description, id, plottable, wmsUrl);
    }

    @Override
    public void addChildItem(LayerMenuItem item) {
        if (item instanceof SelectableLayerMenuItem) {
            super.addChildItem(item);
        } else {
            super.addChildItem(new SelectableLayerMenuItem(item));
        }
    }

    @Override
    public List<? extends SelectableLayerMenuItem> getChildren() {
        return new AbstractList<SelectableLayerMenuItem>() {
            @Override
            public SelectableLayerMenuItem get(int index) {
                return (SelectableLayerMenuItem) SelectableLayerMenuItem.super.getChildren().get(
                        index);
            }

            @Override
            public int size() {
                if (SelectableLayerMenuItem.super.getChildren() == null) {
                    return 0;
                }
                return SelectableLayerMenuItem.super.getChildren().size();
            }
        };
    }

    @Override
    public SelectableLayerMenuItem getParent() {
        return (SelectableLayerMenuItem) super.getParent();
    }

    /**
     * @return Whether or not this item is currently selected
     */
    public boolean isSelected() {
        return selected;
    }

    /**
     * Sets the selected state of this menu item
     * 
     * @param selected
     *            Whether this item is selected or not
     */
    public void setSelected(boolean selected) {
        this.selected = selected;
    }

    /**
     * @return Whether or not this item represents a gridded field
     */
    public boolean isGridded() {
        return gridded;
    }

    /**
     * Sets the gridded state of this menu item
     * 
     * @param selected
     *            Whether this item is gridded or not
     */
    public void setGridded(boolean gridded) {
        this.gridded = gridded;
    }
}

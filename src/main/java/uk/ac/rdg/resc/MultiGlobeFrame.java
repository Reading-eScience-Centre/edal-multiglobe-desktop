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
import java.awt.GridLayout;
import java.io.IOException;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import uk.ac.rdg.resc.edal.exceptions.EdalException;

/**
 * A {@link JPanel} which can hold multiple {@link RescWorldWindow}s and has
 * methods for creating and removing them
 * 
 * @author Guy Griffiths
 */
public class MultiGlobeFrame extends JPanel {
    private static final long serialVersionUID = 1L;

    /**
     * A list of {@link RescWorldWindow}s contained within this
     * {@link MultiGlobeFrame}
     */
    private List<RescWorldWindow> panels = new ArrayList<>();
    /*
     * Start the number of rows and columns at zero
     */
    private int rows = 0;
    private int columns = 0;

    /**
     * A single reference to a {@link VideoWallCatalogue}, to be shared between
     * every globe panel
     */
    private VideoWallCatalogue featureCatalogue;

    public MultiGlobeFrame(VideoWallCatalogue featureCatalogue) throws IOException, EdalException {
        this.featureCatalogue = featureCatalogue;

        /*
         * This will be the colour of the borders between panels
         */
        setBackground(Color.lightGray);

        /*
         * Initialise the frame to contain a single globe
         */
        setShape(1, 1);
    }

    /**
     * @return A list containing all {@link RescModel}s which are displayed (one
     *         per {@link RescWorldWindow})
     */
    public List<RescModel> getAllModels() {
        return new AbstractList<RescModel>() {
            @Override
            public RescModel get(int index) {
                return panels.get(index).getModel();
            }

            @Override
            public int size() {
                return panels.size();
            }
        };
    }

    /**
     * Adds a row to the bottom of the frame, creating as many new
     * {@link RescWorldWindow}s as are required
     */
    public void addRow() {
        setShape(rows + 1, columns);
    }

    /**
     * Removes the bottom row of globes. Any data in these globes will be
     * removed
     */
    public void removeRow() {
        if (rows > 1) {
            setShape(rows - 1, columns);
        }
    }

    /**
     * Adds a column to the right of the frame, creating as many new
     * {@link RescWorldWindow}s as are required
     */
    public void addColumn() {
        setShape(rows, columns + 1);
    }

    /**
     * Removes the rightmost column of globes. Any data in these globes will be
     * removed
     */
    public void removeColumn() {
        if (columns > 1) {
            setShape(rows, columns - 1);
        }
    }

    /**
     * Sets the number of rows and columns
     * 
     * @param rows
     *            The desired number of rows
     * @param columns
     *            The desired number of columns
     */
    private void setShape(int rows, int columns) {
        /*
         * Remove all necessary rows
         */
        while (this.rows > rows) {
            for (int i = this.columns - 1; i >= 0; i--) {
                panels.remove(getIndex(this.rows - 1, i));
                remove(getIndex(this.rows - 1, i));
            }
            this.rows--;
        }

        /*
         * Remove all necessary columns
         */
        while (this.columns > columns) {
            for (int i = this.rows - 1; i >= 0; i--) {
                panels.remove(getIndex(i, this.columns - 1));
                remove(getIndex(i, this.columns - 1));
            }
            this.columns--;
        }

        /*
         * Add any required rows
         */
        while (this.rows < rows) {
            /*
             * Add one panel at the bottom of each column
             */
            for (int i = 0; i < this.columns; i++) {
                RescWorldWindow newPanel = getNewPanel();
                panels.add(newPanel);
                add(newPanel);
            }
            this.rows++;
        }

        /*
         * Add any required columns
         */
        while (this.columns < columns) {
            /*
             * Add from the bottom up, otherwise the indexes change
             */
            for (int i = this.rows - 1; i >= 0; i--) {
                RescWorldWindow newPanel = getNewPanel();
                panels.add(getIndex(i, this.columns), newPanel);
                add(newPanel, getIndex(i, this.columns));
            }
            this.columns++;
        }

        /*
         * Now set the layout
         */
        GridLayout gridLayout = new GridLayout(rows, columns, 2, 2);
        setLayout(gridLayout);

        /*
         * Redraw all panels, since their size will have changed
         */
        for (RescWorldWindow panel : panels) {
            panel.redraw();
        }

        /*
         * validate() needs to be called after add/remove
         */
        this.validate();
    }

    /**
     * Convenience method
     * 
     * @return A new {@link RescWorldWindow}
     */
    private RescWorldWindow getNewPanel() {
        RescWorldWindow wwd = new RescWorldWindow();
        wwd.setModel(new RescModel(featureCatalogue, wwd, this));
        wwd.setVisible(true);
        for (int i = 0; i < panels.size(); i++) {
            /*
             * Link this view with all other RescWorldWindows which have been
             * added
             */
            panels.get(i).getView().addLinkedView(wwd.getView());
        }
        return wwd;
    }

    /**
     * Gets the index in the panels list of a given panel
     * 
     * @param row
     *            The row of the panel
     * @param column
     *            The column of the panel
     * @return The index in the list
     */
    private int getIndex(int row, int column) {
        return row * columns + column;
    }

}

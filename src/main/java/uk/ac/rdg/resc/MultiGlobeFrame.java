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

import gov.nasa.worldwind.View;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;
import gov.nasa.worldwind.view.orbit.BasicOrbitView;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JPanel;

import uk.ac.rdg.resc.edal.exceptions.EdalException;

public class MultiGlobeFrame extends JPanel {
    private static final long serialVersionUID = 1L;

    private List<RescWorldWindow> panels = new ArrayList<>();
    private int rows = 2;
    private int columns = 2;

    private int panelWidth = 500;
    private int panelHeight = 500;

    private VideoWallCatalogue featureCatalogue;
    private View view = new BasicOrbitView();

    public MultiGlobeFrame(VideoWallCatalogue featureCatalogue) throws IOException, EdalException {
        /*
         * Set the location of the config file.
         * 
         * This needs to be done before any WorldWindow objects are created.
         * 
         * TODO Is it OK here, or might it need to go higher up the chain?
         */

        this.featureCatalogue = featureCatalogue;
        
        setBackground(Color.lightGray);
        
        setShape(1,1);
    }
    
    public void addLayers() {
        setShape(2,2);
//        getPanel(0, 1).setOppositeView();
//        setFlat(true, 1, 0);
//        setFlat(true, 1, 1);
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public RescModel getModel(int row, int column) {
        return (RescModel) getPanel(row, column).getModel();
    }

//    public void setData(int row, int column, String field) {
//        getModel(row, column).setDataLayer(field);
//    }

    public void setFlat(boolean flat, int row, int column) {
        getModel(row, column).setFlat(flat);
    }

    public void toggleFlat(int row, int column) {
        RescModel model = getModel(row, column);
        model.setFlat(!model.isFlat());
    }

    public void setShape(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;
        reJig();
    }

    public RescWorldWindow getPanel(int row, int column) {
        if (row >= rows || column >= columns) {
            throw new ArrayIndexOutOfBoundsException("Cannot get panel at " + row + "," + column
                    + ".  This frame is " + rows + "x" + columns);
        }
        return panels.get(getIndex(row, column));
    }

    private int getIndex(int row, int column) {
        return row * rows + column;
    }

    public void reJig() {
        while (panels.size() > rows * columns) {
            panels.remove(panels.size() - 1);
            remove(getComponentCount() - 1);
        }
        GridLayout gridLayout = new GridLayout(rows, columns, 2, 2);
        setLayout(gridLayout);
        
        while (panels.size() < rows * columns) {
            RescWorldWindow wwd = new RescWorldWindow(new LinkedView());
            wwd.setPreferredSize(new java.awt.Dimension(panelWidth, panelHeight));
            wwd.setModel(new RescModel(featureCatalogue, wwd));
            wwd.setVisible(true);
            panels.add(wwd);
        }
        for (RescWorldWindow panel : panels) {
            for (RescWorldWindow panelToSync : panels) {
                panel.getLinkedView().addLinkedView(panelToSync.getLinkedView());
            }
            panel.setSize(panelWidth, panelHeight);
            add(panel);
        }
        this.validate();
    }
}

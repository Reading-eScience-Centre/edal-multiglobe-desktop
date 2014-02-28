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

public class MultiGlobeFrame extends JPanel {
    private static final long serialVersionUID = 1L;

    private List<RescWorldWindow> panels = new ArrayList<>();
    private int rows;
    private int columns;

    private VideoWallCatalogue featureCatalogue;

    public MultiGlobeFrame(VideoWallCatalogue featureCatalogue) throws IOException, EdalException {
        this.featureCatalogue = featureCatalogue;

        setBackground(Color.lightGray);

        setShape(1, 1);
    }

    public void addRow() {
        setShape(rows + 1, columns);
    }

    public void removeRow() {
        if (rows > 1) {
            setShape(rows - 1, columns);
        }
    }

    public void addColumn() {
        setShape(rows, columns + 1);
    }

    public void removeColumn() {
        if (columns > 1) {
            setShape(rows, columns - 1);
        }
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public RescModel getModel(int row, int column) {
        return getPanel(row, column).getModel();
    }

    public void setShape(int rows, int columns) {
        this.rows = rows;
        this.columns = columns;

        while (panels.size() > rows * columns) {
            panels.remove(panels.size() - 1);
            remove(getComponentCount() - 1);
        }
        GridLayout gridLayout = new GridLayout(rows, columns, 2, 2);
        setLayout(gridLayout);
        
        while (panels.size() < rows * columns) {
            RescWorldWindow wwd = new RescWorldWindow(new LinkedView());
            wwd.setModel(new RescModel(featureCatalogue, wwd, this));
            wwd.setVisible(true);
            for (int i = 0; i < panels.size(); i++) {
                panels.get(i).getLinkedView().addLinkedView(wwd.getLinkedView());
            }
            panels.add(wwd);
            add(wwd);
        }
        this.validate();
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
}

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

import javafx.event.EventHandler;
import javafx.scene.Node;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TouchEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

/**
 * A {@link GridPane} which corresponds to {@link MultiGlobeFrame} and handles
 * JFX input.
 * 
 * This class is not currently used and was part of an experiment to try and
 * overlay an invisible JavaFX component over the visible AWT component,
 * allowing JFX multitouch alongside a GLCanvas (since GLCanvas performs better
 * than GLJPanel)
 * 
 * @author Guy Griffiths
 */
class MultiGlobeInputFrame extends VBox {
    private static final ColumnConstraints CC = new ColumnConstraints();
    private static final RowConstraints RC = new RowConstraints();
    static {
        CC.setHgrow(Priority.ALWAYS);
        RC.setVgrow(Priority.ALWAYS);
    }

    /*
     * Start the number of rows and columns at zero
     */
    private int rows = 0;
    private int columns = 1;

    public MultiGlobeInputFrame() {
        /*
         * 
         */
        setStyle("-fx-background-color: rgba(0, 0, 255, 100);");

        /*
         */
        setSpacing(MultiGlobeFrame.GAP);
        //        setVgap(MultiGlobeFrame.GAP);

        setMaxHeight(Double.MAX_VALUE);
        setMaxWidth(Double.MAX_VALUE);
        System.out.println("Input frame initialised");

        //        Rectangle Rectangle = new Rectangle("test");
        //        Rectangle.setMaxWidth(Double.MAX_VALUE);
        //        getChildren().add(Rectangle);
        //        VBox.setVgrow(Rectangle, Priority.ALWAYS);

        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);

        setShape(2, 2);
    }

    /**
     * Sets the number of rows and columns
     * 
     * @param rows
     *            The desired number of rows
     * @param columns
     *            The desired number of columns
     */
    void setShape(int rows, int columns) {
        /*
         * Remove all necessary rows
         */
        while (this.rows > rows) {
            for (int i = this.columns - 1; i >= 0; i--) {
                getChildren().remove(this.rows - 1);
            }
            this.rows--;
        }

        /*
         * Remove all necessary columns
         */
        while (this.columns > columns) {
            for (int i = this.rows - 1; i >= 0; i--) {
                ((HBox) getChildren().get(this.rows - 1)).getChildren().remove(this.columns - 1);
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
            HBox row = new HBox();
            row.setSpacing(MultiGlobeFrame.GAP);
            for (int i = 0; i < this.columns; i++) {
                System.out.println("adding column to row");
                Node inputNode = getInputNode();
                inputNode.setStyle("-fx-background-color: rgba(255, 0, 0, 100);");

                row.getChildren().add(inputNode);
                HBox.setHgrow(inputNode, Priority.ALWAYS);
            }
            row.setMaxWidth(Double.MAX_VALUE);
            row.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(row, Priority.ALWAYS);
            getChildren().add(row);
            this.rows++;
        }

        /*
         * Add any required columns
         */
        while (this.columns < columns) {
            /*
             * Add from the bottom up, otherwise the indices change
             */
            for (int i = this.rows - 1; i >= 0; i--) {
                HBox currentRow = (HBox) getChildren().get(i);
                System.out.println("adding row to column");
                Node inputNode = getInputNode();
                inputNode.setStyle("-fx-background-color: rgba(0, 255, 0, 100);");
                currentRow.getChildren().add(inputNode);
                HBox.setHgrow(inputNode, Priority.ALWAYS);
            }
            //            getColumnConstraints().add(CC);
            this.columns++;
        }
    }

    private Node getInputNode() {
        //        Rectangle inputNode = new Rectangle();
        Rectangle inputNode = new Rectangle();
        inputNode.setOnTouchPressed(new EventHandler<TouchEvent>() {
            @Override
            public void handle(TouchEvent arg0) {
                System.out.println("Got touch event!");
            }
        });
        inputNode.setOnMouseDragged(new EventHandler<MouseEvent>() {
            @Override
            public void handle(MouseEvent arg0) {
                System.out.println("Got mouse event!");
            }
        });
        //        inputNode.setMaxWidth(Double.MAX_VALUE);
        //        inputNode.setMaxHeight(Double.MAX_VALUE);
        inputNode.setWidth(200);
        inputNode.setHeight(200);
        return inputNode;
    }
}

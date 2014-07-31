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

import gov.nasa.worldwind.util.Logging;

import java.io.File;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.FileChooser.ExtensionFilter;
import javafx.stage.Modality;
import javafx.stage.Stage;

import javax.xml.bind.JAXBException;

import uk.ac.rdg.resc.edal.ncwms.config.NcwmsVariable;
import uk.ac.rdg.resc.edal.util.Extents;
import uk.ac.rdg.resc.edal.wms.exceptions.EdalLayerNotFoundException;
import uk.ac.rdg.resc.persist.VideoWallContents;
import uk.ac.rdg.resc.persist.VideoWallLayout;
import uk.ac.rdg.resc.persist.VideoWallRow;

/**
 * Options window which implements load, save, and exit
 *
 * @author Guy Griffiths
 */
public class OptionsWindow extends Stage {
    private static final int WIDTH = 500;

    private Button load;
    private Button save;
    private Button exit;

    public OptionsWindow(Stage parent, final MultiGlobeFrame frame) {
        load = new Button("Load Layout");
        load.setPrefWidth(WIDTH);
        load.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Choose file to load");
                File loadFile = chooser.showOpenDialog(OptionsWindow.this);
                try {
                    VideoWallLayout layout = VideoWallLayout.fromFile(loadFile);
                    List<VideoWallRow> rows = layout.getRows();
                    int nRows = rows.size();
                    int nCols = rows.get(0).getColumns().size();
                    frame.setShape(nRows, nCols);
                    for (int i = 0; i < nRows; i++) {
                        List<VideoWallContents> columns = rows.get(i).getColumns();
                        for (int j = 0; j < nCols; j++) {
                            VideoWallContents contents = columns.get(j);
                            RescModel model = frame.getModelAt(i, j);
                            NcwmsVariable plottingMetadata = contents.getPlottingMetadata();
                            model.setDataLayer(plottingMetadata.getId());
                            /*
                             * Set the layer plotting properties
                             */
                            EdalDataLayer dataLayer = model.getDataLayer();
                            dataLayer.bulkChange(plottingMetadata.getColorScaleRange(),
                                    plottingMetadata.getPalette(),
                                    plottingMetadata.getBelowMinColour(),
                                    plottingMetadata.getAboveMaxColour(),
                                    plottingMetadata.isLogScaling(),
                                    plottingMetadata.getNumColorBands());
                            /*
                             * Make sure that the palette widget matches
                             */
                            model.getConfigLayer().getPaletteSelector()
                                    .setPaletteProperties(plottingMetadata);
                            /*
                             * TODO persist elevation range...
                             */
                            if (contents.getElevation() != null) {
                                dataLayer.setDataElevation(
                                        contents.getElevation(),
                                        Extents.newExtent(contents.getElevation(),
                                                contents.getElevation()));
                            }
                            /*
                             * TODO persist time range...
                             */
                            if (contents.getTime() != null) {
                                dataLayer.setTime(contents.getTime(),
                                        Extents.newExtent(contents.getTime(), contents.getTime()));
                            }
                            model.getConfigLayer().setLinkState(contents.getLinkedViewState());
                            model.setFlat(contents.isFlatMap());
                            model.setProjection(contents.getMapProjection());
                            dataLayer.setOpacity(contents.getOpacity());
                            LinkedView view = model.getWorldWindow().getView();
                            /*
                             * This triggers the linked views to be set
                             */
                            view.setCenterPosition(view.getCenterPosition());
                        }
                    }
                } catch (JAXBException e) {
                    String msg = Logging.getMessage("resc.SettingsLoadProblem");
                    Logging.logger().severe(msg);
                } catch (EdalLayerNotFoundException e) {
                    String msg = Logging.getMessage("resc.SettingsLoadProblem");
                    Logging.logger().severe(msg);
                } finally {
                    OptionsWindow.this.close();
                    ;
                }
            }
        });

        save = new Button("Save Layout");
        save.setPrefWidth(WIDTH);
        save.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Choose file to save to");
                chooser.setSelectedExtensionFilter(new ExtensionFilter("XML Files", "xml"));
                File saveFile = chooser.showSaveDialog(OptionsWindow.this);
                VideoWallLayout layout = VideoWallLayout.fromMultiGlobeFrame(frame);
                try {
                    VideoWallLayout.toFile(layout, saveFile);
                } catch (JAXBException e) {
                    String msg = Logging.getMessage("resc.SettingsSaveProblem");
                    Logging.logger().severe(msg);
                } finally {
                    OptionsWindow.this.close();
                }
            }
        });

        exit = new Button("Exit");
        exit.setPrefWidth(WIDTH);
        exit.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                parent.close();
                OptionsWindow.this.close();
            }
        });

        VBox box = new VBox(load, save, exit);
        box.setPrefWidth(WIDTH);
        Scene scene = new Scene(box);
        setScene(scene);

        initModality(Modality.APPLICATION_MODAL);
        initOwner(parent);
    }
}

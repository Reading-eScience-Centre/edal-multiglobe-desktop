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

import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;

import java.io.IOException;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;

import javax.xml.bind.JAXBException;

import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;

/**
 * Main class for the multi-globe video wall software.
 * 
 * @author Guy Griffiths
 */
public class VideoWall extends Application {
    private static final int BUTTON_WIDTH=30;
    
	private VideoWallCatalogue datasetLoader;
    private MultiGlobeFrame globePanels;
    private VBox addRemoveRowPane;
    private HBox addRemoveColumnPane;
    private BorderPane mainPane;

    public VideoWall() throws IOException, EdalException, JAXBException {
    }
    
    /**
     * Set up necessary components and loads some buttons into the main panel
     * for adding/removing globe panels
     */
    public void start(Stage primaryStage) throws Exception {
    	Configuration.setValue(AVKey.VIEW_CLASS_NAME, LinkedView.class.getName());
    	
    	/*
    	 * Set the default data reader. This means that we don't need to specify
    	 * a dataset factory for cases where we are reading gridded NetCDF data
    	 * (the majority)
    	 */
    	DatasetFactory.setDefaultDatasetFactoryClass(CdmGridDatasetFactory.class);

        /*
         * Initialise the dataset catalogue
         */
        datasetLoader = new VideoWallCatalogue();
        /*
         * Create the main frame which will hold each of the globe panels
         */
        globePanels = new MultiGlobeFrame(datasetLoader);

        /*
         * Create and wire up the panel for adding/removing rows
         */
        addRemoveRowPane = new VBox();

        /* The add row button */
        Button addRowButton = new Button("+");
        addRowButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				globePanels.addRow();
			}
		});
        /*
         * TODO implement styling using CSS
         */
        addRowButton.setStyle("-fx-base: #000000;");
        addRowButton.setTextFill(Color.LIGHTGRAY);
        addRowButton.setMaxHeight(BUTTON_WIDTH);
        addRowButton.setMinHeight(BUTTON_WIDTH);
        addRowButton.setPrefHeight(BUTTON_WIDTH);
        addRowButton.setMaxWidth(Double.MAX_VALUE);
        
        /* The remove row button */
        Button removeRowButton = new Button("-");
        removeRowButton.setOnAction(new EventHandler<ActionEvent>() {
        	@Override
        	public void handle(ActionEvent e) {
        		globePanels.removeRow();
        	}
        });
        /*
         * TODO implement styling using CSS
         */
        removeRowButton.setStyle("-fx-base: #000000;");
        removeRowButton.setTextFill(Color.LIGHTGRAY);
        removeRowButton.setMaxHeight(BUTTON_WIDTH);
        removeRowButton.setMinHeight(BUTTON_WIDTH);
        removeRowButton.setPrefHeight(BUTTON_WIDTH);
        removeRowButton.setMaxWidth(Double.MAX_VALUE);

        addRemoveRowPane.getChildren().addAll(removeRowButton, addRowButton);

        /*
         * Create and wire up the panel for adding/removing columns
         */
        addRemoveColumnPane = new HBox();
        
        /* The add column button */
        Button addColumnButton = new Button("+");
        addColumnButton.setOnAction(new EventHandler<ActionEvent>() {
			@Override
			public void handle(ActionEvent e) {
				globePanels.addColumn();
			}
		});
        /*
         * TODO implement styling using CSS
         */
        addColumnButton.setStyle("-fx-base: #000000;");
        addColumnButton.setTextFill(Color.LIGHTGRAY);
        addColumnButton.setMaxWidth(BUTTON_WIDTH);
        addColumnButton.setMinWidth(BUTTON_WIDTH);
        addColumnButton.setPrefWidth(BUTTON_WIDTH);
        addColumnButton.setMaxHeight(Double.MAX_VALUE);
        
        /* The remove column button */
        Button removeColumnButton = new Button("-");
        removeColumnButton.setOnAction(new EventHandler<ActionEvent>() {
        	@Override
        	public void handle(ActionEvent e) {
        		globePanels.removeColumn();
        	}
        });
        /*
         * TODO implement styling using CSS
         */
        removeColumnButton.setStyle("-fx-base: #000000;");
        removeColumnButton.setTextFill(Color.LIGHTGRAY);
        removeColumnButton.setMaxWidth(BUTTON_WIDTH);
        removeColumnButton.setMinWidth(BUTTON_WIDTH);
        removeColumnButton.setPrefWidth(BUTTON_WIDTH);
        removeColumnButton.setMaxHeight(Double.MAX_VALUE);

        addRemoveColumnPane.getChildren().addAll(removeColumnButton, addColumnButton);

        /*
         * Now set the main window layout with the main globe panel and the
         * buttons
         */
        mainPane = new BorderPane();
        mainPane.setRight(addRemoveColumnPane);
        mainPane.setBottom(addRemoveRowPane);
//        SwingNode mainNode = new SwingNode();
        
//        mainNode.setContent(globePanels);
//        mainPane.setCenter(mainNode);
        
//        globePanels.setMaxHeight(Double.MAX_VALUE);
//        globePanels.setMaxWidth(Double.MAX_VALUE);
        
        mainPane.setCenter(globePanels);
        
        primaryStage.setScene(new Scene(mainPane, 1280, 720));
        
        boolean fullscreen = Configuration.getBooleanValue("uk.ac.rdg.resc.edal.multiglobe.Fullscreen", true);
        int screenNumber = Configuration.getIntegerValue("uk.ac.rdg.resc.edal.multiglobe.ScreenNumber", 0);
    	if(fullscreen) {
    	    int primaryMon = 0;
    	    Screen primary = Screen.getPrimary();
    	    for(int i = 0; i < Screen.getScreens().size(); i++){
    	        if(Screen.getScreens().get(i).equals(primary)){
                    primaryMon = i;
    	            System.out.println("primary: " + i);
    	            break;
    	        }
    	    }

    	    if(primaryMon == screenNumber) {
    	        primaryStage.setFullScreen(fullscreen);
    	    } else {
    	        for(int i=0; i<Screen.getScreens().size(); i++) {
    	            if(i==screenNumber) {
    	                Screen screen = Screen.getScreens().get(i);
    	                primaryStage.setX(screen.getVisualBounds().getMinX());
    	                primaryStage.setY(screen.getVisualBounds().getMinY());
    	                primaryStage.setWidth(screen.getVisualBounds().getWidth());
    	                primaryStage.setHeight(screen.getVisualBounds().getHeight());
    	                primaryStage.initStyle(StageStyle.UNDECORATED);
    	            }
    	        }
    	        
    	    }
    	    
    	}
    	
    	primaryStage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent arg0) {
                /*
                 * Exit properly here
                 */
            }
        });
    	
    	primaryStage.show();
    }

    public static void main(String[] args) {
    	/*
    	 * Set the config location
    	 */
    	System.setProperty("gov.nasa.worldwind.config.document", "config/resc_worldwind.xml");

    	/*
    	 * TODO set window class name
    	 */
    	launch(args);
    }
}

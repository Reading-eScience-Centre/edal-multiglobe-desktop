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
import gov.nasa.worldwind.util.Logging;

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.xml.bind.JAXBException;

import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.logging.RescLogging;

/**
 * Main class for the multi-globe video wall software.
 * 
 * @author Guy Griffiths
 */
@SuppressWarnings("serial")
public class VideoWall extends JFrame {
    private VideoWallCatalogue datasetLoader;
    private MultiGlobeFrame globePanels;
    private JPanel addRemoveRowPanel;
    private JPanel addRemoveColumnPanel;

    /**
     * Set up necessary components and loads some buttons into the main panel
     * for adding/removing globe panels
     */
    public VideoWall() throws IOException, EdalException, JAXBException {
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
        addRemoveRowPanel = new JPanel();

        /* The add row button */
        Button addRowButton = new Button("+");
        addRowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globePanels.addRow();
            }
        });
        addRowButton.setBackground(Color.black);
        addRowButton.setForeground(Color.lightGray);
        addRowButton.setSize(100, 50);

        /* The remove row button */
        Button removeRowButton = new Button("-");
        removeRowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globePanels.removeRow();
            }
        });
        removeRowButton.setBackground(Color.black);
        removeRowButton.setForeground(Color.lightGray);
        addRemoveRowPanel.setLayout(new BoxLayout(addRemoveRowPanel, BoxLayout.PAGE_AXIS));
        addRemoveRowPanel.add(removeRowButton);
        addRemoveRowPanel.add(addRowButton);

        /*
         * Create and wire up the panel for adding/removing columns
         */
        addRemoveColumnPanel = new JPanel();

        /* The add column button */
        Button addColumnButton = new Button("+");
        addColumnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globePanels.addColumn();
            }
        });
        addColumnButton.setBackground(Color.black);
        addColumnButton.setForeground(Color.lightGray);

        /* The remove column button */
        Button removeColumnButton = new Button(" - ");
        removeColumnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globePanels.removeColumn();
            }
        });
        removeColumnButton.setBackground(Color.black);
        removeColumnButton.setForeground(Color.lightGray);
        addRemoveColumnPanel.setLayout(new BoxLayout(addRemoveColumnPanel, BoxLayout.LINE_AXIS));

        addRemoveColumnPanel.add(removeColumnButton);
        addRemoveColumnPanel.add(addColumnButton);

        /*
         * Now set the main window layout with the main globe panel and the
         * buttons
         */
        setLayout(new BorderLayout());
        add(globePanels, BorderLayout.CENTER);
        add(addRemoveColumnPanel, BorderLayout.EAST);
        add(addRemoveRowPanel, BorderLayout.SOUTH);
        
        setPreferredSize(new Dimension(1280, 720));
    }

    public static void main(String[] args) {
        try {
            /*
             * This code sets the X Windows property WM_CLASS to
             * "VideoWallGlobes"
             * 
             * This is useful for identifying the window for e.g. automatically
             * handling it in a tiling window manager.
             */
            Toolkit xToolkit = Toolkit.getDefaultToolkit();
            java.lang.reflect.Field awtAppClassNameField;
            awtAppClassNameField = xToolkit.getClass().getDeclaredField("awtAppClassName");
            awtAppClassNameField.setAccessible(true);
            awtAppClassNameField.set(xToolkit, "VideoWallGlobes");
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException
                | IllegalAccessException e) {
            /*
             * It doesn't really matter if it fails, so ignore it
             */
        }

        /*
         * Set the config location
         */
        System.setProperty("gov.nasa.worldwind.config.document", "config/resc_worldwind.xml");
        
        final boolean fullscreen = Configuration.getBooleanValue("uk.ac.rdg.resc.edal.multiglobe.Fullscreen", true);
        final int screenNumber = Configuration.getIntegerValue("uk.ac.rdg.resc.edal.multiglobe.ScreenNumber", 0);
        
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame = null;
                try {
                    frame = new VideoWall();
                } catch (Exception e) {
                    /*
                     * We have a problem instantiating the video wall. This is
                     * unrecoverable, so log it, print stack trace and exit.
                     */
                    String message = RescLogging.getMessage("resc.StartupError");
                    Logging.logger().severe(message);
                    e.printStackTrace();
                    System.exit(0);
                }
                
                
                if(fullscreen) {
	                /*
	                 * Create a fullscreen application
	                 */
	                frame.setUndecorated(true);
	                int size = frame.getExtendedState();
	                size |= Frame.MAXIMIZED_BOTH;
	                frame.setExtendedState(size);
	                
	                /*
	                 * Move to a particular screen if defined in the config
	                 */
	                GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
	                GraphicsDevice[] gd = ge.getScreenDevices();
	                if( screenNumber > -1 && screenNumber < gd.length ) {
	                	frame.setLocation(gd[screenNumber].getDefaultConfiguration().getBounds().x, gd[screenNumber].getDefaultConfiguration().getBounds().y);
	                } else if( gd.length > 0 ) {
	                	frame.setLocation(gd[0].getDefaultConfiguration().getBounds().x, gd[0].getDefaultConfiguration().getBounds().y);
	                } else {
	                	throw new RuntimeException( "No Screens Found" );
	                }
                }
                frame.pack();
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setTitle("MultiGlobe");
                frame.setVisible(true);
            }
        });
    }
}

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

import java.awt.BorderLayout;
import java.awt.Button;
import java.awt.Color;
import java.awt.Frame;
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

@SuppressWarnings("serial")
public class VideoWall extends JFrame {
    private VideoWallCatalogue datasetLoader;
    private MultiGlobeFrame globePanels;
    //    private RescControlPanel controlPanel;
    private JPanel addRemoveRowPanel;
    private JPanel addRemoveColumnPanel;

    public VideoWall() throws IOException, EdalException, JAXBException {
        DatasetFactory.setDefaultDatasetFactoryClass(CdmGridDatasetFactory.class);

        datasetLoader = new VideoWallCatalogue();
        globePanels = new MultiGlobeFrame(datasetLoader);

        addRemoveRowPanel = new JPanel();
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

        addRemoveColumnPanel = new JPanel();
        Button addColumnButton = new Button("+");
        addColumnButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                globePanels.addColumn();
            }
        });
        addColumnButton.setBackground(Color.black);
        addColumnButton.setForeground(Color.lightGray);
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

        setLayout(new BorderLayout());
        add(globePanels, BorderLayout.CENTER);
        add(addRemoveColumnPanel, BorderLayout.EAST);
        add(addRemoveRowPanel, BorderLayout.SOUTH);

        //        controlPanel = new RescControlPanel(this, globePanels);
        //        add(controlPanel, BorderLayout.WEST);
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

        System.setProperty("gov.nasa.worldwind.config.document", "config/resc_worldwind.xml");
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame;
                try {
                    frame = new VideoWall();
                    frame.setUndecorated(true);
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.pack();
                    int size = frame.getExtendedState();
                    size |= Frame.MAXIMIZED_BOTH;
                    frame.setExtendedState(size);
                    frame.setVisible(true);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (EdalException e) {
                    e.printStackTrace();
                } catch (JAXBException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

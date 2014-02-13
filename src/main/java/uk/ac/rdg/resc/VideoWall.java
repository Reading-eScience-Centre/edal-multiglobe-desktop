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

import gov.nasa.worldwind.BasicFactory;
import gov.nasa.worldwind.Configuration;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.layers.Layer;
import gov.nasa.worldwind.layers.LayerList;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.io.IOException;

import javax.swing.JFrame;
import javax.xml.bind.JAXBException;

import org.w3c.dom.Element;

import uk.ac.rdg.resc.edal.exceptions.EdalException;

@SuppressWarnings("serial")
public class VideoWall extends JFrame {
    private VideoWallCatalogue datasetLoader;
    private MultiGlobeFrame globePanels;
    private RescControlPanel controlPanel;
    
    public VideoWall() throws IOException, EdalException, JAXBException {
        
        datasetLoader = new VideoWallCatalogue();
        globePanels = new MultiGlobeFrame(datasetLoader);
        controlPanel = new RescControlPanel(this, globePanels);
        
        setLayout(new BorderLayout());
        add(globePanels, BorderLayout.CENTER);
        add(controlPanel, BorderLayout.WEST);
    }
    
    public static void main(String[] args) {
        System.setProperty("gov.nasa.worldwind.config.document", "config/resc_worldwind.xml");
//        Element element = Configuration.getElement("./LayerList");
//        Object o = BasicFactory.create(AVKey.LAYER_FACTORY, element);
//        LayerList[] lists = (LayerList[]) o;
//        LayerList ll = null;
//        if (lists.length > 0)
//             ll = LayerList.collapseLists((LayerList[]) o);
//        for(Layer l : ll) {
//            System.out.println(l.getName()+","+l.getClass());
//        }
//        System.out.println(o.getClass());
//        System.out.println(element.getTagName());
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

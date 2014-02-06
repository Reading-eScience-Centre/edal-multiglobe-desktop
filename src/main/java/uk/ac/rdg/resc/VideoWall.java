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

import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.Model;
import gov.nasa.worldwind.View;
import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.awt.WorldWindowGLCanvas;

import java.awt.Color;
import java.awt.GridLayout;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

import javax.swing.JFrame;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue.FeaturesAndMemberName;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;

public class VideoWall extends JFrame {
    private static final long serialVersionUID = 1L;

    public VideoWall() throws IOException, EdalException {
        CdmGridDatasetFactory datasetFactory = new CdmGridDatasetFactory();
        final Dataset dataset = datasetFactory.createDataset("foam",
                "/home/guy/Data/OSTIA/ostia.ncml");
//                "/home/guy/Data/FOAM_ONE/FOAM_one.ncml");
        FeatureCatalogue featureCatalogue = new FeatureCatalogue() {
            @Override
            public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
                    throws EdalException {
                Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset
                        .extractMapFeatures(CollectionUtils.setOf(id), params);
                return new FeaturesAndMemberName(mapFeatures, id);
            }
        };

//        Model model = new BasicModel();
        
        int panelWidth = 1920 / 2;
        int panelHeight = 1150 / 2;

        WorldWindowGLCanvas wwd = new WorldWindowGLCanvas();
        wwd.setPreferredSize(new java.awt.Dimension(panelWidth, panelHeight));
        wwd.setModel(new BasicModel());
        WorldWindowGLCanvas wwd2 = new WorldWindowGLCanvas();
        wwd2.setPreferredSize(new java.awt.Dimension(panelWidth, panelHeight));
        wwd2.setModel(new BasicModel());
        WorldWindowGLCanvas wwd3 = new WorldWindowGLCanvas();
        wwd3.setPreferredSize(new java.awt.Dimension(panelWidth, panelHeight));
        wwd3.setModel(new BasicModel());
        WorldWindowGLCanvas wwd4 = new WorldWindowGLCanvas();
        wwd4.setPreferredSize(new java.awt.Dimension(panelWidth, panelHeight));
        wwd4.setModel(new BasicModel());

        View view = wwd.getView();
        System.out.println(view.getClass());
        wwd2.setView(view);
        wwd3.setView(view);
        wwd4.setView(view);
        
        ProceduralTestLayer tmp = new ProceduralTestLayer("analysed_sst", featureCatalogue, 270f, 305f);
        ProceduralTestLayer salty = new ProceduralTestLayer("analysis_error", featureCatalogue, 0f, 5f);
//        ProceduralTestLayer icec = new ProceduralTestLayer("UV-mag", featureCatalogue, 0f, 3f);
//        ProceduralTestLayer icetk = new ProceduralTestLayer("ICETK", featureCatalogue, 0f, 9f);
        tmp.setEnabled(true);
        salty.setEnabled(true);
//        icec.setEnabled(true);
//        icetk.setEnabled(true);
        wwd.getModel().getLayers().add(tmp);
        wwd2.getModel().getLayers().add(salty);
//        wwd3.getModel().getLayers().add(icec);
//        wwd4.getModel().getLayers().add(icetk);

        /*
         * Note, dragging over multiple globe panels only works because you
         * modified AbstractViewInputHandler.constrainPointToSource()
         * 
         * It's a bit of a hack which will need sorting out in a proper program,
         * but it's fine for now.
         */

        GridLayout gridLayout = new GridLayout(1, 2);
        this.setLayout(gridLayout);
        this.add(wwd);
        this.add(wwd2);
//        this.add(wwd3);
//        this.add(wwd4);
    }

    public static void main(String[] args) {
        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                JFrame frame;
                try {
                    frame = new VideoWall();
                    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    frame.pack();
                    frame.setVisible(true);
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (EdalException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

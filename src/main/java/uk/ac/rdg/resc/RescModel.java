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

import uk.ac.rdg.resc.edal.exceptions.EdalException;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.render.GlobeAnnotationBalloon;
import gov.nasa.worldwind.render.GlobeBrowserBalloon;

public class RescModel extends BasicModel {
    private Earth globe;
    private EarthFlat flatMap;
    private VideoWallCatalogue catalogue;
    private boolean flat = false;
    
    private EdalDataLayer edalDataLayer = null;
    
    private static int createdModels = 0;
    private EdalConfigLayer edalConfigLayer;
    
    public RescModel(VideoWallCatalogue catalogue, RescWorldWindow wwd) {
        super();

        this.catalogue = catalogue;

        globe = new Earth();
        flatMap = new EarthFlat();
        setGlobe(globe);

        /*
         * TODO create 2 edal data layers and switch between them?  c.f. buffer flipping
         */
        edalDataLayer = new EdalDataLayer("edal-layer-"+(createdModels++), catalogue);
        getLayers().add(edalDataLayer);
        
        edalConfigLayer = new EdalConfigLayer(wwd, catalogue);
        getLayers().add(edalConfigLayer);
    }

    public void setFlat(boolean flat) {
        this.flat = flat;
        if (flat) {
            setGlobe(flatMap);
        } else {
            setGlobe(globe);
        }
    }

    public boolean isFlat() {
        return flat;
    }

    public EdalDataLayer getDataLayer() {
        return edalDataLayer;
    }
    
    public void setDataLayer(String layerName) {
        edalDataLayer.setData(layerName);
    }

    public void showFeatureInfo(Position position) {
        if(edalDataLayer.isShowingData()) {
            Number value = null;
            try {
                /*
                 * null for DateTime because it's a hack (see comment in VideoWallCatalogue about this method being a hack...)
                 */
                value = catalogue.getLayerValue(edalDataLayer.getDataLayerName(), position, null, null);
            } catch (EdalException e) {
                e.printStackTrace();
            }
            GlobeAnnotationBalloon balloon = new GlobeAnnotationBalloon(edalDataLayer.getDataLayerName()+": "+value+"\nYou can't close this yet.  It's still in the proof-of-concept stage...", position);
            edalConfigLayer.addRenderable(balloon);
        }
    }
}

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

import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.layers.Layer;

public class RescModel extends BasicModel {
    private Earth globe;
    private EarthFlat flatMap;
    private FeatureCatalogue catalogue;

    public RescModel(FeatureCatalogue catalogue) {
        super();
        this.catalogue = catalogue;
        
        globe = new Earth();
        flatMap = new EarthFlat();
        setGlobe(globe);
    }

    public void setFlat(boolean flat) {
        if (flat) {
            setGlobe(flatMap);
        } else {
            setGlobe(globe);
        }
    }
    
    public void setDataLayer(String layerName, float min, float max) {
        Layer layerByName = getLayers().getLayerByName("edal-layer");
        if(layerByName != null) {
            getLayers().remove(layerByName);
        }
        getLayers().add(new ProceduralTestLayer(layerName, catalogue, min, max));
    }
}

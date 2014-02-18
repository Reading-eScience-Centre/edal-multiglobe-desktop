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
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import gov.nasa.worldwind.BasicModel;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.globes.Earth;
import gov.nasa.worldwind.globes.EarthFlat;
import gov.nasa.worldwind.layers.AnnotationLayer;
import gov.nasa.worldwind.render.GlobeAnnotationBalloon;
import gov.nasa.worldwind.render.GlobeBrowserBalloon;
import gov.nasa.worldwindx.examples.util.DialogAnnotation;

public class RescModel extends BasicModel {
    private Earth globe;
    private EarthFlat flatMap;
    private VideoWallCatalogue catalogue;
    private boolean flat = false;

    private EdalDataLayer currentDataLayer = null;
    private String edalLayerName = null;

    private EdalConfigLayer edalConfigLayer;

    private AnnotationLayer annotationLayer;
    private RescWorldWindow wwd;

    public RescModel(VideoWallCatalogue catalogue, RescWorldWindow wwd) {
        super();

        this.wwd = wwd;

        this.catalogue = catalogue;

        globe = new Earth();
        flatMap = new EarthFlat();
        setGlobe(globe);

        edalConfigLayer = new EdalConfigLayer(wwd, catalogue);
        getLayers().add(edalConfigLayer);

        annotationLayer = new AnnotationLayer();
        getLayers().add(annotationLayer);
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

    public void setDataLayer(String layerName) {
        edalLayerName = layerName;
        /*
         * TODO Add time, elevation, etc to method signature
         */
        if (currentDataLayer != null) {
            getLayers().remove(currentDataLayer);
        }
        if (layerName == null) {
            currentDataLayer = null;
        } else {
            /*
             * TODO layerId needs to be (e.g.) a UUID which encapsulates date,
             * time, elevation, layername, colourmap, etc. for caching
             */
            String layerId = layerName;
            currentDataLayer = new EdalDataLayer(layerId, catalogue);
            currentDataLayer.setData(layerName);
            currentDataLayer.setPickEnabled(true);
            getLayers().add(currentDataLayer);
        }
    }

    public void showFeatureInfo(Position position) {
        if (edalLayerName != null && !edalLayerName.equals("")) {
            /*
             * Only display feature info if we have an active layer
             */
            try {
                /*
                 * null for DateTime because it's a hack (see comment in
                 * VideoWallCatalogue about this method being a hack...)
                 */
                VariableMetadata metadata = catalogue.getVariableMetadataForLayer(edalLayerName);
                if(metadata.getTemporalDomain() != null) {
                    /*
                     * Generate a timeseries graph
                     */
                }
                if(metadata.getVerticalDomain() != null) {
                    /*
                     * Generate a profile graph
                     */
                }
                /*-
                 * Make the feature info balloon contain:
                 * 
                 * The layer name?
                 * 
                 * The value, nicely formatted and bold
                 * 
                 * A big close button (currently has a wee one)
                 * 
                 * A small square with the colour representing the value?
                 * 
                 * The timeseries/profile graphs (clickable -> big screen annotation)
                 */
                
                Number value = catalogue.getLayerValue(edalLayerName, position, null, null);
                FeatureInfoBalloon balloon = new FeatureInfoBalloon(
//                        edalLayerName
//                                + ": "
//                                + value
//                                + "\nYou can't close this yet.  It's still in the proof-of-concept stage...",
                        position, wwd, annotationLayer);
                annotationLayer.addAnnotation(balloon);
            } catch (EdalException e) {
                e.printStackTrace();
            }
        }
    }
}

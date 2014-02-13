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

import gov.nasa.worldwind.geom.Position;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.dataset.DatasetFactory;
import uk.ac.rdg.resc.edal.dataset.cdm.CdmGridDatasetFactory;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsConfig;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsConfig.DatasetStorage;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsVariable;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.GridCoordinates2D;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.edal.wms.exceptions.WmsLayerNotFoundException;
import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;

public class VideoWallCatalogue implements DatasetStorage, FeatureCatalogue {
    private NcwmsConfig config;
    private Map<String, Dataset> datasets;
    private Map<String, NcwmsVariable> variables;
    private ActiveLayerMenuItem rootMenuNode = null;

    public VideoWallCatalogue() throws IOException, JAXBException {
        DatasetFactory.setDefaultDatasetFactoryClass(CdmGridDatasetFactory.class);

        config = NcwmsConfig.readFromFile(new File("/home/guy/.ncWMS-edal/config.xml"));
        config.setDatasetLoadedHandler(this);
        config.loadDatasets();

        datasets = new HashMap<>();
        variables = new HashMap<>();

        rootMenuNode = new ActiveLayerMenuItem("Root", "root", false, false);
    }

    @Override
    public void datasetLoaded(Dataset dataset, Collection<NcwmsVariable> variables) {
        //        System.out.println("Dataset "+dataset.getId()+" loaded");
        datasets.put(dataset.getId(), dataset);
        for (NcwmsVariable variable : variables) {
            String layerName = getLayerName(dataset.getId(), variable.getId());
            this.variables.put(layerName, variable);
        }
        addDatasetToMenu(dataset);
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
            throws EdalException {
        Dataset dataset = getDatasetFromLayerName(id);
        String varId = getVariableFromId(id);

        Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset.extractMapFeatures(
                CollectionUtils.setOf(varId), params);
        return new FeaturesAndMemberName(mapFeatures, varId);
    }

    public Number getLayerValue(String layerId, Position position, Double z, DateTime time)
            throws EdalException {
        /*
         * Massive hack for proof-of-concept.
         * 
         * Make it work properly (maybe we want a readSinglePoint method on the
         * dataset?). Also, document Dataset!
         * 
         * The previous version of this (also a massive hack) didn't work
         * because of issues with readFeature for grids where an axis is
         * missing...
         */
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableFromId(layerId);
        Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset.extractMapFeatures(
                CollectionUtils.setOf(varId), new PlottingDomainParams(100, 100,
                        new BoundingBoxImpl(position.longitude.degrees, position.latitude.degrees,
                                position.longitude.degrees + 0.1, position.latitude.degrees + 0.1,
                                DefaultGeographicCRS.WGS84), null, null, null, null, null));

        DiscreteFeature<?, ?> feature = mapFeatures.iterator().next();
        if (feature instanceof MapFeature) {
            MapFeature mapFeature = (MapFeature) feature;
            /*
             * This should correspond to the ll corner of the bbox, so that's right
             * 
             * But yeah, massive hack 
             */
            return mapFeature.getValues(varId).get(0,0);
            
        } else {
            return null;
        }
    }

    /**
     * Gets the {@link VariableMetadata} object corresponding to a named layer
     * 
     * @param layerName
     *            The name of the WMS layer
     * @return The corresponding {@link VariableMetadata}
     * @throws WmsLayerNotFoundException
     *             If the WMS layer name doesn't map to a variable
     */
    public VariableMetadata getVariableMetadataForLayer(String layerName) throws EdalException {
        Dataset dataset = getDatasetFromLayerName(layerName);
        String variableFromId = getVariableFromId(layerName);
        if (dataset != null && variableFromId != null) {
            return dataset.getVariableMetadata(variableFromId);
        } else {
            throw new EdalException("The layer name " + layerName + " doesn't map to a variable");
        }
    }

    public Extent<Float> getRangeForLayer(String layerName) throws EdalException {
        if (!variables.containsKey(layerName)) {
            throw new EdalException("Layer " + layerName
                    + " is not present or has not been loaded yet");
        }
        return variables.get(layerName).getColorScaleRange();
    }

    public ActiveLayerMenuItem getEdalLayers() {
        return rootMenuNode;
    }

    private void addDatasetToMenu(Dataset dataset) {
        ActiveLayerMenuItem datasetNode = new ActiveLayerMenuItem(config.getDatasetInfo(
                dataset.getId()).getTitle(), dataset.getId(), false, false);
        Set<VariableMetadata> variables = dataset.getTopLevelVariables();
        for (VariableMetadata variable : variables) {
            LayerMenuItem child = createMenuNode(variable);
            datasetNode.addChildItem(child);
        }
        rootMenuNode.addChildItem(datasetNode);
    }

    private ActiveLayerMenuItem createMenuNode(VariableMetadata metadata) {
        String layerId = getLayerName(metadata);
        ActiveLayerMenuItem variableNode = new ActiveLayerMenuItem(variables.get(layerId)
                .getTitle(), layerId, true, false);
        Set<VariableMetadata> variables = metadata.getChildren();
        for (VariableMetadata variable : variables) {
            ActiveLayerMenuItem child = createMenuNode(variable);
            variableNode.addChildItem(child);
        }
        return variableNode;
    }

    private Dataset getDatasetFromLayerName(String layerName) throws WmsLayerNotFoundException {
        String[] layerParts = layerName.split("/");
        if (layerParts.length != 2) {
            throw new WmsLayerNotFoundException(
                    "The WMS layer name is malformed.  It should be of the form \"dataset/variable\"");
        }
        return datasets.get(layerParts[0]);
    }

    private String getVariableFromId(String layerName) throws WmsLayerNotFoundException {
        String[] layerParts = layerName.split("/");
        if (layerParts.length != 2) {
            throw new WmsLayerNotFoundException(
                    "The WMS layer name is malformed.  It should be of the form \"dataset/variable\"");
        }
        return layerParts[1];
    }

    private String getLayerName(VariableMetadata metadata) {
        return getLayerName(metadata.getDataset().getId(), metadata.getId());
    }

    private String getLayerName(String datasetId, String variableId) {
        return datasetId + "/" + variableId;
    }
}

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
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.ncwms.NcwmsCatalogue;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsConfig;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsConfig.DatasetStorage;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsVariable;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.edal.wms.WmsLayerMetadata;
import uk.ac.rdg.resc.edal.wms.exceptions.WmsLayerNotFoundException;
import uk.ac.rdg.resc.edal.wms.util.ContactInfo;
import uk.ac.rdg.resc.edal.wms.util.ServerInfo;
import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;

public class VideoWallCatalogue extends NcwmsCatalogue implements DatasetStorage, FeatureCatalogue {
//    private NcwmsConfig config;
//    private Map<String, Dataset> datasets;
//    private Map<String, NcwmsVariable> variables;
    private final LayerMenuItem rootMenuNode;

    private Map<String, GridFeature> gridFeatures;

    public VideoWallCatalogue() throws IOException, JAXBException {
        super(NcwmsConfig.readFromFile(new File("/home/guy/.ncWMS-edal/config.xml")));
        
//        config = NcwmsConfig.readFromFile(new File("/home/guy/.ncWMS-edal/config.xml"));
//        config.setDatasetLoadedHandler(this);
//        config.loadDatasets();

//        datasets = new HashMap<>();
//        variables = new HashMap<>();
        gridFeatures = new HashMap<>();

        rootMenuNode = new LayerMenuItem("Datasets", "root", false);
    }

    @Override
    public void datasetLoaded(Dataset dataset, Collection<NcwmsVariable> variables) {
        super.datasetLoaded(dataset, variables);
        
        for (NcwmsVariable variable : variables) {
            Class<? extends DiscreteFeature<?, ?>> mapFeatureType = dataset
                    .getMapFeatureType(variable.getId());
            if (GridFeature.class.isAssignableFrom(mapFeatureType)) {
                /*
                 * This preloads features in memory.  Speeds up operation, but slows loading
                 */
//                try {
//                    GridFeature gridFeature = (GridFeature) dataset.readFeature(variable.getId());
//                    gridFeatures.put(layerName, gridFeature);
//                } catch (DataReadingException e) {
//                    /*
//                     * TODO log this properly, and ignore can't read data, but
//                     * maybe it will work later...
//                     * 
//                     * Or just make this layer unavailable...
//                     */
//                    e.printStackTrace();
//                }
            } else {
                /*
                 * Not a grid feature. For now we ignore it and retrieve as
                 * normal, but we may want to add different methods for
                 * point/profile feature
                 */
            }
        }
        System.out.println(dataset.getId()+" loaded");
        addDatasetToMenu(dataset);
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
            throws EdalException {
        String varId = getVariableIdFromLayerName(id);
        if (gridFeatures.containsKey(id)) {
            /*
             * If we have an in-memory grid feature, just extract a subset of it
             */
            MapFeature mapFeature = gridFeatures.get(id).extractMapFeature(
                    CollectionUtils.setOf(varId), params.getImageGrid(), params.getTargetZ(),
                    params.getTargetT());
            return new FeaturesAndMemberName(CollectionUtils.setOf(mapFeature), varId);
        } else {
            Dataset dataset = getDatasetFromLayerName(id);

            Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset.extractMapFeatures(
                    CollectionUtils.setOf(varId), params);
            return new FeaturesAndMemberName(mapFeatures, varId);
        }
    }

    public Number getLayerValue(String layerId, Position position, Double z, DateTime time)
            throws EdalException {
        /*
         * Bit of a hack for proof-of-concept.
         */
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableIdFromLayerName(layerId);
        Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset.extractMapFeatures(
                CollectionUtils.setOf(varId), new PlottingDomainParams(1, 1, new BoundingBoxImpl(
                        position.longitude.degrees - 1e-8, position.latitude.degrees - 1e-8,
                        position.longitude.degrees + 1e-8, position.latitude.degrees + 1e-8,
                        DefaultGeographicCRS.WGS84), null, null, null, null, null));

        DiscreteFeature<?, ?> feature = mapFeatures.iterator().next();
        if (feature instanceof MapFeature) {
            MapFeature mapFeature = (MapFeature) feature;
            return mapFeature.getValues(varId).get(0, 0);
        } else {
            return null;
        }
    }

    public Collection<? extends ProfileFeature> getProfiles(String layerId, Position position)
            throws EdalException {
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableIdFromLayerName(layerId);
        VariableMetadata variableMetadata = dataset.getVariableMetadata(varId);
        DateTime latest = variableMetadata.getTemporalDomain().getExtent().getHigh();
        Collection<? extends ProfileFeature> profileFeatures = dataset.extractProfileFeatures(
                CollectionUtils.setOf(varId), new PlottingDomainParams(1, 1, null, null, null,
                        new HorizontalPosition(position.longitude.degrees,
                                position.latitude.degrees, DefaultGeographicCRS.WGS84), null,
                        latest));
        return profileFeatures;
    }

    public Collection<? extends PointSeriesFeature> getTimeseries(String layerId, Position position)
            throws EdalException {
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableIdFromLayerName(layerId);
        VariableMetadata variableMetadata = dataset.getVariableMetadata(varId);
        Double surface = GISUtils
                .getClosestElevationToSurface(variableMetadata.getVerticalDomain());
        Collection<? extends PointSeriesFeature> timeseriesFeatures = dataset
                .extractTimeseriesFeatures(CollectionUtils.setOf(varId), new PlottingDomainParams(
                        1, 1, null, null, null, new HorizontalPosition(position.longitude.degrees,
                                position.latitude.degrees, DefaultGeographicCRS.WGS84), surface,
                        null));
        return timeseriesFeatures;
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
        String variableFromId = getVariableIdFromLayerName(layerName);
        if (dataset != null && variableFromId != null) {
            return dataset.getVariableMetadata(variableFromId);
        } else {
            throw new EdalException("The layer name " + layerName + " doesn't map to a variable");
        }
    }

    public LayerMenuItem getEdalLayers() {
        return rootMenuNode;
    }

    private void addDatasetToMenu(Dataset dataset) {
        LayerMenuItem datasetNode = new LayerMenuItem(config.getDatasetInfo(
                dataset.getId()).getTitle(), dataset.getId(), false);
        Set<VariableMetadata> variables = dataset.getTopLevelVariables();
        for (VariableMetadata variable : variables) {
            LayerMenuItem child = createMenuNode(variable);
            datasetNode.addChildItem(child);
        }
        rootMenuNode.addChildItem(datasetNode);
    }

    private LayerMenuItem createMenuNode(VariableMetadata metadata) {
        String layerId = getLayerName(metadata);
        LayerMenuItem variableNode = new LayerMenuItem(layerMetadata.get(layerId)
                .getTitle(), layerId, true);
        Set<VariableMetadata> variables = metadata.getChildren();
        for (VariableMetadata variable : variables) {
            LayerMenuItem child = createMenuNode(variable);
            variableNode.addChildItem(child);
        }
        return variableNode;
    }

    @Override
    public Dataset getDatasetFromLayerName(String layerName) throws WmsLayerNotFoundException {
        String[] layerParts = layerName.split("/");
        if (layerParts.length != 2) {
            throw new WmsLayerNotFoundException(
                    "The WMS layer name is malformed.  It should be of the form \"dataset/variable\"");
        }
        return datasets.get(layerParts[0]);
    }

    public String getVariableIdFromLayerName(String layerName) throws WmsLayerNotFoundException {
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

    @Override
    public String getLayerName(String datasetId, String variableId) {
        return datasetId + "/" + variableId;
    }
}

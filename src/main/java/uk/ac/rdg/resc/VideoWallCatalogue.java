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
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.util.Logging;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.geotoolkit.referencing.crs.DefaultGeographicCRS;
import org.joda.time.DateTime;

import uk.ac.rdg.resc.edal.dataset.Dataset;
import uk.ac.rdg.resc.edal.domain.Extent;
import uk.ac.rdg.resc.edal.exceptions.EdalException;
import uk.ac.rdg.resc.edal.feature.DiscreteFeature;
import uk.ac.rdg.resc.edal.feature.GridFeature;
import uk.ac.rdg.resc.edal.feature.MapFeature;
import uk.ac.rdg.resc.edal.feature.PointSeriesFeature;
import uk.ac.rdg.resc.edal.feature.ProfileFeature;
import uk.ac.rdg.resc.edal.geometry.BoundingBoxImpl;
import uk.ac.rdg.resc.edal.graphics.style.util.FeatureCatalogue;
import uk.ac.rdg.resc.edal.metadata.GridVariableMetadata;
import uk.ac.rdg.resc.edal.metadata.VariableMetadata;
import uk.ac.rdg.resc.edal.ncwms.NcwmsCatalogue;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsConfig;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsConfig.DatasetStorage;
import uk.ac.rdg.resc.edal.ncwms.config.NcwmsVariable;
import uk.ac.rdg.resc.edal.position.HorizontalPosition;
import uk.ac.rdg.resc.edal.util.CollectionUtils;
import uk.ac.rdg.resc.edal.util.GISUtils;
import uk.ac.rdg.resc.edal.util.PlottingDomainParams;
import uk.ac.rdg.resc.edal.wms.exceptions.EdalLayerNotFoundException;
import uk.ac.rdg.resc.godiva.shared.LayerMenuItem;
import uk.ac.rdg.resc.logging.RescLogging;

/**
 * A class to manage all of the datasets available for the video wall software.
 * This extends {@link NcwmsCatalogue} to provide some extra caching ability as
 * well as additional functionality required only for the video wall.
 * 
 * @author Guy Griffiths
 */
public class VideoWallCatalogue extends NcwmsCatalogue implements DatasetStorage, FeatureCatalogue {
    /** The root of the layer menu */
    private final LayerMenuItem rootMenuNode;
    /** A cache of gridded features, for fast loading */
    private Map<String, GridFeature> gridFeatures;

    public VideoWallCatalogue() throws IOException, JAXBException {
        /*
         * Create a catalogue from the config.xml file specified in the
         * configuration.
         * 
         * If the config file doesn't exist it will be created with no datasets
         * 
         * If no config file is specified in the WW config, create it at
         * ~/edalVideoWall/config.xml
         */
        super(NcwmsConfig.readFromFile(new File(Configuration.getStringValue(
                "uk.ac.rdg.resc.edal.multiglobe.CatalogueLocation", System.getProperty("user.home")
                        + "/.edalVideoWall/config.xml"))));

        gridFeatures = new HashMap<>();

        rootMenuNode = new LayerMenuItem("Datasets", "root", false);
    }

    @Override
    public void datasetLoaded(Dataset dataset, Collection<NcwmsVariable> variables) {
        super.datasetLoaded(dataset, variables);
        /*
         * Update the menu. We do this prior to trying to cache the dataset.
         */
        addDatasetToMenu(dataset);
        System.out.println("Loaded metadata for dataset: " + dataset.getId());
        /*
         * For any variables which map to gridded features, preload into memory
         */
        for (NcwmsVariable variable : variables) {
            Class<? extends DiscreteFeature<?, ?>> featureType = dataset.getFeatureType(variable
                    .getId());
            if (GridFeature.class.isAssignableFrom(featureType)) {
                /*
                 * This preloads features in memory. Speeds up operation, but
                 * slows loading. This is usually commented out during
                 * development, but should be uncommented during production
                 */
                try {
                    String layerName = getLayerName(dataset.getId(), variable.getId());
                    GridFeature gridFeature = (GridFeature) dataset.readFeature(variable.getId());
                    gridFeatures.put(layerName, gridFeature);
                    System.out.println("grid feature cached in memory with ID:" + layerName);
                } catch (Exception e) {
                    System.out.println("Dataset " + dataset.getId()
                            + " couldn't be loaded into memory");
                    e.printStackTrace();
                    /*
                     * Log, and ignore that it can't read data, but maybe it
                     * will work later (e.g. remote data).
                     */
                    String message = RescLogging.getMessage("resc.DataReadingProblem");
                    Logging.logger().warning(message);
                }
            } else {
                /*
                 * Not a grid feature dataset. Currently no caching is
                 * implemented (at this level) for non-gridded features.
                 * 
                 * If caching is required, implement it here
                 */
            }
        }
        System.out.println(dataset.getId() + " loaded");
        String message = RescLogging.getMessage("resc.DatasetLoaded", dataset.getId());
        Logging.logger().fine(message);
    }

    @Override
    public FeaturesAndMemberName getFeaturesForLayer(String id, PlottingDomainParams params)
            throws EdalException {
        String varId = getVariableFromId(id);

        if (gridFeatures.containsKey(id)) {
            /*
             * If we have an in-memory grid feature, just extract a subset of it
             */
            MapFeature mapFeature = gridFeatures.get(id).extractMapFeature(
                    CollectionUtils.setOf(varId), params.getImageGrid(), params.getTargetZ(),
                    params.getTargetT());
            return new FeaturesAndMemberName(CollectionUtils.setOf(mapFeature), varId);
        } else {
            /*
             * We have a non-cached layer, so extract it
             */
            Dataset dataset = getDatasetFromLayerName(id);

            Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset.extractMapFeatures(
                    CollectionUtils.setOf(varId), params);
            return new FeaturesAndMemberName(mapFeatures, varId);
        }
    }

    public Number getLayerValue(String layerId, Position position, Double z, DateTime time,
            Extent<Double> zRange, Extent<DateTime> tRange, double sensitivity)
            throws EdalException {
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableFromId(layerId);
        Collection<? extends DiscreteFeature<?, ?>> mapFeatures = dataset.extractMapFeatures(
                CollectionUtils.setOf(varId), new PlottingDomainParams(1, 1, new BoundingBoxImpl(
                        position.longitude.degrees - sensitivity, position.latitude.degrees
                                - sensitivity, position.longitude.degrees + sensitivity,
                        position.latitude.degrees + sensitivity, DefaultGeographicCRS.WGS84),
                        zRange, tRange, null, null, null));

        if (!mapFeatures.isEmpty()) {
            DiscreteFeature<?, ?> feature = mapFeatures.iterator().next();
            if (feature instanceof MapFeature) {
                MapFeature mapFeature = (MapFeature) feature;
                return mapFeature.getValues(varId).get(0, 0);
            } else if (feature instanceof ProfileFeature) {
                ProfileFeature profileFeature = (ProfileFeature) feature;
                int index = GISUtils.getIndexOfClosestElevationTo(z, profileFeature.getDomain());
                return profileFeature.getValues(varId).get(index);
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    /**
     * Returns a {@link List} of {@link ProfileFeature}s extracted from the
     * given layer.
     * 
     * @param layerId
     *            The layer to extract from
     * @param position
     *            The target {@link Position} to extract features from
     * @param sensitivity
     *            The range, in degrees, around the target position to extract
     *            features from
     * @param elevationRange
     *            An {@link Extent} representing elevation. All extracted
     *            profiles will have a domain which intersects this
     * @param timeRange
     *            An {@link Extent} representing time. All extracted profiles
     *            will have a time which falls within this, unless it is a
     *            zero-width range in which case gridded features will extract
     *            the nearest profiles (in time) and non-gridded will extract
     *            exact time matches
     * @return A {@link List} of extracted features
     * @throws EdalException
     *             If there is a problem extracting the profiles
     */
    public List<? extends ProfileFeature> getProfiles(String layerId, Position position,
            double sensitivity, Extent<Double> elevationRange, Extent<DateTime> timeRange)
            throws EdalException {
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableFromId(layerId);
        if (!dataset.supportsProfileFeatureExtraction(varId)) {
            /*
             * Profile features not supported for this dataset/variable
             * combination, return an empty list
             */
            String message = RescLogging.getMessage("resc.UnsupportedProfileExtraction");
            Logging.logger().warning(message);
            return new ArrayList<>();
        } else {
            /*
             * Extract features from the dataset
             */
            DateTime targetT = null;
            if (timeRange != null && timeRange.getLow().equals(timeRange.getHigh())) {
                /*
                 * If no range is selected on the time slider, we don't want to
                 * have exact matches only for gridded features, so we nullify
                 * the time range
                 */
                targetT = timeRange.getLow();
                timeRange = null;
            }
            List<? extends ProfileFeature> profileFeatures = dataset.extractProfileFeatures(
                    CollectionUtils.setOf(varId), new PlottingDomainParams(1, 1,
                            new BoundingBoxImpl(position.longitude.degrees - sensitivity,
                                    position.latitude.degrees - sensitivity,
                                    position.longitude.degrees + sensitivity,
                                    position.latitude.degrees + sensitivity,
                                    DefaultGeographicCRS.WGS84), elevationRange, timeRange,
                            new HorizontalPosition(position.longitude.degrees,
                                    position.latitude.degrees, DefaultGeographicCRS.WGS84), null,
                            targetT));
            return profileFeatures;
        }
    }

    /**
     * Returns a {@link List} of {@link PointSeriesFeature}s extracted from the
     * given layer.
     * 
     * @param layerId
     *            The layer to extract from
     * @param position
     *            The target {@link Position} to extract features from
     * @param sensitivity
     *            The range, in degrees, around the target position to extract
     *            features from
     * @param elevationRange
     *            An {@link Extent} representing elevation. All extracted
     *            {@link PointSeriesFeature}s will have an elevation which falls
     *            within this, unless it is a zero-width range in which case
     *            gridded features will extract the nearest series' (in
     *            elevation) and non-gridded will extract exact elevation
     *            matches
     * @param timeRange
     *            An {@link Extent} representing time. All extracted features
     *            will have a time extent which intersects with this
     * @return A {@link List} of extracted features
     * @throws EdalException
     *             If there is a problem extracting the timeseries features
     */
    public List<? extends PointSeriesFeature> getTimeseries(String layerId, Position position,
            double sensitivity, Extent<Double> elevationRange, Extent<DateTime> timeRange)
            throws EdalException {
        Dataset dataset = getDatasetFromLayerName(layerId);
        String varId = getVariableFromId(layerId);
        if (!dataset.supportsTimeseriesExtraction(varId)) {
            /*
             * Timeseries features not supported for this dataset/variable
             * combination, return an empty list
             */
            String message = RescLogging.getMessage("resc.UnsupportedTimeseriesExtraction");
            Logging.logger().warning(message);
            return new ArrayList<>();
        } else {
            /*
             * Extract features from the dataset
             */
            Double targetZ = null;
            if (elevationRange != null && elevationRange.getLow().equals(elevationRange.getHigh())) {
                /*
                 * If no range is selected on the time slider, we don't want to
                 * have exact matches only for gridded features, so we nullify
                 * the time range
                 */
                targetZ = elevationRange.getLow();
                elevationRange = null;
            }
            List<? extends PointSeriesFeature> pointseriesFeatures = dataset
                    .extractTimeseriesFeatures(CollectionUtils.setOf(varId),
                            new PlottingDomainParams(1, 1, new BoundingBoxImpl(
                                    position.longitude.degrees - sensitivity,
                                    position.latitude.degrees - sensitivity,
                                    position.longitude.degrees + sensitivity,
                                    position.latitude.degrees + sensitivity,
                                    DefaultGeographicCRS.WGS84), elevationRange, timeRange,
                                    new HorizontalPosition(position.longitude.degrees,
                                            position.latitude.degrees, DefaultGeographicCRS.WGS84),
                                    targetZ, null));
            return pointseriesFeatures;
        }
    }

    /**
     * Gets the {@link VariableMetadata} object corresponding to a named layer
     * 
     * @param layerName
     *            The name of the WMS layer
     * @return The corresponding {@link VariableMetadata}
     * @throws EdalLayerNotFoundException
     *             If the WMS layer name doesn't map to a variable
     */
    public VariableMetadata getVariableMetadataForLayer(String layerName)
            throws EdalLayerNotFoundException {
        Dataset dataset = getDatasetFromLayerName(layerName);
        String variableFromId = getVariableFromId(layerName);
        if (dataset != null && variableFromId != null) {
            return dataset.getVariableMetadata(variableFromId);
        } else {
            throw new EdalLayerNotFoundException("The layer name " + layerName
                    + " doesn't map to a variable");
        }
    }

    /**
     * @return The root {@link LayerMenuItem} for the menu of the available
     *         layers
     */
    public LayerMenuItem getLayerMenu() {
        return rootMenuNode;
    }

    /**
     * Adds a {@link Dataset} to the menu
     * 
     * @param dataset
     *            The {@link Dataset} to add
     */
    private void addDatasetToMenu(Dataset dataset) {
        LayerMenuItem datasetNode = new LayerMenuItem(config.getDatasetInfo(dataset.getId())
                .getTitle(), dataset.getId(), false);
        Set<VariableMetadata> variables = dataset.getTopLevelVariables();
        for (VariableMetadata variable : variables) {
            LayerMenuItem child = createMenuNode(variable);
            datasetNode.addChildItem(child);
        }
        rootMenuNode.addChildItem(datasetNode);
    }

    /**
     * Recursively creates a node in the layer menu
     * 
     * @param metadata
     *            The {@link VariableMetadata} to extract the title and ID from
     * @return
     */
    private LayerMenuItem createMenuNode(VariableMetadata metadata) {
        String layerId = getLayerName(metadata);
        LayerMenuItem variableNode = new LayerMenuItem(layerMetadata.get(layerId).getTitle(),
                layerId, true);
        Set<VariableMetadata> variables = metadata.getChildren();
        for (VariableMetadata variable : variables) {
            LayerMenuItem child = createMenuNode(variable);
            variableNode.addChildItem(child);
        }
        return variableNode;
    }

    @Override
    public Dataset getDatasetFromLayerName(String layerName) throws EdalLayerNotFoundException {
        String[] layerParts = layerName.split("/");
        if (layerParts.length != 2) {
            throw new EdalLayerNotFoundException(
                    "The WMS layer name is malformed.  It should be of the form \"dataset/variable\"");
        }
        return datasets.get(layerParts[0]);
    }

    @Override
    public String getLayerName(String datasetId, String variableId) {
        return datasetId + "/" + variableId;
    }

    /**
     * Convenience method to get a layer name from {@link VariableMetadata}
     * 
     * @param metadata
     *            The {@link VariableMetadata} object to get the layer name for.
     * @return The resulting layer name
     */
    private String getLayerName(VariableMetadata metadata) {
        return getLayerName(metadata.getDataset().getId(), metadata.getId());
    }

    /**
     * Determine whether a particular layer represents gridded data or not
     * 
     * @param layerName
     *            The layer ID to test
     * @return <code>true</code> if this layer is gridded
     * @throws EdalLayerNotFoundException
     *             If the layer does not exist in this
     *             {@link VideoWallCatalogue}
     */
    public boolean layerIsGridded(String layerName) throws EdalLayerNotFoundException {
        return getVariableMetadataForLayer(layerName) instanceof GridVariableMetadata;
    }
}

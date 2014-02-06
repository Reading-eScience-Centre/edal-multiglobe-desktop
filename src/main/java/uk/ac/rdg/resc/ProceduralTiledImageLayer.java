/*
Copyright (C) 2001, 2006 United States Government
as represented by the Administrator of the
National Aeronautics and Space Administration.
All Rights Reserved.
 */
package uk.ac.rdg.resc;

import gov.nasa.worldwind.WorldWind;
import gov.nasa.worldwind.avlist.AVList;
import gov.nasa.worldwind.layers.BasicTiledImageLayer;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.util.LevelSet;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;

/**
 * @author Patrick Murris
 * @version $Id:$
 */
public abstract class ProceduralTiledImageLayer extends BasicTiledImageLayer {
    public ProceduralTiledImageLayer(LevelSet levelSet) {
        super(levelSet);
    }

    public ProceduralTiledImageLayer(AVList params) {
        super(params);
    }

    abstract BufferedImage createTileImage(TextureTile tile, BufferedImage image);

    @Override
    protected void retrieveRemoteTexture(TextureTile tile, DownloadPostProcessor postProcessor) {
        final File outFile = WorldWind.getDataFileStore().newFile(tile.getPath());
        if (outFile == null)
            return;

        if (outFile.exists())
            return;

        // Create and save tile texture image
        BufferedImage image = new BufferedImage(tile.getLevel().getTileWidth(), tile.getLevel()
                .getTileHeight(), BufferedImage.TYPE_4BYTE_ABGR);
        image = createTileImage(tile, image);
        try {
            ImageIO.write(image, "png", outFile);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}

/*
 * Copyright (C) 2012 United States Government as represented by the Administrator of the
 * National Aeronautics and Space Administration.
 * All Rights Reserved.
 */
package uk.ac.rdg.resc;

import gov.nasa.worldwind.geom.Sector;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.util.Level;

import com.jogamp.opengl.util.texture.TextureData;

/**
 * A {@link TextureTile} which determines whether a tile is expired or not based
 * on when the texture data was set, rather than when the tile was last drawn.
 * 
 * If normal {@link TextureTile}s are used, then they falsely test as not having
 * expired when they actually have, leading to old tiles being displayed. This
 * is particularly problematic when changing colour scales.
 * 
 * I'm not sure of exactly why this is the case, but this behaviour can be seen
 * by:
 * 
 * <li>Changing isTextureExpired to just return super.isTextureExpired()</li>
 * 
 * <li>Running the software and selecting a layer</li>
 * 
 * <li>Zooming in a lot</li>
 * 
 * <li>Changing the colour palette</li>
 * 
 * <li>Zooming out</li>
 * 
 * After which several tiles are not refreshed, but use the old colour palette.
 *
 * @author Guy Griffiths
 */
public class RescTextureTile extends TextureTile {
    private long textureDataUpdateTime = 0L;

    public RescTextureTile(Sector sector) {
        super(sector);
    }

    public RescTextureTile(Sector sector, Level level, int row, int col) {
        super(sector, level, row, col);
    }

    public RescTextureTile(Sector sector, Level level, int row, int column, String cacheName) {
        super(sector, level, row, column, cacheName);
    }

    protected TextureTile createSubTile(Sector sector, Level level, int row, int col) {
        return new RescTextureTile(sector, level, row, col);
    }

    @Override
    public void setTextureData(TextureData textureData) {
        super.setTextureData(textureData);
        textureDataUpdateTime = System.currentTimeMillis();
    }

    @Override
    public long getUpdateTime() {
        return textureDataUpdateTime;
    }

    @Override
    public boolean isTextureExpired(long expiryTime) {
        return this.textureDataUpdateTime > 0 && this.textureDataUpdateTime < expiryTime;
    }
}

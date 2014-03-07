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
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Vec4;
import gov.nasa.worldwind.layers.TextureTile;
import gov.nasa.worldwind.layers.TiledImageLayer;
import gov.nasa.worldwind.render.DrawContext;
import gov.nasa.worldwind.util.LevelSet;
import gov.nasa.worldwind.util.Logging;
import gov.nasa.worldwind.util.PerformanceStatistic;

import java.awt.image.BufferedImage;
import java.util.Arrays;

import javax.media.opengl.GL;
import javax.media.opengl.GL2;

import com.jogamp.opengl.util.texture.TextureData;
import com.jogamp.opengl.util.texture.awt.AWTTextureIO;

/**
 * A {@link TiledImageLayer} which generates images on-the-fly with no caching.
 * 
 * @author Guy
 */
public abstract class OnTheFlyTiledImageLayer extends TiledImageLayer {
    public OnTheFlyTiledImageLayer(LevelSet levelSet) {
        super(levelSet);
    }

    @Override
    protected void forceTextureLoad(TextureTile tile) {
        loadTexture(tile);
    }

    @Override
    protected void requestTexture(DrawContext dc, final TextureTile tile) {
        Vec4 centroid = tile.getCentroidPoint(dc.getGlobe());
        Vec4 referencePoint = this.getReferencePoint(dc);
        if (referencePoint != null)
            tile.setPriority(centroid.distanceTo3(referencePoint));

        this.getRequestQ().add(new SimpleRequestTask(tile, this));
    }

    protected void loadTexture(TextureTile tile) {
        TextureData textureData;

        BufferedImage tileImage = createTileImage(tile);

        textureData = AWTTextureIO.newTextureData(Configuration.getMaxCompatibleGLProfile(),
                tileImage, isUseMipMaps());

        if (textureData == null) {
            /*
             * Log error
             */
            System.out.println("Null texture data");
        }

        tile.setTextureData(textureData);
    }

    @Override
    protected void assembleTiles(DrawContext dc) {
        this.currentTiles.clear();

        for (TextureTile tile : this.getTopLevels()) {
            this.currentResourceTile = null;
            this.addTileOrDescendants(dc, tile);
        }
    }
    
    @Override
    protected void addTileOrDescendants(DrawContext dc, TextureTile tile)
    {
        if (this.meetsRenderCriteria(dc, tile))
        {
            this.addTile(dc, tile);
            return;
        }

        // The incoming tile does not meet the rendering criteria, so it must be subdivided and those
        // subdivisions tested against the criteria.

        // All tiles that meet the selection criteria are drawn, but some of those tiles will not have
        // textures associated with them either because their texture isn't loaded yet or because they
        // are finer grain than the layer has textures for. In these cases the tiles use the texture of
        // the closest ancestor that has a texture loaded. This ancestor is called the currentResourceTile.
        // A texture transform is applied during rendering to align the sector's texture coordinates with the
        // appropriate region of the ancestor's texture.

        TextureTile ancestorResource = null;

        try
        {
            // TODO: Revise this to reflect that the parent layer is only requested while the algorithm continues
            // to search for the layer matching the criteria.
            // At this point the tile does not meet the render criteria but it may have its texture in memory.
            // If so, register this tile as the resource tile. If not, then this tile will be the next level
            // below a tile with texture in memory. So to provide progressive resolution increase, add this tile
            // to the draw list. That will cause the tile to be drawn using its parent tile's texture, and it will
            // cause it's texture to be requested. At some future call to this method the tile's texture will be in
            // memory, it will not meet the render criteria, but will serve as the parent to a tile that goes
            // through this same process as this method recurses. The result of all this is that a tile isn't rendered
            // with its own texture unless all its parents have their textures loaded. In addition to causing
            // progressive resolution increase, this ensures that the parents are available as the user zooms out, and
            // therefore the layer remains visible until the user is zoomed out to the point the layer is no longer
            // active.
//            if (tile.isTextureInMemory(dc.getTextureCache()) || tile.getLevelNumber() == 0)
//            {
//                ancestorResource = this.currentResourceTile;
//                this.currentResourceTile = tile;
//            }
//            else if (!tile.getLevel().isEmpty())
//            {
////                this.addTile(dc, tile);
////                return;
//
//                // Issue a request for the parent before descending to the children.
////                if (tile.getLevelNumber() < this.levels.getNumLevels())
////                {
////                    // Request only tiles with data associated at this level
////                    if (!this.levels.isResourceAbsent(tile))
////                        this.requestTexture(dc, tile);
////                }
//            }

            TextureTile[] subTiles = tile.createSubTiles(this.levels.getLevel(tile.getLevelNumber() + 1));
            for (TextureTile child : subTiles)
            {
                if (this.getLevels().getSector().intersects(child.getSector()) && this.isTileVisible(dc, child))
                    this.addTileOrDescendants(dc, child);
            }
        }
        finally
        {
            if (ancestorResource != null) // Pop this tile as the currentResource ancestor
                this.currentResourceTile = ancestorResource;
        }
    }

    @Override
    protected void draw(DrawContext dc) {
        this.assembleTiles(dc); // Determine the tiles to draw.

        if (this.currentTiles.size() >= 1) {
            // Indicate that this layer rendered something this frame.
            this.setValue(AVKey.FRAME_TIMESTAMP, dc.getFrameTimeStamp());

            if (this.getScreenCredit() != null) {
                dc.addScreenCredit(this.getScreenCredit());
            }

            TextureTile[] sortedTiles = new TextureTile[this.currentTiles.size()];
            sortedTiles = this.currentTiles.toArray(sortedTiles);
            Arrays.sort(sortedTiles, levelComparer);

            GL2 gl = dc.getGL().getGL2(); // GL initialization checks for GL2 compatibility.

            if (this.isUseTransparentTextures() || this.getOpacity() < 1) {
                gl.glPushAttrib(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_POLYGON_BIT | GL2.GL_CURRENT_BIT);
                this.setBlendingFunction(dc);
            } else {
                gl.glPushAttrib(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_POLYGON_BIT);
            }

            gl.glPolygonMode(GL2.GL_FRONT, GL2.GL_FILL);
            gl.glEnable(GL.GL_CULL_FACE);
            gl.glCullFace(GL.GL_BACK);

            dc.setPerFrameStatistic(PerformanceStatistic.IMAGE_TILE_COUNT, this.tileCountName,
                    this.currentTiles.size());
            dc.getGeographicSurfaceTileRenderer().renderTiles(dc, this.currentTiles);

            gl.glPopAttrib();

            if (this.drawBoundingVolumes)
                this.drawBoundingVolumes(dc, this.currentTiles);

            // Check texture expiration. Memory-cached textures are checked for expiration only when an explicit,
            // non-zero expiry time has been set for the layer. If none has been set, the expiry times of the layer's
            // individual levels are used, but only for images in the local file cache, not textures in memory. This is
            // to avoid incurring the overhead of checking expiration of in-memory textures, a very rarely used feature.
            //            if (this.getExpiryTime() > 0 && this.getExpiryTime() <= System.currentTimeMillis())
            //                this.checkTextureExpiration(dc, this.currentTiles);
            for (TextureTile tile : currentTiles) {
                this.requestTexture(dc, tile);
            }

            this.currentTiles.clear();
        }

        this.sendRequests();
        this.requestQ.clear();
    }

    protected abstract BufferedImage createTileImage(TextureTile tile);

    protected static class SimpleRequestTask implements Runnable, Comparable<SimpleRequestTask> {
        protected final OnTheFlyTiledImageLayer layer;
        protected final TextureTile tile;

        protected SimpleRequestTask(TextureTile tile, OnTheFlyTiledImageLayer layer) {
            this.layer = layer;
            this.tile = tile;
        }

        @Override
        public void run() {
            if (Thread.currentThread().isInterrupted())
                return; // the task was cancelled because it's a duplicate or for some other reason

            layer.loadTexture(tile);
        }

        /**
         * @param that
         *            the task to compare
         * 
         * @return -1 if <code>this</code> less than <code>that</code>, 1 if
         *         greater than, 0 if equal
         * 
         * @throws IllegalArgumentException
         *             if <code>that</code> is null
         */
        @Override
        public int compareTo(SimpleRequestTask that) {
            if (that == null) {
                String msg = Logging.getMessage("nullValue.RequestTaskIsNull");
                Logging.logger().severe(msg);
                throw new IllegalArgumentException(msg);
            }
            return this.tile.getPriority() == that.tile.getPriority() ? 0
                    : this.tile.getPriority() < that.tile.getPriority() ? -1 : 1;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            final SimpleRequestTask that = (SimpleRequestTask) o;

            // Don't include layer in comparison so that requests are shared among layers
            return !(tile != null ? !tile.equals(that.tile) : that.tile != null);
        }

        @Override
        public int hashCode() {
            return (tile != null ? tile.hashCode() : 0);
        }

        @Override
        public String toString() {
            return this.tile.toString();
        }
    }
}

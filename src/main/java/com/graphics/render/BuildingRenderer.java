package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.graphics.GfxConfig;
import com.rustorio.World;

/** Слой «здания»: пока рисует только буры, читая мир и ничего в нём не меняя. */
final class BuildingRenderer {

    private final SpriteBatch batch;
    private final Textures textures;
    private final Grid grid;

    BuildingRenderer(SpriteBatch batch, Textures textures, Grid grid) {
        this.batch = batch;
        this.textures = textures;
        this.grid = grid;
    }

    void render(World world) {
        float tile = GfxConfig.TILE;
        batch.begin();
        world.forEachMiner((x, y, miner) ->
                batch.draw(textures.miner[0], grid.x(x), grid.yBottom(y), tile, tile));
        batch.end();
    }
}

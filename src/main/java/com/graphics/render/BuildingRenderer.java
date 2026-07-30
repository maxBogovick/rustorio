package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.graphics.GfxConfig;
import com.rustorio.Appearance;
import com.rustorio.BuildingType;
import com.rustorio.Sprite;
import com.rustorio.World;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Miner;

import static com.badlogic.gdx.utils.Align.top;

/**
 * Слой «здания»: пока рисует только буры, читая мир и ничего в нём не меняя.
 */
final class BuildingRenderer {

    private final SpriteBatch batch;
    private final Textures textures;
    private final BitmapFont font;
    private final Grid grid;

    BuildingRenderer(SpriteBatch batch, Textures textures, Grid grid, BitmapFont font) {
        this.batch = batch;
        this.textures = textures;
        this.grid = grid;
        this.font = font;
    }

    void render(World world, BuildingType selected) {
        float tile = GfxConfig.TILE;
        batch.begin();
        font.draw(batch, hotbar(selected), 20, top - 44);
        world.forEachBuilding((x, y, building) -> {
            Appearance look = building.appearance();
            float px = grid.x(x), py = grid.yBottom(y);
            batch.draw(sprite(look.sprite()), px, py, tile, tile);
            if (look.hasBadge())
                font.draw(batch, Integer.toString(look.badge()), px + 3, py + tile - 3);
        });
        batch.end();
    }


    public static String hotbar(BuildingType selected) {
        StringBuilder sb = new StringBuilder("Build:   ");
        for (BuildingType type : BuildingType.values()) {       // список строит сам себя
            int number = type.ordinal() + 1;
            if (type == selected) sb.append("[ ").append(number).append(' ').append(type.label()).append(" ]    ");
            else                  sb.append("  ").append(number).append(' ').append(type.label()).append("     ");
        }
        return sb.toString();
    }

    private TextureRegion sprite(Sprite sprite) {
        return switch (sprite) {
            case MINER        -> textures.miner[0];
            case CHEST        -> textures.chest;
            case FURNACE_HOT  -> textures.furnaceOn;
            case FURNACE_COLD -> textures.furnaceOff;
        };
    }
}

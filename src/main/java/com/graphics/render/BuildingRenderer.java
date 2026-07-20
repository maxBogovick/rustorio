package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.Appearance;
import com.rustorio.Sprite;
import com.rustorio.World;

/**
 * Слой «здания»: рисует все здания одинаково, не зная их сортов.
 *
 * <p>Раньше здесь был {@code switch} по каждому зданию — бур/ящик/печь со своей логикой рисования.
 * Теперь каждое здание САМО описывает свою внешность ({@link Appearance}: какой спрайт + бейдж),
 * а слой лишь исполняет описание: взять спрайт по имени, нарисовать, при наличии — подписать
 * число. Про сорта зданий отрисовка больше НЕ знает; добавится новое — этот файл не тронут.
 *
 * <p>Единственное, что осталось от «знания картинок», — перевод логического имени {@link Sprite}
 * в конкретную текстуру ({@link #sprite}). Это про АССЕТЫ (какие пиксели), а не про поведение
 * зданий, и switch там — по enum-именам, проверяемый компилятором на полноту.
 */
final class BuildingRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final Textures textures;
    private final BitmapFont font;
    private final Grid grid;

    BuildingRenderer(SpriteBatch batch, ShapeRenderer shapes, Textures textures,
            BitmapFont font, Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.textures = textures;
        this.font = font;
        this.grid = grid;
    }

    void render(World world) {
        float tile = GfxConfig.TILE;
        batch.begin();
        world.forEachBuilding((x, y, building) -> {
            Appearance look = building.appearance();
            float px = grid.x(x);
            float py = grid.yBottom(y);
            batch.draw(sprite(look.sprite()), px, py, tile, tile);
            if (look.hasBadge()) {
                // Число — в правый-верхний угол клетки (загрузка ящика / буфер печи).
                font.draw(batch, Integer.toString(look.badge()), px + 3, py + tile - 3);
            }
        });
        batch.end();
    }

    /** Перевод логического имени спрайта в текстуру атласа. Про ассеты, не про сорта зданий. */
    private TextureRegion sprite(Sprite sprite) {
        return switch (sprite) {
            case MINER -> textures.miner[0];
            case CHEST -> textures.chest;
            case FURNACE_HOT -> textures.furnaceOn;
            case FURNACE_COLD -> textures.furnaceOff;
            case BELT_EMPTY -> textures.belt[0];
            case BELT_FULL -> textures.belt[1];
            case SPLITTER -> textures.splitter;
        };
    }
}

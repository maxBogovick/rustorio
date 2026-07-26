package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.world.World;

/**
 * Слой «здания»: рисует все здания одинаково, не зная их сортов.
 *
 * <p>Раньше здесь был {@code switch} по каждому зданию — бур/ящик/печь со своей логикой рисования.
 * Теперь каждое здание САМО описывает свою внешность ({@link Appearance}: какой спрайт + бейдж),
 * а слой лишь исполняет описание: взять спрайт по имени, нарисовать, при наличии — подписать
 * число. Про сорта зданий отрисовка больше НЕ знает; добавится новое — этот файл не тронут.
 *
 * <p>Перевод логического имени спрайта в текстуру атласа теперь в {@link Textures#forSprite} —
 * им же пользуется {@link HudRenderer} для иконок в панели построек, чтобы здание на карте и
 * его иконка в меню были гарантированно ОДНОЙ и той же картинкой.
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

    void render(World world, TileRange visible) {
        float tile = GfxConfig.TILE;
        batch.begin();
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(),
                (x, y, building) -> {
                    Appearance look = building.appearance();
                    float px = grid.x(x);
                    float py = grid.yBottom(y);
                    batch.draw(textures.forSprite(look.sprite()), px, py, tile, tile);
                    if (look.hasBadge()) {
                        // Top-right corner of the cell (a chest's count / a furnace's buffer).
                        font.draw(batch, Integer.toString(look.badge()), px + 3, py + tile - 3);
                    }
                });
        batch.end();
    }
}

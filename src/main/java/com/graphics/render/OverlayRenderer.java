package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.BuildingType;
import com.rustorio.UndergroundBelt;
import com.rustorio.World;

/**
 * Слой «поверх мира» (подсветки, линии, HUD-панели, тосты). HUD-проход пока пуст — ждёт своей
 * задачи. Мировой проход подсвечивает вход подземной ленты, у которого НЕТ пары в пределах
 * дальности.
 *
 * <p><b>Зачем эта подсветка.</b> Вход и выход подземки визуально ничем не отличаются от рабочей
 * пары — те же спрайты, то же поведение на экране, если по ним ничего не едет. Игрок, поставивший
 * вход дальше {@code MAX_RANGE} от любого подходящего выхода (или с несовпадающим направлением),
 * не получает НИКАКОГО сигнала — тоннель просто тихо не работает, и это неотличимо от «пока
 * нечего везти». Красная рамка вокруг такого входа — тот самый сигнал.
 */
final class OverlayRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;
    private final Grid grid;

    OverlayRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures,
            Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
        this.grid = grid;
    }

    /** Обвести красной рамкой каждый вход подземки без пары — единственная активная подсветка. */
    void renderWorld(World world) {
        float tile = GfxConfig.TILE;
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.T_BAD);
        world.forEachBuilding((x, y, building) -> {
            if (building instanceof UndergroundBelt in
                    && in.type() == BuildingType.UNDERGROUND_IN
                    && in.findPartner(world, x, y) == null) {
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        });
        shapes.end();
    }

    /** HUD-проход — пока пуст. */
    void renderHud() {
    }
}

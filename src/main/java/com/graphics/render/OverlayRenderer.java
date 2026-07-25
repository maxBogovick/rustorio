package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.UndergroundBelt;
import com.rustorio.domain.world.World;

/**
 * Слой «поверх мира» (подсветки, линии, HUD-панели, тосты). HUD-проход пока пуст — ждёт своей
 * задачи. Мировой проход красит две вещи: стрелку направления над каждым зданием, у которого оно
 * вообще есть, и красную рамку вокруг входа подземной ленты без пары в пределах дальности.
 *
 * <p><b>Зачем стрелка.</b> Ни один спрайт в игре не поворачивается под направление (заготовки
 * ассетов этого не умеют — см. {@link Textures}), а лента/печь/туннель держат направление молча
 * внутри себя. Пустая клетка ленты или холодная печь смотрит одинаково в любую сторону — куда
 * пойдёт готовый предмет, было видно только по факту, когда он уже поехал. Стрелка читает
 * {@link Building#outputDirection()} — здание само говорит, куда смотрит, тем же приёмом, что и
 * {@code appearance()}: рендер не разбирает, лента это, печь или туннель. У сплиттера выходов
 * два — второй читается отдельным {@link Building#secondaryOutputDirection()} и рисуется тем же
 * треугольником, просто по другому направлению; для всех остальных зданий он {@code null}, и
 * вторая стрелка просто не рисуется.
 *
 * <p><b>Зачем подсветка тоннеля.</b> Вход и выход подземки визуально ничем не отличаются от
 * рабочей пары — те же спрайты, то же поведение на экране, если по ним ничего не едет. Игрок,
 * поставивший вход дальше {@code MAX_RANGE} от любого подходящего выхода (или с несовпадающим
 * направлением), не получает НИКАКОГО сигнала — тоннель просто тихо не работает, и это неотличимо
 * от «пока нечего везти». Красная рамка вокруг такого входа — тот самый сигнал.
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

    /** Стрелка (или две, у сплиттера) над каждым зданием с направлением; красная рамка — вход-сирота. */
    void renderWorld(World world) {
        float tile = GfxConfig.TILE;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.DIRECTION_ARROW);
        world.forEachBuilding((x, y, building) -> {
            // building.outputDirection(), не Building.unwrap(building).outputDirection(): метод
            // интерфейсный, а SpeedModule обязан (и делегирует, см. его javadoc) отвечать за
            // обёрнутое здание сам — разворачивать здесь незачем, как не разворачиваем ради
            // appearance()/heldItem().
            float cx = grid.x(x) + tile / 2f;
            float cy = grid.yBottom(y) + tile / 2f;
            building.outputDirection().ifPresent(direction -> drawArrow(cx, cy, direction, tile));
            building.secondaryOutputDirection().ifPresent(direction -> drawArrow(cx, cy, direction, tile));
        });
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.T_BAD);
        world.forEachBuilding((x, y, building) -> {
            // Building.unwrap: an upgraded entrance sits in the map as a SpeedModule — without
            // unwrapping, this highlight would silently stop working on it (see the javadoc on
            // UndergroundBelt#findPartner).
            if (Building.unwrap(building) instanceof UndergroundBelt in
                    && in.type() == BuildingType.UNDERGROUND_IN
                    && in.findPartner(world, x, y).isEmpty()) {
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        });
        shapes.end();
    }

    /**
     * Треугольник-стрелка с центром клетки {@code (cx, cy)}, остриём в сторону {@code direction}.
     *
     * <p>{@code direction.dy()} — координата СЕТКИ (вниз = увеличение строки, урок 11), а экранный
     * Y растёт ВВЕРХ ({@link Grid}) — тот же переворот знака, что уже делает {@code Grid.yBottom}
     * для клеток, здесь нужен явно, потому что стрелка считает пиксели сама, в обход {@code Grid}.
     */
    private void drawArrow(float cx, float cy, Direction direction, float tile) {
        float dx = direction.dx();
        float dy = -direction.dy();
        float perpX = -dy;
        float perpY = dx;

        float tipX = cx + dx * tile * 0.34f;
        float tipY = cy + dy * tile * 0.34f;
        float baseX = cx + dx * tile * 0.14f;
        float baseY = cy + dy * tile * 0.14f;
        float halfWidth = tile * 0.13f;

        shapes.triangle(
                tipX, tipY,
                baseX + perpX * halfWidth, baseY + perpY * halfWidth,
                baseX - perpX * halfWidth, baseY - perpY * halfWidth);
    }

    /** HUD-проход — пока пуст. */
    void renderHud() {
    }
}

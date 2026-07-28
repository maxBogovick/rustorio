package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
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
 *
 * <p><b>Поворот спрайта (C-01, DEV_TASKS.md).</b> Раньше ни один спрайт не поворачивался под
 * направление вообще — направление было видно только по стрелке {@link OverlayRenderer} поверх
 * здания. Теперь сам спрайт рисуется повёрнутым на угол {@link #rotationDegrees}, посчитанный из
 * {@code building.outputDirection()}; стрелка для ПЕРВОГО направления стала избыточна вне
 * Alt-режима и убрана оттуда (см. {@link OverlayRenderer} — вторая стрелка сплиттера осталась,
 * это направление больше никак не показано на самом спрайте).
 *
 * <p><b>Анимация ленты (C-03, DEV_TASKS.md).</b> {@link com.rustorio.domain.building.Belt#appearance()}
 * по-прежнему честно различает {@code BELT_EMPTY}/{@code BELT_FULL} по грузу — это осталось в
 * домене нетронутым (не в списке затронутых файлов задачи). Но для РИСОВАНИЯ это различие больше
 * не используется: {@link #animatedBeltSprite} подменяет его на кадр, выбранный по {@link
 * World#currentTick()} — лента «едет» непрерывно, само наличие груза показывает только кружок
 * {@link ItemRenderer} поверх, как и было решено в карточке.
 */
final class BuildingRenderer {

    /** Симуляционных тиков на один кадр анимации ленты — не связано с реальным временем, поэтому пауза (которая тики не двигает) честно останавливает анимацию тоже. */
    private static final long BELT_ANIM_TICKS_PER_FRAME = 10;

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
        long tick = world.currentTick();
        batch.begin();
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(),
                (x, y, building) -> {
                    Appearance look = building.appearance();
                    float px = grid.x(x);
                    float py = grid.yBottom(y);
                    // footprintWidth/Height (X-03, DEV_TASKS.md) — 1 for every building except
                    // ASSEMBLER, so this is a no-op scale factor for everything else. Square
                    // footprint (see Building#footprintHeight) keeps this valid under rotation
                    // too: width and height stay equal, so there's no non-square sprite to
                    // mis-rotate about an off-center pivot.
                    float w = tile * building.footprintWidth();
                    float h = tile * building.footprintHeight();
                    TextureRegion region = textures.forSprite(animatedBeltSprite(look.sprite(), tick));
                    float rotation = building.outputDirection().map(BuildingRenderer::rotationDegrees).orElse(0f);
                    batch.draw(region, px, py, w / 2f, h / 2f, w, h, 1f, 1f, rotation);
                    if (look.hasBadge()) {
                        // Top-right corner of the cell (a chest's count / a furnace's buffer) —
                        // fixed in screen space, deliberately NOT rotated with the sprite above.
                        // "+ h", not "+ tile": for a multi-cell building this is the top of the
                        // WHOLE footprint, not just the anchor's own single cell.
                        font.draw(batch, Integer.toString(look.badge()), px + 3, py + h - 3);
                    }
                });
        batch.end();

        renderMarkers(world, visible, tile);
    }

    /**
     * {@code BELT_EMPTY}/{@code BELT_FULL} both mean "this is a belt" for drawing purposes now
     * (C-03) — which of the two real texture frames ({@code belt[0]}/{@code belt[1]}) shows is
     * picked by {@code tick}, not by which constant {@code Belt#appearance} happened to report.
     * Empty for every non-belt sprite — the caller falls back to the plain, un-animated lookup.
     */
    private static Sprite animatedBeltSprite(Sprite sprite, long tick) {
        if (sprite != Sprite.BELT_EMPTY && sprite != Sprite.BELT_FULL) {
            return sprite;
        }
        boolean firstFrame = (tick / BELT_ANIM_TICKS_PER_FRAME) % 2 == 0;
        return firstFrame ? Sprite.BELT_EMPTY : Sprite.BELT_FULL;
    }

    /**
     * Counterclockwise degrees {@link SpriteBatch#draw} expects, matching the same grid-to-screen
     * Y-flip {@link OverlayRenderer#drawArrow} already applies (grid {@code dy} down = increasing
     * row, screen Y up) — base art is drawn facing {@link Direction#RIGHT}, so that's the 0° case.
     */
    private static float rotationDegrees(Direction direction) {
        return switch (direction) {
            case RIGHT -> 0f;
            case UP -> 90f;
            case LEFT -> 180f;
            case DOWN -> 270f;
        };
    }

    /**
     * Two small overlays, one {@link ShapeRenderer} pass, not interleaved with the {@link
     * SpriteBatch} pass above (libGDX doesn't allow both active at once):
     *
     * <ul>
     *   <li>a colored square in the bottom-left corner of any cell whose building isn't {@code
     *       WORKING} (F-01, DEV_TASKS.md; §3.3/§6.5 of the design audit);
     *   <li>a colored circle in the top-right corner of a furnace/press with a recipe hint (F-03,
     *       DEV_TASKS.md) — which item it's set to produce, visible without opening the
     *       inspection panel.
     * </ul>
     *
     * <p>Bottom-left / top-right, not the badge's top-left corner, so none of the three ever
     * overlap on the same cell.
     */
    private void renderMarkers(World world, TileRange visible, float tile) {
        float markerSize = tile * 0.28f;
        float recipeRadius = tile * 0.14f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(),
                (x, y, building) -> {
                    Appearance look = building.appearance();
                    Palette.statusColor(look.status()).ifPresent(color -> {
                        shapes.setColor(color);
                        shapes.rect(grid.x(x), grid.yBottom(y), markerSize, markerSize);
                    });
                    Item hint = look.recipeHint();
                    if (hint != null) {
                        // "+ w"/"+ h", not "+ tile" (X-03, DEV_TASKS.md): top-RIGHT of the WHOLE
                        // footprint for a multi-cell building, same reasoning as the badge above.
                        float w = tile * building.footprintWidth();
                        float h = tile * building.footprintHeight();
                        shapes.setColor(Palette.itemColor(hint));
                        shapes.circle(grid.x(x) + w - recipeRadius - 2f,
                                grid.yBottom(y) + h - recipeRadius - 2f, recipeRadius, 12);
                    }
                });
        shapes.end();
    }
}

package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidFill;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.BuildingPrototype;
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

    /** Стороны для стыков труб — статикой, а не {@code Direction.values()}: тот клонирует массив на каждом вызове, а этот цикл идёт на каждое видимое здание каждый кадр (ловушка graphics.md, как в {@code HotbarLayout}). */
    private static final Direction[] SIDES = Direction.values();

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
    private static ContentId animatedBeltSprite(ContentId sprite, long tick) {
        if (sprite != VanillaSprites.BELT_EMPTY && sprite != VanillaSprites.BELT_FULL) {
            return sprite;
        }
        boolean firstFrame = (tick / BELT_ANIM_TICKS_PER_FRAME) % 2 == 0;
        return firstFrame ? VanillaSprites.BELT_EMPTY : VanillaSprites.BELT_FULL;
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
     * A short bar from the tile's centre ({@code (px, py)} is its bottom-left) to the edge on {@code
     * side}, drawn in the current {@link ShapeRenderer} colour. Grid row grows downward while screen
     * Y grows upward, so {@link Direction#DOWN} reaches toward smaller Y — the same flip {@link
     * OverlayRenderer#drawArrow} makes.
     */
    private void drawJoint(float px, float py, float tile, Direction side) {
        float cx = px + tile / 2f;
        float cy = py + tile / 2f;
        float thick = tile * 0.22f;
        float reach = tile / 2f; // centre to the cell edge
        switch (side) {
            case RIGHT -> shapes.rect(cx, cy - thick / 2f, reach, thick);
            case LEFT -> shapes.rect(cx - reach, cy - thick / 2f, reach, thick);
            case UP -> shapes.rect(cx - thick / 2f, cy, thick, reach);
            case DOWN -> shapes.rect(cx - thick / 2f, cy - reach, thick, reach);
        }
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
     *   <li>a fill bar along the bottom edge of a fluid tile whose {@link Appearance#fill()} is
     *       set — a track in {@link Palette#FLUID_BAR_TRACK} with the filled part in the fluid's own
     *       colour, so "how full" reads off the map without the inspection panel.
     * </ul>
     *
     * <p>Bottom-left / top-right, not the badge's top-left corner, so none of the three ever
     * overlap on the same cell. The fill bar spans the bottom edge, but only a {@code Pipe}/tank
     * ever sets {@code fill}, and those carry no status marker, badge or recipe hint — so it never
     * shares a cell with the bottom-left status square in practice.
     */
    private void renderMarkers(World world, TileRange visible, float tile) {
        float markerSize = tile * 0.28f;
        float recipeRadius = tile * 0.14f;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(),
                (x, y, building) -> {
                    Appearance look = building.appearance();
                    // A thin accent stripe along the top edge marking the building's system (power
                    // or fluid) — the category is read from prototype data, not the class (see
                    // BuildingAccent). Top edge, clear of the bottom fill bar.
                    //
                    // Checked against NONE before asking Palette, not via ifPresent: a capturing
                    // lambda is an allocation, and this runs for every visible building every frame
                    // (graphics.md). Ordinary buildings — nearly all of them — now leave here.
                    BuildingPrototype proto = world.buildingFactory().prototype(building.prototypeId());
                    BuildingAccent accent = BuildingAccent.forPrototype(proto);
                    if (accent != BuildingAccent.NONE) {
                        float fw = tile * building.footprintWidth();
                        float fh = tile * building.footprintHeight();
                        float stripeH = tile * 0.12f;
                        shapes.setColor(Palette.accentColor(accent).orElseThrow());
                        shapes.rect(grid.x(x), grid.yBottom(y) + fh - stripeH, fw, stripeH);
                    }
                    Palette.statusColor(look.status()).ifPresent(color -> {
                        shapes.setColor(color);
                        shapes.rect(grid.x(x), grid.yBottom(y), markerSize, markerSize);
                    });
                    // Стыки трубы: отросток от центра клетки к каждому соседу по той же сети, чтобы
                    // ряд труб читался соединённым так же, как шевроны ленты читаются направленными.
                    // Только там, где World говорит «одна сеть», — граница воды и пара остаётся
                    // видимо несостыкованной. Один запрос на здание, не по одному на сторону:
                    // не-жидкостный тайл отдаёт 0 и не стоит ни одного лишнего поиска по карте.
                    int joints = world.fluidJoints(x, y);
                    if (joints != 0) {
                        shapes.setColor(Palette.PIPE_JOINT);
                        for (Direction side : SIDES) {
                            if (World.hasJoint(joints, side)) {
                                drawJoint(grid.x(x), grid.yBottom(y), tile, side);
                            }
                        }
                    }
                    FluidFill fill = look.fill();
                    if (fill != null) {
                        float w = tile * building.footprintWidth();
                        float pad = tile * 0.1f;
                        float trackW = w - 2f * pad;
                        float barH = tile * 0.16f;
                        float bx = grid.x(x) + pad;
                        float by = grid.yBottom(y) + pad;
                        shapes.setColor(Palette.FLUID_BAR_TRACK);
                        shapes.rect(bx, by, trackW, barH);
                        shapes.setColor(Palette.fluidColor(fill.fluid()));
                        shapes.rect(bx, by, trackW * fill.percent() / 100f, barH);
                    }
                    ItemType hint = look.recipeHint();
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

package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.core.Appearance;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.game.GameState;
import com.rustorio.model.Assembler;
import com.rustorio.model.Belt;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Lab;
import com.rustorio.model.Miner;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;

/**
 * Слой «здания»: спрайты, накладки (стрелки, полоски прогресса, «призрак» под
 * курсором) и рамки-подсказки. Три отдельных прохода, потому что {@link SpriteBatch}
 * и {@link ShapeRenderer} нельзя рисовать вперемешку без переоткрытия.
 *
 * <p><b>Графика больше не знает типы зданий — кроме спрайтов.</b> Накладки и рамки
 * рисуются по {@link Appearance}, который здание рассказывает о себе само. Поэтому новое
 * здание сюда не заглядывает: пока у него нет спрайта, {@link #renderSprites} рисует его
 * подписанной плашкой (ветка {@code default}), а стрелку и полоску возьмёт из его
 * {@code appearance()}. Спрайты восьми встроенных зданий оставлены как есть — это «арт»,
 * его место здесь; добавить свой спрайт новому зданию — отдельная необязательная задача.
 */
final class BuildingRenderer {

    /** Смена кадров ленты в секунду. */
    private static final float BELT_ANIM_SPEED = 4.0f;
    private static final float TILE = Config.TILE;

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

    /** Проход 1: спрайты зданий. {@code elapsed} — настенные часы для анимации ленты. */
    void renderSprites(World world, TileRange range, float elapsed) {
        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                // Спрайты — «арт» встроенных зданий. Ветка default ловит любое здание БЕЗ
                // спрайта (в том числе новое, добавленное студентом) и рисует его подписанной
                // плашкой. Поэтому новое здание не требует правки этого switch.
                switch (b) {
                    case Miner m -> {
                        int frame = (int) (clamp(m.progressFraction(), 0f, 0.999f) * 3);
                        batch.draw(textures.miner[frame], px, py, TILE, TILE);
                    }
                    case Belt belt -> {
                        int frame = (int) (elapsed * BELT_ANIM_SPEED) % 2;
                        batch.draw(textures.belt[frame], px, py, TILE / 2, TILE / 2,
                                TILE, TILE, 1f, 1f, beltRotation(belt.dir()));
                    }
                    case Furnace f -> batch.draw(
                            f.hasStock() ? textures.furnaceOn : textures.furnaceOff,
                            px, py, TILE, TILE);
                    // `_` — здание известно по типу, а само значение здесь не нужно.
                    case Assembler _ -> batch.draw(textures.assembler, px, py, TILE, TILE);
                    case Chest _ -> batch.draw(textures.chest, px, py, TILE, TILE);
                    case Splitter s -> batch.draw(textures.splitter, px, py, TILE / 2, TILE / 2,
                            TILE, TILE, 1f, 1f, beltRotation(s.dir()));
                    case UndergroundBelt u -> batch.draw(textures.underground, px, py,
                            TILE / 2, TILE / 2, TILE, TILE, 1f, 1f, beltRotation(u.dir()));
                    case Lab _ -> batch.draw(textures.lab, px, py, TILE, TILE);
                    default -> drawPlate(b.appearance().label(), px, py);
                }
            }
        }
        batch.end();
    }

    /** Проход 2: стрелки направлений, полоски прогресса, «призрак» будущего здания. */
    void renderOverlays(World world, GameState game, TileRange range) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                // Никакого switch по типу: здание само сказало, что показать.
                Appearance look = b.appearance();
                if (look.arrow() != null) {
                    drawArrow(px, py, look.arrow(), look.working());
                }
                if (look.hasProgress()) {
                    drawProgressBar(px, py, look.progress());
                }
            }
        }
        // «Призрак» будущего здания под курсором — полупрозрачная стрелка.
        game.hover().ifPresent(cell -> drawArrow(
                grid.x(cell.x()), grid.yBottom(cell.y()), game.direction(), Palette.GHOST));
        shapes.end();
    }

    /** Проход 3: рамки — «тревога» здания (бур не на руде) и белая рамка под курсором. */
    void renderOutlines(World world, GameState game, TileRange range) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b != null && b.appearance().alert()) {
                    shapes.setColor(Palette.IDLE);
                    shapes.rect(grid.x(x) + 2, grid.yBottom(y) + 2, TILE - 4, TILE - 4);
                }
            }
        }
        game.hover().ifPresent(cell -> {
            shapes.setColor(Color.WHITE);
            shapes.rect(grid.x(cell.x()), grid.yBottom(cell.y()), TILE, TILE);
        });
        shapes.end();
    }

    /**
     * Подписанная плашка для здания без своего спрайта: крашеный квадрат + имя.
     *
     * <p>Цвет выводится из имени (стабильно: одно здание — всегда один цвет), чтобы разные
     * виды отличались на глаз. Рисуется белым регионом через {@code batch.setColor}; цвет
     * обязательно возвращаем в белый, иначе следующий спрайт в этом же проходе покрасился бы.
     */
    private void drawPlate(String label, float px, float py) {
        int h = label.hashCode();
        float r = 0.30f + 0.55f * ((h & 0xFF) / 255f);
        float g = 0.30f + 0.55f * (((h >> 8) & 0xFF) / 255f);
        float b = 0.30f + 0.55f * (((h >> 16) & 0xFF) / 255f);
        batch.setColor(r, g, b, 1f);
        batch.draw(textures.white, px + 2, py + 2, TILE - 4, TILE - 4);
        batch.setColor(Color.WHITE);
        font.getData().setScale(0.7f);
        font.draw(batch, label, px + 4, py + TILE - 5);
        font.getData().setScale(1f);
    }

    /** Полоска прогресса у нижнего края клетки. */
    private void drawProgressBar(float px, float py, float fraction) {
        float pad = 3f;
        float inner = TILE - 2 * pad;
        shapes.setColor(Palette.BAR);
        shapes.rect(px + pad, py + pad, inner * fraction, 4f);
    }

    /**
     * Треугольник-стрелка в центре клетки, смотрящий в направлении {@code dir}.
     * Цвет — зелёный, пока здание работает, красный — пока простаивает.
     */
    private void drawArrow(float px, float py, Direction dir, boolean active) {
        drawArrow(px, py, dir, active ? Palette.WORKING : Palette.IDLE);
    }

    private void drawArrow(float px, float py, Direction dir, Color color) {
        float cx = px + TILE / 2f;
        float cy = py + TILE / 2f;
        float r = TILE * 0.26f;
        // Визуальный вектор направления в координатах Y-вверх: экранный «низ»
        // (South, dy=+1) — это -Y, поэтому vy = -dir.dy().
        float vx = dir.dx();
        float vy = -dir.dy();
        float perpX = -vy;
        float perpY = vx;
        float tipX = cx + vx * r;
        float tipY = cy + vy * r;
        float base1X = cx - vx * r * 0.6f + perpX * r * 0.7f;
        float base1Y = cy - vy * r * 0.6f + perpY * r * 0.7f;
        float base2X = cx - vx * r * 0.6f - perpX * r * 0.7f;
        float base2Y = cy - vy * r * 0.6f - perpY * r * 0.7f;
        shapes.setColor(color);
        shapes.triangle(tipX, tipY, base1X, base1Y, base2X, base2Y);
    }

    /** Угол поворота спрайта ленты (спрайт нарисован вдоль East), Y-вверх. */
    private static float beltRotation(Direction dir) {
        return switch (dir) {
            case EAST -> 0f;
            case NORTH -> 90f;
            case WEST -> 180f;
            case SOUTH -> -90f;
        };
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}

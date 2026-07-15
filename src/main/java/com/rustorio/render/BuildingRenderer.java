package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
import com.rustorio.model.Tile;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;

/**
 * Слой «здания»: спрайты, накладки (стрелки, полоски прогресса, «призрак» под
 * курсором) и рамки-подсказки. Три отдельных прохода, потому что {@link SpriteBatch}
 * и {@link ShapeRenderer} нельзя рисовать вперемешку без переоткрытия.
 */
final class BuildingRenderer {

    /** Смена кадров ленты в секунду. */
    private static final float BELT_ANIM_SPEED = 4.0f;
    private static final float TILE = Config.TILE;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final Textures textures;
    private final Grid grid;

    BuildingRenderer(SpriteBatch batch, ShapeRenderer shapes, Textures textures, Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.textures = textures;
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
                // Исчерпывающий switch по sealed-типу: без `default`. Добавишь
                // новое здание — компилятор ПОТРЕБУЕТ здесь новую ветку.
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
                Tile tile = world.tile(x, y);
                Building b = tile.building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                switch (b) {
                    case Miner m ->
                            drawArrow(px, py, m.dir(), m.outputItem().isEmpty() && tile.hasOre());
                    case Furnace f -> {
                        drawArrow(px, py, f.dir(), f.isWorking());
                        drawProgressBar(px, py, f.progressFraction());
                    }
                    case Assembler a -> {
                        drawArrow(px, py, a.dir(), a.isWorking());
                        drawProgressBar(px, py, a.progressFraction());
                    }
                    case Belt _ -> { /* у ленты стрелки нет */ }
                    case Chest _ -> { /* у ящика накладок нет */ }
                    case Lab lab -> drawProgressBar(px, py, lab.progressFraction());
                    case Splitter _, UndergroundBelt _ -> { /* накладок нет */ }
                }
            }
        }
        // «Призрак» будущего здания под курсором — полупрозрачная стрелка.
        game.hover().ifPresent(cell -> drawArrow(
                grid.x(cell.x()), grid.yBottom(cell.y()), game.direction(), Palette.GHOST));
        shapes.end();
    }

    /** Проход 3: рамки — «бур не на руде» и белая рамка под курсором. */
    void renderOutlines(World world, GameState game, TileRange range) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Tile tile = world.tile(x, y);
                if (tile.building() instanceof Miner && !tile.hasOre()) {
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

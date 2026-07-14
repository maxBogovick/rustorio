package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.game.GameState;
import com.rustorio.model.Assembler;
import com.rustorio.model.Belt;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Miner;
import com.rustorio.model.Tile;
import com.rustorio.model.World;

/**
 * Отрисовка кадра. ЗОЛОТОЕ ПРАВИЛО (перенесено из Rust-версии): рендер только
 * ЧИТАЕТ {@link GameState} и рисует, НИКОГДА не меняя мир. Благодаря этому «что
 * происходит в игре» и «как это выглядит» независимы.
 *
 * <p><b>Про систему координат.</b> macroquad рисовал с началом сверху-слева
 * (ось Y вниз), а libGDX — снизу-слева (ось Y вверх). Весь перевод сетки в
 * экран собран в двух методах — {@link #tileX(int)} и {@link #tileYBottom(int)};
 * дальше внутренняя математика (стрелки, полоски) совпадает с оригиналом.
 *
 * <p><b>Про «проходы».</b> {@link SpriteBatch} и {@link ShapeRenderer} нельзя
 * рисовать вперемешку без переоткрытия. Поэтому кадр идёт слоями:
 * фон → сетка → спрайты зданий → накладки (стрелки/полоски) → рамки →
 * предметы и текст. Так на весь кадр всего несколько {@code begin/end}.
 */
public final class Renderer implements Disposable {

    // ── Палитра (перенесена из render.rs) ────────────────────────────
    private static final Color C_BG = rgb(26, 26, 31);
    private static final Color C_GROUND = rgb(38, 41, 46);
    private static final Color C_ORE = rgb(51, 71, 115);
    private static final Color C_GRID = new Color(0, 0, 0, 64 / 255f);
    private static final Color C_HINT = rgb(179, 179, 199);
    private static final Color C_WORKING = Color.GREEN;
    private static final Color C_IDLE = Color.RED;
    private static final Color C_BAR = Color.YELLOW;
    private static final Color C_GHOST = new Color(1, 1, 1, 0.6f);

    /** Смена кадров ленты в секунду. */
    private static final float BELT_ANIM_SPEED = 4.0f;
    private static final float TILE = Config.TILE;

    private final OrthographicCamera camera;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;
    private final float worldHeight;

    /** Настенные часы для анимации ленты (тикают даже на паузе, как в Rust). */
    private float elapsed = 0f;

    public Renderer(Textures textures) {
        this.textures = textures;
        this.worldHeight = Config.windowHeight();
        this.camera = new OrthographicCamera();
        this.camera.setToOrtho(false, Config.windowWidth(), Config.windowHeight());
        this.camera.update();
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont(); // встроенный 15px Arial — хватает для HUD
    }

    /** Нарисовать весь кадр по текущему состоянию игры. */
    public void render(GameState game, float delta) {
        elapsed += delta;
        World world = game.world();

        Gdx.gl.glClearColor(C_BG.r, C_BG.g, C_BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Для полупрозрачных сетки и «призрака». Функцию смешивания задаём явно:
        // проход сетки идёт до первого SpriteBatch.begin(), который иначе
        // выставил бы её за нас, — без этого alpha не смешивалась бы.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        batch.setProjectionMatrix(camera.combined);
        shapes.setProjectionMatrix(camera.combined);

        drawTileBackgrounds(world);   // 1. фон клеток (руда/земля)
        drawGrid(world);              // 2. сетка
        drawBuildingSprites(world);   // 3. спрайты зданий
        drawOverlays(world, game);    // 4. стрелки, полоски прогресса, «призрак»
        drawOutlines(world, game);    // 5. рамки (нет руды под буром, курсор)
        drawItemsAndText(world, game); // 6. предметы поверх + HUD
    }

    // ── Проход 1: фон клеток ──────────────────────────────────────────
    private void drawTileBackgrounds(World world) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                shapes.setColor(world.tile(x, y).hasOre() ? C_ORE : C_GROUND);
                shapes.rect(tileX(x), tileYBottom(y), TILE, TILE);
            }
        }
        shapes.end();
    }

    // ── Проход 2: сетка ───────────────────────────────────────────────
    private void drawGrid(World world) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(C_GRID);
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                shapes.rect(tileX(x), tileYBottom(y), TILE, TILE);
            }
        }
        shapes.end();
    }

    // ── Проход 3: спрайты зданий ──────────────────────────────────────
    private void drawBuildingSprites(World world) {
        batch.begin();
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = tileX(x);
                float py = tileYBottom(y);
                // Исчерпывающий switch по sealed-типу: без `default`. Добавишь
                // новое здание — компилятор ПОТРЕБУЕТ здесь новую ветку.
                switch (b) {
                    case Miner m -> {
                        float progress = clamp(1f - m.cooldown() / Config.MINER_TIME, 0f, 0.999f);
                        int frame = (int) (progress * 3);
                        batch.draw(textures.miner[frame], px, py, TILE, TILE);
                    }
                    case Belt belt -> {
                        int frame = (int) (elapsed * BELT_ANIM_SPEED) % 2;
                        batch.draw(textures.belt[frame], px, py, TILE / 2, TILE / 2,
                                TILE, TILE, 1f, 1f, beltRotation(belt.dir()));
                    }
                    case Furnace f -> batch.draw(
                            f.input().isPresent() ? textures.furnaceOn : textures.furnaceOff,
                            px, py, TILE, TILE);
                    // `_` — здание известно по типу, а само значение здесь не нужно.
                    case Assembler _ -> batch.draw(textures.assembler, px, py, TILE, TILE);
                    case Chest _ -> batch.draw(textures.chest, px, py, TILE, TILE);
                }
            }
        }
        batch.end();
    }

    // ── Проход 4: накладки (стрелки, полоски, «призрак») ──────────────
    private void drawOverlays(World world, GameState game) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Tile tile = world.tile(x, y);
                Building b = tile.building();
                if (b == null) {
                    continue;
                }
                float px = tileX(x);
                float py = tileYBottom(y);
                switch (b) {
                    case Miner m ->
                            drawArrow(px, py, m.dir(), m.outputItem().isEmpty() && tile.hasOre());
                    case Furnace f -> {
                        drawArrow(px, py, f.dir(), f.input().isPresent() && f.outputItem().isEmpty());
                        drawProgressBar(px, py, f.progressFraction());
                    }
                    case Assembler a -> {
                        drawArrow(px, py, a.dir(), a.input().isPresent() && a.outputItem().isEmpty());
                        drawProgressBar(px, py, a.progressFraction());
                    }
                    case Belt _ -> { /* у ленты стрелки нет */ }
                    case Chest _ -> { /* у ящика накладок нет */ }
                }
            }
        }
        // «Призрак» будущего здания под курсором — полупрозрачная стрелка.
        game.hover().ifPresent(cell -> drawArrow(
                tileX(cell.x()), tileYBottom(cell.y()), game.direction(), C_GHOST));
        shapes.end();
    }

    // ── Проход 5: рамки ───────────────────────────────────────────────
    private void drawOutlines(World world, GameState game) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Tile tile = world.tile(x, y);
                // Подсказка: бур поставлен не на руду — красная рамка.
                if (tile.building() instanceof Miner && !tile.hasOre()) {
                    shapes.setColor(C_IDLE);
                    shapes.rect(tileX(x) + 2, tileYBottom(y) + 2, TILE - 4, TILE - 4);
                }
            }
        }
        // Белая рамка под курсором.
        game.hover().ifPresent(cell -> {
            shapes.setColor(Color.WHITE);
            shapes.rect(tileX(cell.x()), tileYBottom(cell.y()), TILE, TILE);
        });
        shapes.end();
    }

    // ── Проход 6: предметы поверх зданий + HUD ────────────────────────
    private void drawItemsAndText(World world, GameState game) {
        batch.begin();
        for (int y = 0; y < world.height(); y++) {
            for (int x = 0; x < world.width(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = tileX(x);
                float py = tileYBottom(y);
                switch (b) {
                    case Miner m -> m.outputItem().ifPresent(it -> drawItemIcon(px, py, it));
                    case Belt belt -> belt.item().ifPresent(it -> drawItemIcon(px, py, it));
                    case Furnace f -> shownItem(f.outputItem().orElse(null), f.input().orElse(null))
                            .ifPresent(it -> drawItemIcon(px, py, it));
                    case Assembler a -> shownItem(a.outputItem().orElse(null), a.input().orElse(null))
                            .ifPresent(it -> drawItemIcon(px, py, it));
                    case Chest c -> {
                        font.getData().setScale(0.9f);
                        font.setColor(Color.WHITE);
                        font.draw(batch, Integer.toString(c.items()), px + 6, py + 20);
                    }
                }
            }
        }
        drawHud(game);
        batch.end();
    }

    private void drawHud(GameState game) {
        String status = "Tool: " + game.tool().displayName()
                + "   Dir: " + game.direction().shortName()
                + (game.isPaused() ? "   [PAUSED]" : "");
        font.setColor(Color.WHITE);
        font.getData().setScale(1.15f);
        font.draw(batch, status, 20, worldHeight - 16);
        font.setColor(C_HINT);
        font.getData().setScale(0.9f);
        font.draw(batch,
                "1 Miner  2 Belt  3 Furnace  4 Chest  5 Assembler    |    "
                        + "LMB place   RMB remove   R rotate   Space pause",
                20, worldHeight - 46);
        font.getData().setScale(1f);
    }

    // ── Мелкие помощники отрисовки ────────────────────────────────────

    /** Полоска прогресса у нижнего края клетки. */
    private void drawProgressBar(float px, float py, float fraction) {
        float pad = 3f;
        float inner = TILE - 2 * pad;
        shapes.setColor(C_BAR);
        shapes.rect(px + pad, py + pad, inner * fraction, 4f);
    }

    /**
     * Треугольник-стрелка в центре клетки, смотрящий в направлении {@code dir}.
     * Цвет — зелёный, пока здание работает, красный — пока простаивает.
     */
    private void drawArrow(float px, float py, Direction dir, boolean active) {
        drawArrow(px, py, dir, active ? C_WORKING : C_IDLE);
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

    /** Маленькая иконка предмета в нижнем-правом углу клетки. */
    private void drawItemIcon(float px, float py, Item item) {
        float size = TILE * 0.4f;
        batch.draw(textures.itemTexture(item), px + TILE - size - 2, py + 2, size, size);
    }

    /** Что показать на машине: приоритет у продукта, иначе — сырьё. */
    private static java.util.Optional<Item> shownItem(Item output, Item input) {
        if (output != null) {
            return java.util.Optional.of(output);
        }
        return java.util.Optional.ofNullable(input);
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

    // ── Перевод «сетка → экран» (единственное место Y-flip) ───────────

    /** X левого края клетки-столбца {@code gx}. */
    private float tileX(int gx) {
        return Config.OFFSET_X + gx * TILE;
    }

    /**
     * Y НИЖНЕГО края клетки-строки {@code gy}. Строка 0 — сверху экрана,
     * поэтому переворачиваем относительно высоты окна.
     */
    private float tileYBottom(int gy) {
        return worldHeight - Config.OFFSET_Y - (gy + 1) * TILE;
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

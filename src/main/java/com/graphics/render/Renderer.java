package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.game.GameState;
import com.rustorio.model.World;

/**
 * Дирижёр отрисовки. ЗОЛОТОЕ ПРАВИЛО (перенесено из Rust-версии): рендер только
 * ЧИТАЕТ {@link GameState} и рисует, НИКОГДА не меняя мир.
 *
 * <p>Сам он не рисует ни одной фигуры — только владеет общими ресурсами
 * ({@link SpriteBatch}, {@link ShapeRenderer}, шрифт) и вызывает слои по порядку:
 * земля → здания → предметы → HUD. Каждый слой — маленький класс со своей зоной
 * ответственности; правишь внешний вид зданий — открываешь {@link BuildingRenderer},
 * остальные файлы не трогаешь.
 *
 * <p>Мировые слои рисуются через матрицу камеры (скролл/зум), HUD — через её же
 * {@code hudMatrix()}, прибитую к окну. Обход клеток везде идёт по
 * {@link GameCamera#visibleTiles}: что за кадром — не рисуется вовсе.
 */
public final class Renderer implements Disposable {

    private final GameCamera camera;
    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    private final WorldRenderer worldRenderer;
    private final BuildingRenderer buildingRenderer;
    private final ItemRenderer itemRenderer;
    private final OverlayRenderer overlayRenderer;
    private final HudRenderer hudRenderer;

    /** Настенные часы для анимации ленты (тикают даже на паузе, как в Rust). */
    private float elapsed = 0f;

    public Renderer(Textures textures, GameCamera camera, World world) {
        this.camera = camera;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont(); // встроенный 15px Arial — хватает для HUD

        Grid grid = new Grid(world.height());
        this.worldRenderer = new WorldRenderer(shapes, grid);
        this.buildingRenderer = new BuildingRenderer(batch, shapes, textures, font, grid);
        this.itemRenderer = new ItemRenderer(batch, font, textures, grid);
        this.overlayRenderer = new OverlayRenderer(batch, shapes, font, textures, grid);
        this.hudRenderer = new HudRenderer(batch, font);
    }

    /** Нарисовать весь кадр по текущему состоянию игры. */
    public void render(GameState game, float delta) {
        elapsed += delta;
        World world = game.world();

        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Для полупрозрачных сетки и «призрака». Функцию смешивания задаём явно:
        // проход сетки идёт до первого SpriteBatch.begin(), который иначе
        // выставил бы её за нас, — без этого alpha не смешивалась бы.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Мировые слои — глазами камеры.
        batch.setProjectionMatrix(camera.combined());
        shapes.setProjectionMatrix(camera.combined());
        TileRange visible = camera.visibleTiles(world.width());

        worldRenderer.render(world, visible);                       // 1. фон + сетка
        buildingRenderer.renderSprites(world, visible, elapsed);    // 2. спрайты зданий
        buildingRenderer.renderOverlays(world, game, visible);      // 3. стрелки, прогресс
        buildingRenderer.renderOutlines(world, game, visible);      // 4. рамки
        overlayRenderer.renderWorld(game.overlay());                // 5. подсветки клеток
        itemRenderer.render(world, game, visible);                  // 6. предметы

        // HUD — поверх всего, в координатах окна.
        batch.setProjectionMatrix(camera.hudMatrix());
        hudRenderer.render(game);                                   // 7. текст HUD
        overlayRenderer.renderHud(game.overlay());                  // 8. панели, уведомления
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

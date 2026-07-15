package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.game.GameState;
import com.rustorio.model.World;

/**
 * Дирижёр отрисовки. ЗОЛОТОЕ ПРАВИЛО: рендер только ЧИТАЕТ {@link GameState}
 * и рисует, НИКОГДА не меняя мир.
 *
 * <p>Сам он не рисует ни одной фигуры — только владеет общими ресурсами
 * ({@link SpriteBatch}, {@link ShapeRenderer}, шрифт) и вызывает слои по порядку:
 * земля → HUD. По ходу курса между ними встанут слои зданий и предметов — каждый
 * будет маленьким классом со своей зоной ответственности.
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

    /**
     * Атлас спрайтов. Пока его никто не читает: первый потребитель появится
     * вместе с первым зданием (слой {@code BuildingRenderer}).
     */
    @SuppressWarnings("unused")
    private final Textures textures;

    private final WorldRenderer worldRenderer;
    private final HudRenderer hudRenderer;

    public Renderer(Textures textures, GameCamera camera, World world) {
        this.textures = textures;
        this.camera = camera;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont(); // встроенный 15px Arial — хватает для HUD

        Grid grid = new Grid(world.height());
        this.worldRenderer = new WorldRenderer(shapes, grid);
        this.hudRenderer = new HudRenderer(batch, font);
    }

    /** Нарисовать весь кадр по текущему состоянию игры. */
    public void render(GameState game, float delta) {
        World world = game.world();

        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        // Для полупрозрачной сетки. Функцию смешивания задаём явно: проход сетки
        // идёт до первого SpriteBatch.begin(), который иначе выставил бы её за
        // нас, — без этого alpha не смешивалась бы.
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Мировые слои — глазами камеры.
        batch.setProjectionMatrix(camera.combined());
        shapes.setProjectionMatrix(camera.combined());
        TileRange visible = camera.visibleTiles(world.width());

        worldRenderer.render(world, visible, game.hover().orElse(null)); // 1. фон + сетка

        // HUD — поверх всего, в координатах окна.
        batch.setProjectionMatrix(camera.hudMatrix());
        hudRenderer.render(game);                                        // 2. текст HUD
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

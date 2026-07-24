package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.graphics.GfxConfig;
import com.rustorio.BuildingType;
import com.rustorio.Direction;
import com.rustorio.ProductionLog;
import com.rustorio.World;

/**
 * Дирижёр отрисовки: владеет общими ресурсами и вызывает слои по порядку — земля, здания,
 * предметы, HUD. Рендер только ЧИТАЕТ мир и рисует, НИКОГДА его не меняя.
 *
 * <p>Живые слои — земля (карта с рудой), здания, груз, который они держат в пути, и подсветка
 * непарных входов подземки ({@code OverlayRenderer}). HUD-проход наложений всё ещё пуст.
 *
 * <p>Мир и HUD рисуются в РАЗНЫЕ пиксельные области окна ({@code glViewport}): мир — в узкую
 * полосу между верхней и нижней панелями ({@link GameCamera#resize}), HUD — во всё окно целиком.
 * Раньше оба рисовались в одну и ту же область, и панели HUD просто перекрывали часть карты —
 * та её часть была навсегда не видна игроку, хоть строить там технически можно было.
 *
 * <p><b>{@code glViewport} — в пикселях БЭКБУФЕРА, не в «логических» точках окна.</b> На
 * Retina-экранах (macOS) {@code Gdx.graphics.getWidth()/getHeight()} возвращают размер окна в
 * точках (то, что видит пользователь и чем меряет {@code Gdx.input}), а сам фреймбуфер, в
 * который реально рисует GL, — вдвое больше в пикселях. Отдать {@code glViewport} точки вместо
 * пикселей на таком экране — значит нарисовать сцену в ЧЕТВЕРТЬ фреймбуфера (половина по каждой
 * оси), а остальное окно останется просто нетронутым чёрным — ровно то, что произошло. Отступы
 * HUD ({@link GfxConfig#HUD_TOP_HEIGHT}/{@code HUD_BOTTOM_HEIGHT}) заданы в точках (та же
 * система координат, что и {@code Gdx.input}/{@code GameCamera}), поэтому для {@code
 * glViewport} их отдельно домножаем на масштаб бэкбуфера.
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
    private final RecipeBookRenderer recipeBookRenderer;

    public Renderer(Textures textures, GameCamera camera) {
        this.camera = camera;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont(); // встроенный 15px Arial — хватает для HUD

        Grid grid = new Grid(GfxConfig.GRID_H);
        this.worldRenderer = new WorldRenderer(shapes, grid);
        this.buildingRenderer = new BuildingRenderer(batch, shapes, textures, font, grid);
        this.itemRenderer = new ItemRenderer(shapes, grid);
        this.overlayRenderer = new OverlayRenderer(batch, shapes, font, textures, grid);
        this.hudRenderer = new HudRenderer(batch, shapes, font, textures);
        this.recipeBookRenderer = new RecipeBookRenderer(batch, shapes, font);
    }

    /**
     * Нарисовать кадр по текущему состоянию мира, выбранному зданию, логу событий, паузе/скорости
     * и тому, открыта ли книга рецептов (клавиша TAB, см. {@code InputHandler#showRecipeBook}).
     */
    public void render(World world, BuildingType selected, Direction facing, ProductionLog log,
            boolean paused, int speed, boolean showRecipeBook, float delta) {
        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Логические точки (то же, что видит Gdx.input и что использует GameCamera/HUD-вёрстка)…
        int screenH = Gdx.graphics.getHeight();
        // …а glViewport требует пиксели РЕАЛЬНОГО бэкбуфера — на Retina это вдвое больше точек.
        int bbW = Gdx.graphics.getBackBufferWidth();
        int bbH = Gdx.graphics.getBackBufferHeight();
        float scale = bbH / (float) screenH;
        int bottomInsetPx = Math.round(GfxConfig.HUD_BOTTOM_HEIGHT * scale);
        int topInsetPx = Math.round(GfxConfig.HUD_TOP_HEIGHT * scale);

        // Мировые слои — глазами камеры, и рисуются ТОЛЬКО в узкую полосу между панелями:
        // glViewport ограничивает, КУДА на экране вообще попадает то, что рисует GL, — полосы
        // HUD сверху/снизу этот проход даже не трогает, там всегда будет фон окна, а не карта.
        Gdx.gl.glViewport(0, bottomInsetPx, bbW, bbH - topInsetPx - bottomInsetPx);
        batch.setProjectionMatrix(camera.combined());
        shapes.setProjectionMatrix(camera.combined());
        TileRange visible = camera.visibleTiles(GfxConfig.GRID_W);

        worldRenderer.render(visible);   // 1. земля + рудные области
        buildingRenderer.render(world);  // 2. здания на карте
        itemRenderer.render(world);      // 3. груз поверх зданий (лента/бур/сортировщик/подземка)
        overlayRenderer.renderWorld(world); // 4. подсветка непарных входов подземки

        // HUD — снова во ВСЁ окно (панели должны дотягиваться до самых краёв), в координатах
        // окна: и batch (текст/иконки), и shapes (подложки панелей).
        Gdx.gl.glViewport(0, 0, bbW, bbH);
        batch.setProjectionMatrix(camera.hudMatrix());
        shapes.setProjectionMatrix(camera.hudMatrix());
        // 5. заголовок + панель + статистика + исследования + лог + пауза/скорость + подсказки
        hudRenderer.render(selected, facing, world.stats(), world.research(), log, paused, speed);
        overlayRenderer.renderHud();     // 6. пусто
        // 7. книга рецептов — поверх всего остального, только если игрок её открыл (TAB).
        if (showRecipeBook) {
            recipeBookRenderer.render();
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

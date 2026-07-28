package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.graphics.GfxConfig;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.world.ProductionLogView;
import com.rustorio.domain.world.World;

/**
 * Дирижёр отрисовки: владеет общими ресурсами и вызывает слои по порядку — земля, здания,
 * предметы, HUD. Рендер только ЧИТАЕТ мир и рисует, НИКОГДА его не меняя.
 *
 * <p>Живые слои — земля (карта с рудой), здания, груз, который они держат в пути, и подсветка
 * непарных входов подземки ({@code OverlayRenderer}).
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
    private final int gridW;

    private final WorldRenderer worldRenderer;
    private final BuildingRenderer buildingRenderer;
    private final ItemRenderer itemRenderer;
    private final OverlayRenderer overlayRenderer;
    private final HudRenderer hudRenderer;
    private final RecipeBookRenderer recipeBookRenderer;
    private final TechTreeRenderer techTreeRenderer;
    private final StatsScreenRenderer statsScreenRenderer;

    /**
     * {@code gridW}/{@code gridH} come from whoever built the {@link World} this renderer will be
     * asked to draw ({@code GameScreen}) — not read from {@link GfxConfig} here. Before P3-06,
     * BUG_FIX_PROGRESS.md, this class and {@code World} each got the map size independently from
     * the same constants; they happened to agree, but nothing enforced it — a world built with a
     * different size would render silently wrong. One source of truth now: whoever constructs the
     * world hands its size to the renderer explicitly.
     */
    public Renderer(Textures textures, GameCamera camera, OreLayout oreLayout, int gridW, int gridH) {
        this.camera = camera;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont(); // built-in 15px Arial — enough for the HUD
        this.gridW = gridW;

        Grid grid = new Grid(gridH);
        this.worldRenderer = new WorldRenderer(shapes, batch, grid, oreLayout, textures);
        this.buildingRenderer = new BuildingRenderer(batch, shapes, textures, font, grid);
        this.itemRenderer = new ItemRenderer(batch, shapes, font, grid);
        this.overlayRenderer = new OverlayRenderer(batch, shapes, textures, font, camera, grid);
        this.hudRenderer = new HudRenderer(batch, shapes, font, textures);
        this.recipeBookRenderer = new RecipeBookRenderer(batch, shapes, font);
        this.techTreeRenderer = new TechTreeRenderer(batch, shapes, font);
        this.statsScreenRenderer = new StatsScreenRenderer(batch, shapes, font);
    }

    /**
     * Нарисовать кадр по текущему состоянию мира, HUD (что выбрано, пауза/скорость, открыта ли
     * книга рецептов — см. {@link HudState}) и логу событий.
     *
     * @param ups сколько раз {@code World.tick()} реально позвался за последнюю полную секунду
     *            (S-04, DEV_TASKS.md) — {@code GameScreen}'s own measurement, не то же самое, что FPS
     */
    public void render(World world, HudState hud, ProductionLogView log, int ups) {
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
        TileRange visible = camera.visibleTiles(gridW);

        worldRenderer.render(visible);              // 1. земля + рудные области
        buildingRenderer.render(world, visible);     // 2. здания на карте
        itemRenderer.render(world, visible);         // 3. груз поверх зданий (лента/бур/сортировщик/подземка)
        overlayRenderer.renderWorld(world, visible, hud); // 4. Alt-слой: стрелки, рамка туннеля, статус, содержимое ящиков (F-04)
        overlayRenderer.renderBuildGhost(world, hud); // 4b. призрак постройки под курсором/протяжкой (F-02)

        // HUD — снова во ВСЁ окно (панели должны дотягиваться до самых краёв), в координатах
        // окна: и batch (текст/иконки), и shapes (подложки панелей).
        Gdx.gl.glViewport(0, 0, bbW, bbH);
        batch.setProjectionMatrix(camera.hudMatrix());
        shapes.setProjectionMatrix(camera.hudMatrix());
        // 5. заголовок + панель + статистика + исследования + инвентарь + лог + пауза/скорость + подсказки
        hudRenderer.render(hud, world, visible, world.stats(), world.research(), world.inventory(), log, ups);
        // 6. книга рецептов — поверх всего остального, только если игрок её открыл (TAB).
        if (hud.showRecipeBook()) {
            recipeBookRenderer.render(world.buildingFactory().recipeBook());
        }
        // 7. дерево техов (P-02, DEV_TASKS.md) — поверх всего, только если игрок открыл его (T).
        if (hud.showTechTree()) {
            techTreeRenderer.render(world.research());
        }
        // 8. экран статистики (P-03, DEV_TASKS.md) — поверх всего, только если игрок открыл его (V).
        if (hud.showStats()) {
            statsScreenRenderer.render(world.stats(), world.currentTick(), hud.statsItem());
        }
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

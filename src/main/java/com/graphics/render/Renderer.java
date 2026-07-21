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
 * <p>Живые слои сейчас — земля (карта с рудой) и здания (буры). Предметы и наложения ещё
 * пустые каркасы; наполнятся, когда в игре появятся ленты и статистика.
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

    public Renderer(Textures textures, GameCamera camera) {
        this.camera = camera;
        this.batch = new SpriteBatch();
        this.shapes = new ShapeRenderer();
        this.font = new BitmapFont(); // встроенный 15px Arial — хватает для HUD

        Grid grid = new Grid(GfxConfig.GRID_H);
        this.worldRenderer = new WorldRenderer(shapes, grid);
        this.buildingRenderer = new BuildingRenderer(batch, shapes, textures, font, grid);
        this.itemRenderer = new ItemRenderer(batch, font, textures, grid);
        this.overlayRenderer = new OverlayRenderer(batch, shapes, font, textures, grid);
        this.hudRenderer = new HudRenderer(batch, font);
    }

    /** Нарисовать кадр по текущему состоянию мира, выбранному зданию и логу событий. */
    public void render(World world, BuildingType selected, Direction facing, ProductionLog log, float delta) {
        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        // Мировые слои — глазами камеры.
        batch.setProjectionMatrix(camera.combined());
        shapes.setProjectionMatrix(camera.combined());
        TileRange visible = camera.visibleTiles(GfxConfig.GRID_W);

        worldRenderer.render(visible);   // 1. земля + рудные области
        buildingRenderer.render(world);  // 2. буры на карте
        itemRenderer.render();           // 3. пусто (предметы — позже)
        overlayRenderer.renderWorld();   // 4. пусто

        // HUD — в координатах окна.
        batch.setProjectionMatrix(camera.hudMatrix());
        hudRenderer.render(selected, facing, world.stats(), log); // 5. заголовок + панель + статистика + лог + подсказки
        overlayRenderer.renderHud();     // 6. пусто
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

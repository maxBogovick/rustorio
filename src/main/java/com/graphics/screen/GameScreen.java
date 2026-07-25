package com.graphics.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.ScreenAdapter;
import com.graphics.GfxConfig;
import com.graphics.input.InputHandler;
import com.graphics.render.GameCamera;
import com.graphics.render.Renderer;
import com.graphics.render.Textures;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.ProductionLog;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.JsonSaveRepository;

/**
 * Экран игры: держит мир и каждый кадр гоняет связку ввод → тик → рендер.
 *
 * <p>Мир пока минимальный (карта с рудой + поставленные буры). libGDX сам вызывает
 * {@link #render(float)} каждый кадр.
 */
public final class GameScreen extends ScreenAdapter {

    private final World world;
    private final GameCamera camera;
    private final InputHandler input;
    private final Renderer renderer;
    private final Textures textures;
    /** Независимый от статистики слушатель того же события — экран его создал, экран его читает. */
    private final ProductionLog productionLog = new ProductionLog();

    /** Фиксированная карта руды ({@link com.rustorio.domain.PatchOreLayout#standard()}). */
    public GameScreen() {
        this(BuildingFactory.standard());
    }

    /** Карта руды сгенерирована из {@code oreSeed} — см. {@link RandomOreLayout}. */
    public GameScreen(long oreSeed) {
        this(new BuildingFactory(
                new RandomOreLayout(oreSeed, GfxConfig.GRID_W, GfxConfig.GRID_H), RecipeBook.standard()));
    }

    private GameScreen(BuildingFactory buildingFactory) {
        this.world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H, buildingFactory);
        world.addProductionListener(productionLog);
        this.camera = new GameCamera(GfxConfig.GRID_W, GfxConfig.GRID_H);
        this.input = new InputHandler(camera, new JsonSaveRepository());
        this.textures = new Textures();
        this.renderer = new Renderer(textures, camera, world.buildingFactory().oreLayout());
        // Колесо мыши в libGDX — событие, опросом его не поймать: подписываемся.
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                camera.zoomAt(Gdx.input.getX(), Gdx.input.getY(), amountY);
                return true;
            }
        });
    }

    @Override
    public void render(float delta) {
        input.handle(world, delta);           // 1. ввод: камера + выбор/постройка + пауза/скорость
        // 2. тик: на паузе — ни разу; иначе — сколько раз попросила скорость (1×/2×/4×).
        if (!input.isPaused()) {
            for (int i = 0; i < input.speed(); i++) {
                world.tick();
            }
        }
        // 3. рендер: карта + HUD
        renderer.render(world, input.selected(), input.facing(), productionLog,
                input.isPaused(), input.speed(), input.showRecipeBook(), delta);
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) { // 0×0 приходит при сворачивании окна
            camera.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        renderer.dispose();
        textures.dispose();
    }
}

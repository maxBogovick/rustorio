package com.graphics.screen;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputProcessor;
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

    /** Один тик симуляции = 1/60 реальной секунды, независимо от частоты кадров (P4-06). */
    private static final float TICK_SECONDS = 1f / 60f;
    /**
     * Потолок «догоняющих» тиков за ОДИН кадр (P4-06, BUG_FIX_PROGRESS.md): без него долгая
     * заминка (просевший кадр, разворачивание окна) оставляет в {@link #accumulator} огромный
     * долг, и следующий кадр пытается его весь разом отработать — «спираль смерти» (каждый
     * досчитанный тик стоит времени, кадр только удлиняется, долг не уменьшается).
     */
    private static final int MAX_CATCHUP_TICKS = 5;

    private final World world;
    private final GameCamera camera;
    private final InputHandler input;
    private final Renderer renderer;
    private final Textures textures;
    /** Независимый от статистики слушатель того же события — экран его создал, экран его читает. */
    private final ProductionLog productionLog = new ProductionLog();
    /** Сколько реального времени накопилось сверх последнего отработанного тика. */
    private float accumulator;
    /**
     * UPS-счётчик (S-04, DEV_TASKS.md): сколько раз {@link World#tick()} реально позвался за
     * последнюю полную секунду — не то же самое, что FPS ({@link com.badlogic.gdx.Graphics#getFramesPerSecond()},
     * уже готовый и сглаженный самим libGDX, читается прямо в {@code HudRenderer}). При множителе
     * скорости (2×/4×) UPS растёт вместе с ним, а FPS — нет; расхождение между ними — само по себе
     * полезный сигнал (например, «симуляция не поспевает за запрошенным множителем»).
     */
    private float upsTimer;
    private int ticksThisSecond;
    private int lastUps;
    /**
     * Обработчик колеса мыши, сохранённый ради {@link #dispose}/{@link #hide} (P4-07,
     * BUG_FIX_PROGRESS.md): раньше конструктор ставил его в {@code Gdx.input} и никогда не снимал
     * — при появлении второго экрана этот, привязанный к уже мёртвой {@link #camera}, продолжил
     * бы получать события.
     */
    private final InputProcessor inputProcessor;

    /** Фиксированная карта руды ({@link com.rustorio.domain.PatchOreLayout#standard()}). */
    public GameScreen() {
        this(BuildingFactory.standard(), false);
    }

    /** Карта руды сгенерирована из {@code oreSeed} — см. {@link RandomOreLayout}. */
    public GameScreen(long oreSeed) {
        this(new BuildingFactory(
                new RandomOreLayout(oreSeed, GfxConfig.GRID_W, GfxConfig.GRID_H), RecipeBook.standard()), false);
    }

    /**
     * Dev-mode showcase ({@code --dev}, {@code com.graphics.Main}) — фиксированная карта, как у
     * {@link #GameScreen()}: {@link DevScene}'s coordinates assume the standard map's real ore
     * patches, so this can't be combined with {@link #GameScreen(long)}'s random seed.
     */
    public GameScreen(boolean devMode) {
        this(BuildingFactory.standard(), devMode);
    }

    private GameScreen(BuildingFactory buildingFactory, boolean devMode) {
        this.world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H, buildingFactory);
        world.addProductionListener(productionLog);
        if (devMode) {
            DevScene.build(world);
        }
        this.camera = new GameCamera(GfxConfig.GRID_W, GfxConfig.GRID_H);
        this.input = new InputHandler(camera, new JsonSaveRepository());
        this.textures = Textures.vanilla();
        this.renderer = new Renderer(textures, camera, world.buildingFactory().oreLayout(), world.width(), world.height());
        // Колесо мыши в libGDX — событие, опросом его не поймать: подписываемся.
        this.inputProcessor = new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                camera.zoomAt(Gdx.input.getX(), Gdx.input.getY(), amountY);
                return true;
            }
        };
        Gdx.input.setInputProcessor(inputProcessor);
    }

    @Override
    public void render(float delta) {
        input.handle(world, delta);           // 1. ввод: камера + выбор/постройка + пауза/скорость
        // 2. тик: фиксированным шагом (P4-06) — на паузе аккумулятор не растёт и тиков не будет;
        // иначе на каждый накопленный TICK_SECONDS мир тикает столько раз, сколько просит скорость
        // (1×/2×/4×), но не больше MAX_CATCHUP_TICKS раз за этот кадр.
        if (!input.isPaused()) {
            accumulator += delta;
            int caughtUp = 0;
            while (accumulator >= TICK_SECONDS && caughtUp < MAX_CATCHUP_TICKS) {
                accumulator -= TICK_SECONDS;
                for (int i = 0; i < input.speed(); i++) {
                    world.tick();
                    ticksThisSecond++;
                }
                caughtUp++;
            }
        }
        upsTimer += delta;
        if (upsTimer >= 1f) {
            lastUps = ticksThisSecond;
            ticksThisSecond = 0;
            upsTimer -= 1f;
        }
        // 3. рендер: карта + HUD
        renderer.render(world, input.hudState(), productionLog, lastUps);
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) { // 0×0 приходит при сворачивании окна
            camera.resize(width, height);
        }
    }

    @Override
    public void hide() {
        clearInputProcessorIfOurs();
    }

    @Override
    public void dispose() {
        clearInputProcessorIfOurs();
        renderer.dispose();
        textures.dispose();
    }

    /** Снять {@link #inputProcessor}, только если он всё ещё текущий — не затереть чужой (P4-07). */
    private void clearInputProcessorIfOurs() {
        if (Gdx.input.getInputProcessor() == inputProcessor) {
            Gdx.input.setInputProcessor(null);
        }
    }
}

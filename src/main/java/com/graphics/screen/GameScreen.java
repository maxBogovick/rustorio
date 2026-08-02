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
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.ProductionLog;
import com.rustorio.domain.world.World;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModDirectories;
import com.rustorio.mod.ModLoader;
import com.rustorio.persistence.JsonSaveRepository;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

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

    /** Where every mod (including the built-in {@code rustorio} one) lives — see {@link ModDirectories#discover}. */
    private static final Path MODS_ROOT = Path.of("resources", "mods");

    /** Фиксированная карта руды ({@link PatchOreLayout#standard()}). */
    public GameScreen() {
        this(loadedGame -> PatchOreLayout.standard(), false);
    }

    /** Карта руды сгенерирована из {@code oreSeed} — см. {@link RandomOreLayout}. */
    public GameScreen(long oreSeed) {
        this(loadedGame -> new RandomOreLayout(oreSeed, GfxConfig.GRID_W, GfxConfig.GRID_H), false);
    }

    /**
     * Dev-mode showcase ({@code --dev}, {@code com.graphics.Main}) — фиксированная карта, как у
     * {@link #GameScreen()}: {@link DevScene}'s coordinates assume the standard map's real ore
     * patches, so this can't be combined with {@link #GameScreen(long)}'s random seed.
     */
    public GameScreen(boolean devMode) {
        this(loadedGame -> PatchOreLayout.standard(), devMode);
    }

    /**
     * A mod-authored map (the content editor's canvas, {@code --map=} in {@code com.graphics.Main})
     * — {@code mapId} must resolve in {@code loadedGame.maps()} once mods are loaded, hence the
     * {@link Function}-based constructor below rather than a plain {@link OreLayout}: unlike {@link
     * PatchOreLayout#standard()}/{@link RandomOreLayout}, this one doesn't exist until AFTER {@link
     * ModLoader#loadAll} has run.
     */
    public GameScreen(ContentId mapId) {
        this(loadedGame -> AuthoredOreLayout.from(loadedGame.maps().get(mapId)), false);
    }

    /**
     * Every real content set (items, recipes, buildings — vanilla AND modded) is loaded here,
     * through the same {@link ModLoader#loadAll} the mod system's own acceptance tests already
     * exercise end to end, instead of the {@code VanillaItems.frozen()}/{@code
     * VanillaBuildings.frozen()}/{@code RecipeBook.standard()} shortcut {@link
     * BuildingFactory#standard()} takes. {@code resources/mods/rustorio}'s JSON mirrors vanilla
     * 1:1 (see {@code VanillaAsModParityTest}), so on a stock checkout this looks identical — the
     * difference only shows once a mod (or the local content editor, {@code com.rustorio.editor})
     * adds or changes a {@code content/*.json} file under {@link #MODS_ROOT}.
     *
     * <p>{@code oreLayoutFactory}, not a plain {@link OreLayout}: an {@link AuthoredOreLayout} can
     * only be built from a {@link LoadedGame}'s own {@code maps()} registry, which doesn't exist
     * until {@link ModLoader#loadAll} below has already run — every OTHER caller's factory just
     * ignores the argument, same as before this constructor existed.
     */
    private GameScreen(Function<LoadedGame, OreLayout> oreLayoutFactory, boolean devMode) {
        List<Path> modDirectories = ModDirectories.discover(MODS_ROOT);
        LoadedGame loadedGame = ModLoader.loadAll(modDirectories);
        OreLayout oreLayout = oreLayoutFactory.apply(loadedGame);
        BuildingFactory buildingFactory = new BuildingFactory(
                oreLayout, loadedGame.recipes(), loadedGame.items(), loadedGame.buildings());
        this.world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H, buildingFactory);
        world.addProductionListener(productionLog);
        if (devMode) {
            DevScene.build(world);
        }
        this.camera = new GameCamera(GfxConfig.GRID_W, GfxConfig.GRID_H);
        this.input = new InputHandler(camera, new JsonSaveRepository());
        this.textures = Textures.loadFrom(modDirectories);
        this.renderer = new Renderer(textures, camera, world.buildingFactory().oreLayout(), world.width(), world.height());
        // Колесо мыши в libGDX — событие, опросом его не поймать: подписываемся.
        this.inputProcessor = new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                // Меню построек открыто (B) — колесо листает его сетку иконок, а не зумит камеру
                // под ним; input.handleScroll возвращает false, когда меню не открыто, и тогда
                // колесо зумит как раньше.
                if (!input.handleScroll(amountY)) {
                    camera.zoomAt(Gdx.input.getX(), Gdx.input.getY(), amountY);
                }
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

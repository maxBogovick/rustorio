package com.graphics.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
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
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.ProductionLog;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import com.rustorio.mod.LoadedGame;
import com.rustorio.persistence.JsonSaveRepository;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

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
    private boolean disposed;
    /**
     * Обработчик колеса мыши, сохранённый ради {@link #dispose}/{@link #hide} (P4-07,
     * BUG_FIX_PROGRESS.md): раньше конструктор ставил его в {@code Gdx.input} и никогда не снимал
     * — при появлении второго экрана этот, привязанный к уже мёртвой {@link #camera}, продолжил
     * бы получать события.
     */
    private final InputProcessor inputProcessor;

    /** Esc (once no HUD panel is open — {@code InputHandler#hasOpenPanel}) — Save/Load/Main Menu/Exit; see its own javadoc. */
    private final PauseMenu pauseMenu;

    /**
     * Фиксированная карта руды ({@link PatchOreLayout#standard()}). {@code loadedGame}/{@code
     * modDirectories} come from whoever is switching TO this screen ({@code
     * com.graphics.RustorioGame} on a CLI launch, {@code MainMenuScreen} from a menu choice) —
     * mods are loaded exactly once, up there, not per {@code GameScreen} (see this class's own
     * former javadoc history: before the main menu existed, this constructor ran {@code
     * ModLoader#loadAll} itself, which would have meant loading mods twice for every
     * menu → game transition).
     */
    public GameScreen(Game game, LoadedGame loadedGame, List<Path> modDirectories) {
        this(game, loadedGame, modDirectories, PatchOreLayout.standard(), false);
    }

    /** Карта руды сгенерирована из {@code oreSeed} — см. {@link RandomOreLayout}. */
    public GameScreen(Game game, LoadedGame loadedGame, List<Path> modDirectories, long oreSeed) {
        this(game, loadedGame, modDirectories, new RandomOreLayout(oreSeed, GfxConfig.GRID_W, GfxConfig.GRID_H), false);
    }

    /**
     * Dev-mode showcase ({@code --dev}, {@code com.graphics.Main}) — фиксированная карта, как у
     * {@link #GameScreen(Game, LoadedGame, List)}: {@link DevScene}'s coordinates assume the standard
     * map's real ore patches, so this can't be combined with a random seed.
     */
    public GameScreen(Game game, LoadedGame loadedGame, List<Path> modDirectories, boolean devMode) {
        this(game, loadedGame, modDirectories, PatchOreLayout.standard(), devMode);
    }

    /**
     * A mod-authored map (the content editor's canvas, {@code --map=} in {@code com.graphics.Main},
     * or a map picked in {@code MainMenuScreen}'s "New Game" list) — {@code mapId} must resolve in
     * {@code loadedGame.maps()}.
     */
    public GameScreen(Game game, LoadedGame loadedGame, List<Path> modDirectories, ContentId mapId) {
        this(game, loadedGame, modDirectories, AuthoredOreLayout.from(loadedGame.maps().get(mapId)), false);
    }

    /**
     * Every real content set (items, recipes, buildings — vanilla AND modded) already sits in
     * {@code loadedGame}, built by the same {@link com.rustorio.mod.ModLoader#loadAll} the mod
     * system's own acceptance tests already exercise end to end, instead of the {@code
     * VanillaItems.frozen()}/{@code VanillaBuildings.frozen()}/{@code RecipeBook.standard()}
     * shortcut {@link BuildingFactory#standard()} takes. {@code resources/mods/rustorio}'s JSON
     * mirrors vanilla 1:1 (see {@code VanillaAsModParityTest}), so on a stock checkout this looks
     * identical — the difference only shows once a mod (or the local content editor, {@code
     * com.rustorio.editor}) adds or changes a {@code content/*.json} file.
     */
    private GameScreen(Game game, LoadedGame loadedGame, List<Path> modDirectories, OreLayout oreLayout, boolean devMode) {
        // GameBootstrap, а не сборка мира здесь: он же цепляет шину событий мода к миру
        // (EventWiring), про которую этот конструктор раньше не знал — и ни одно событие в
        // запущенной игре до мода не доезжало.
        // Every mod's buildings AND its own services come from loadedGame — including code mods,
        // which register both from inside their own jar (ModdedServiceAcceptanceTest exercises
        // that whole path end to end against a real one). This constructor
        // named one mod by hand until that mod became a real jar; now it names none, and a modded
        // building is placeable from the ordinary build menu (B) with no further UI work —
        // InputHandler reads world.buildingFactory().buildings() live, not a fixed BuildingType list.
        this.world = GameBootstrap.createWorld(loadedGame, oreLayout, GfxConfig.GRID_W, GfxConfig.GRID_H);
        world.addProductionListener(productionLog);
        if (devMode) {
            DevScene.build(world);
        }
        this.camera = new GameCamera(GfxConfig.GRID_W, GfxConfig.GRID_H);
        // GameBootstrap.saves, а не конструктор напрямую: быстрое сохранение (F5/F9) должно
        // читаться и писаться тем же реестром предметов, на котором построен мир, И применять
        // переименования прототипов, объявленные модами, — забыть второе из четырёх мест вызова
        // ровно так и получилось.
        this.input = new InputHandler(camera, GameBootstrap.saves(loadedGame, JsonSaveRepository.DEFAULT_PATH),
                world.buildingFactory().buildings());
        this.pauseMenu = new PauseMenu(game, loadedGame, modDirectories, world, this::dispose);
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
        // Процессор ввода регистрируется в show(), НЕ здесь: forSave строит экран-кандидата ещё до
        // показа, и захват глобального ввода в конструкторе крал его у текущего экрана — а на
        // неудачной загрузке dispose() кандидата обнулял процессор, оставляя живую игру без колеса
        // мыши (зум/скролл меню построек). Экран, который не показан, ввод не трогает.
    }

    /**
     * Loads a save into this screen's already-built world — used by {@code MainMenuScreen}'s
     * "Load Game"/"Continue", BEFORE this screen is ever shown to the player: the caller picks
     * which {@code GameScreen} constructor to use from the save's own recorded map first, so
     * {@link SaveRepository#load}'s map-mismatch check can never fire here. On {@link
     * SaveResult.Failure} the caller is expected to {@link #dispose()} this instance instead of
     * showing it — same "world untouched on failure" guarantee {@link SaveRepository#load} already
     * documents, just one level up.
     */
    public SaveResult loadFrom(SaveRepository repository) {
        return input.load(repository, world);
    }

    /**
     * Picks the right {@code GameScreen} constructor for a save's own recorded {@link
     * OreLayoutId} (vanilla/random/authored) — {@code null} if {@code layout} itself is {@code
     * null} (a corrupt/foreign save header) or names an authored map {@code loadedGame} no longer
     * has registered. Shared by {@code MainMenuScreen}'s "Load Game"/"Continue" and {@code
     * PauseMenu}'s "Load Game": both need to pick a constructor from a save's own map BEFORE
     * calling {@link #loadFrom}, so {@link SaveRepository#load}'s map-mismatch check can never
     * fire for either.
     */
    public static @Nullable GameScreen forSave(Game game, LoadedGame loadedGame, List<Path> modDirectories, @Nullable OreLayoutId layout) {
        if (layout == null) {
            return null;
        }
        String kind = layout.kind();
        if (kind.equals("patch")) {
            return new GameScreen(game, loadedGame, modDirectories);
        }
        if (kind.equals("random")) {
            return new GameScreen(game, loadedGame, modDirectories, layout.seed());
        }
        if (kind.startsWith("authored:")) {
            ContentId mapId = ContentId.of(kind.substring("authored:".length()));
            return loadedGame.maps().peek(mapId).isEmpty() ? null : new GameScreen(game, loadedGame, modDirectories, mapId);
        }
        return null;
    }

    @Override
    public void render(float delta) {
        if (pauseMenu.isOpen()) {
            pauseMenu.handleInput();
        } else {
            input.handle(world, delta);        // 1. ввод: камера + выбор/постройка + пауза/скорость
            // Esc, только если input.handle только что НЕ закрыл им же какую-то HUD-панель (см.
            // InputHandler#hasOpenPanel) — один и тот же Esc либо закрывает панель, либо (если
            // закрывать было нечего) открывает паузу-меню, не оба сразу за один кадр.
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && !input.hasOpenPanel()) {
                pauseMenu.open();
            }
        }
        // pauseMenu.handleInput выше мог загрузить сейв или выйти в меню — освободив этот экран
        // (disposeOwningScreen) и переключив screen — а мы всё ещё внутри кадра: и тик, и рендер
        // ниже пошли бы по уже освобождённым ресурсам ("No buffer allocated!").
        if (disposed) {
            return;
        }
        // 2. тик: фиксированным шагом (P4-06) — на паузе (обычной или через паузу-меню) аккумулятор
        // не растёт и тиков не будет; иначе на каждый накопленный TICK_SECONDS мир тикает столько
        // раз, сколько просит скорость (1×/2×/4×), но не больше MAX_CATCHUP_TICKS раз за этот кадр.
        if (!input.isPaused() && !pauseMenu.isOpen()) {
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
        // 3. рендер: карта + HUD (+ паузa-меню поверх всего, если открыто)
        renderer.render(world, input.hudState(), productionLog, lastUps, pauseMenu.isOpen() ? pauseMenu.view() : null);
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) { // 0×0 приходит при сворачивании окна
            camera.resize(width, height);
        }
    }

    @Override
    public void show() {
        // Пара к hide(): экран берёт ввод, когда его показывают, и отдаёт, когда прячут — а не
        // держит с момента конструктора (см. его конец, про forSave-кандидата).
        Gdx.input.setInputProcessor(inputProcessor);
    }

    @Override
    public void hide() {
        clearInputProcessorIfOurs();
    }

    @Override
    public void dispose() {
        if (disposed) { // called by disposeOwningScreen and possibly again by a load-failure caller — free once
            return;
        }
        disposed = true;
        clearInputProcessorIfOurs();
        world.closeServices(); // unrelated to the GL context below — order relative to it doesn't matter
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

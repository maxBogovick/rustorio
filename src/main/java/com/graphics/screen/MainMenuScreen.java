package com.graphics.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.ScreenAdapter;
import com.graphics.render.MenuLayout;
import com.graphics.render.MenuRenderer;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.game.GameBootstrap;
import com.rustorio.mod.LoadedGame;
import com.rustorio.persistence.SaveResult;
import com.rustorio.persistence.SaveSlotInfo;
import com.rustorio.persistence.SaveSlots;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The game's entry screen ({@code com.graphics.RustorioGame}, a plain desktop launch with no
 * {@code --map=}/{@code --seed=}/{@code --dev} override) — pick a map for a new game, load one of
 * {@link SaveSlots}'s named saves, or continue the most recent one.
 *
 * <p><b>Three views, one class.</b> {@link Mode} switches what {@link #items}/{@link #actions}
 * hold, not what screen is showing — a real screen swap (via {@code game.setScreen}) only happens
 * once, when the player actually starts or loads a game, so there's no {@link
 * com.badlogic.gdx.Screen#hide()}/{@code show()} churn between "New Game" and "Load Game", which
 * are really the same panel with different rows.
 *
 * <p><b>{@link #items}/{@link #actions}, not a positional {@code switch (mode, index)}.</b> Each
 * row's label and what picking it does are built together, in the same loop, so there's no second
 * place that has to agree on what index N means (the same trap {@link
 * com.graphics.render.BuildMenuLayout}'s scroll/hit-test split avoids by sharing one formula).
 */
public final class MainMenuScreen extends ScreenAdapter {

    private enum Mode { ROOT, NEW_GAME, LOAD_GAME }

    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final Game game;
    private final LoadedGame loadedGame;
    private final List<Path> modDirectories;
    private final SaveSlots saveSlots = new SaveSlots();
    private final MenuRenderer renderer = new MenuRenderer();

    private Mode mode = Mode.ROOT;
    private List<String> items = List.of();
    private List<Runnable> actions = List.of();
    private int selectedIndex = 0;
    private @Nullable String status;
    private boolean disposed;

    public MainMenuScreen(Game game, LoadedGame loadedGame, List<Path> modDirectories) {
        this.game = game;
        this.loadedGame = loadedGame;
        this.modDirectories = modDirectories;
        enterRoot();
        // После enterRoot: он сбрасывает status в null, а пропущенные моды игрок должен увидеть
        // на первом же экране. Раньше сломанный мод просто ронял игру со стек-трейсом.
        this.status = MenuStatus.skippedMods(loadedGame.skippedMods());
    }

    @Override
    public void render(float delta) {
        handleInput();
        // handleInput may have started a game (New Game/Load/Continue), which disposes our renderer
        // and switches screens — but we are still inside this frame's render() call, so drawing now
        // would flush a SpriteBatch whose GPU buffers are already freed ("No buffer allocated!").
        if (disposed) {
            return;
        }
        renderer.render(title(), items, selectedIndex, hoverIndex(), status);
    }

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) { // 0×0 приходит при сворачивании окна — GameScreen#resize делает то же
            renderer.resize(width, height);
        }
    }

    @Override
    public void dispose() {
        if (disposed) { // startGame calls this explicitly; guard keeps a stray second call from double-freeing
            return;
        }
        disposed = true;
        renderer.dispose();
    }

    private String title() {
        return switch (mode) {
            case ROOT -> "Rustorio";
            case NEW_GAME -> "New Game — choose a map";
            case LOAD_GAME -> "Load Game";
        };
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            selectedIndex = items.isEmpty() ? 0 : (selectedIndex + 1) % items.size();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            selectedIndex = items.isEmpty() ? 0 : Math.floorMod(selectedIndex - 1, items.size());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && !actions.isEmpty()) {
            activate(selectedIndex);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && mode != Mode.ROOT) {
            enterRoot();
        }
        int hovered = hoverIndex();
        if (hovered >= 0) {
            selectedIndex = hovered;
            if (Gdx.input.justTouched()) {
                activate(hovered);
            }
        }
    }

    private int hoverIndex() {
        return MenuLayout.hitTestRow(Gdx.input.getX(), Gdx.input.getY(),
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), items.size(), status != null);
    }

    private void activate(int index) {
        actions.get(index).run();
    }

    private void enterRoot() {
        mode = Mode.ROOT;
        List<String> labels = new ArrayList<>();
        List<Runnable> ops = new ArrayList<>();
        List<SaveSlotInfo> saves = saveSlots.list();
        if (!saves.isEmpty()) {
            SaveSlotInfo latest = saves.get(0); // list() sorts newest-first — see SaveSlots
            labels.add("Continue (" + latest.name() + ")");
            ops.add(() -> loadSlot(latest));
        }
        labels.add("New Game");
        ops.add(this::enterNewGame);
        labels.add("Load Game");
        ops.add(this::enterLoadGame);
        labels.add("Exit");
        ops.add(Gdx.app::exit);
        setItems(labels, ops);
    }

    private void enterNewGame() {
        mode = Mode.NEW_GAME;
        List<String> labels = new ArrayList<>();
        List<Runnable> ops = new ArrayList<>();
        labels.add("Vanilla map");
        ops.add(() -> startGame(new GameScreen(game, loadedGame, modDirectories)));
        for (AuthoredMap map : loadedGame.maps().iterate()) {
            labels.add(map.id().toString());
            ops.add(() -> startGame(new GameScreen(game, loadedGame, modDirectories, map.id())));
        }
        labels.add("Back");
        ops.add(this::enterRoot);
        setItems(labels, ops);
    }

    private void enterLoadGame() {
        mode = Mode.LOAD_GAME;
        List<String> labels = new ArrayList<>();
        List<Runnable> ops = new ArrayList<>();
        List<SaveSlotInfo> saves = saveSlots.list();
        if (saves.isEmpty()) {
            labels.add("(no saves yet)");
            ops.add(() -> { });
        } else {
            for (SaveSlotInfo info : saves) {
                labels.add(info.name() + "   —   " + info.mapLabel() + "   —   " + SAVED_AT.format(info.savedAt()));
                ops.add(() -> loadSlot(info));
            }
        }
        labels.add("Back");
        ops.add(this::enterRoot);
        setItems(labels, ops);
    }

    private void setItems(List<String> labels, List<Runnable> ops) {
        this.items = List.copyOf(labels);
        this.actions = List.copyOf(ops);
        this.selectedIndex = 0;
        this.status = null;
    }

    /**
     * Builds the right {@link GameScreen} for {@code slot}'s own recorded map FIRST (via {@link
     * GameScreen#forSave}, so {@link GameScreen#loadFrom}'s map-mismatch check can never fire),
     * then loads into it. On failure — unknown/foreign map header, or {@link SaveResult.Failure}
     * from the load itself — the half-built {@link GameScreen} is disposed and this screen stays
     * put with {@link #status} set, exactly like a failed F9 leaves {@code GameScreen} untouched.
     */
    private void loadSlot(SaveSlotInfo slot) {
        GameScreen candidate = GameScreen.forSave(game, loadedGame, modDirectories, slot.mapLayout());
        if (candidate == null) {
            status = "Cannot load \"" + slot.name() + "\": unknown or removed map.";
            return;
        }
        SaveResult result = candidate.loadFrom(GameBootstrap.saves(loadedGame, saveSlots.pathFor(slot.name())));
        if (result instanceof SaveResult.Failure failure) {
            candidate.dispose();
            status = "Load failed: " + failure.reason();
            return;
        }
        startGame(candidate);
    }

    /**
     * {@code Game#setScreen} never disposes the screen it replaces (libGDX leaves that to the
     * caller) — this screen's own {@link MenuRenderer} (a {@code SpriteBatch}/{@code
     * ShapeRenderer}/{@code BitmapFont}, all GPU resources) would otherwise leak on every single
     * "New Game"/"Load Game"/"Continue", found while wiring up {@code PauseMenu}'s own screen
     * switches, which have the exact same shape.
     */
    private void startGame(GameScreen screen) {
        dispose();
        game.setScreen(screen);
    }
}

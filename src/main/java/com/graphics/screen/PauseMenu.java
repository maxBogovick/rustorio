package com.graphics.screen;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.render.MenuLayout;
import com.graphics.render.PauseMenuView;
import com.rustorio.domain.world.World;
import com.rustorio.mod.LoadedGame;
import com.rustorio.persistence.JsonSaveRepository;
import com.rustorio.persistence.SaveResult;
import com.rustorio.persistence.SaveSlotInfo;
import com.rustorio.persistence.SaveSlots;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * Esc, opened from {@link GameScreen} once no other HUD panel is (see {@code
 * InputHandler#hasOpenPanel}) — Resume / Save / Load Game / Main Menu / Exit, the same
 * items-plus-actions and single-column list technique {@link MainMenuScreen} already uses, just
 * embedded as one more thing {@link GameScreen} owns and draws through {@link
 * com.graphics.render.PauseMenuRenderer} instead of a whole separate {@code Screen}.
 *
 * <p>Deliberately NOT reachable while the game is running normally: {@link GameScreen} skips
 * {@code InputHandler#handle} entirely while this is open (see its own {@code render}), so camera/
 * building/F5/F9 all stop responding — a real pause, not an overlay floating on top of a still-live
 * game.
 */
final class PauseMenu {

    private enum Mode { ROOT, SAVE, LOAD, NAME_ENTRY }

    private static final DateTimeFormatter SAVED_AT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());
    /** {@link SaveSlots#pathFor}'s own limit — the name field refuses to grow the buffer past it. */
    private static final int MAX_NAME_LENGTH = 64;

    private final Game game;
    private final LoadedGame loadedGame;
    private final List<Path> modDirectories;
    private final World world;
    private final SaveSlots saveSlots = new SaveSlots();
    /** {@code GameScreen::dispose}, bound to the instance this menu belongs to — called right before switching away to a different screen (Load/Main Menu), never for Resume/Save/Exit. */
    private final Runnable disposeOwningScreen;

    private boolean open;
    private Mode mode = Mode.ROOT;
    private List<String> items = List.of();
    private List<Runnable> actions = List.of();
    private int selectedIndex;
    private @Nullable String status;
    private final StringBuilder nameBuffer = new StringBuilder();
    /** Non-{@code null} while {@link Mode#NAME_ENTRY} is renaming this existing slot rather than naming a brand new save — see {@link #confirmName}. */
    private @Nullable String renameTarget;
    /** Parallel to {@link #items}/{@link #actions} while {@link Mode#LOAD} — lets R/Delete act on whichever row is selected without re-deriving a name from its label text. Empty outside {@link Mode#LOAD}. */
    private List<SaveSlotInfo> loadListSlots = List.of();

    PauseMenu(Game game, LoadedGame loadedGame, List<Path> modDirectories, World world, Runnable disposeOwningScreen) {
        this.game = game;
        this.loadedGame = loadedGame;
        this.modDirectories = modDirectories;
        this.world = world;
        this.disposeOwningScreen = disposeOwningScreen;
    }

    boolean isOpen() {
        return open;
    }

    void open() {
        open = true;
        enterRoot();
    }

    void handleInput() {
        if (mode == Mode.NAME_ENTRY) {
            handleNameEntryInput();
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) {
            selectedIndex = items.isEmpty() ? 0 : (selectedIndex + 1) % items.size();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) {
            selectedIndex = items.isEmpty() ? 0 : Math.floorMod(selectedIndex - 1, items.size());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && !actions.isEmpty()) {
            activate(selectedIndex);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (mode == Mode.ROOT) {
                open = false;
            } else {
                enterRoot();
            }
            return;
        }
        // R/Delete act on whichever LOAD row is selected — only real slots, never the trailing
        // "Back" row or the "(no saves yet)" placeholder: both are outside loadListSlots' range,
        // so the guard below excludes them for free instead of naming them specially.
        if (mode == Mode.LOAD && selectedIndex < loadListSlots.size()) {
            SaveSlotInfo target = loadListSlots.get(selectedIndex);
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                beginRename(target.name());
                return;
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.FORWARD_DEL)) {
                confirmDelete(target.name());
                return;
            }
        }
        int hovered = hoverIndex();
        if (hovered >= 0) {
            selectedIndex = hovered;
            if (Gdx.input.justTouched()) {
                activate(hovered);
            }
        }
    }

    PauseMenuView view() {
        return new PauseMenuView(title(), items, selectedIndex, mode == Mode.NAME_ENTRY ? -1 : hoverIndex(),
                status, mode == Mode.NAME_ENTRY ? nameBuffer.toString() : null);
    }

    private String title() {
        return switch (mode) {
            case ROOT -> "Paused";
            case SAVE -> "Save Game";
            case LOAD -> "Load Game  (Enter: load, R: rename, Del: delete)";
            case NAME_ENTRY -> renameTarget != null ? "Rename \"" + renameTarget + "\"" : "New Save";
        };
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
        setItems(List.of("Resume", "Save Game", "Load Game", "Main Menu", "Exit to Desktop"),
                List.of(this::resume, this::enterSave, this::enterLoad, this::goToMainMenu, Gdx.app::exit));
    }

    private void resume() {
        open = false;
    }

    private void enterSave() {
        mode = Mode.SAVE;
        List<String> labels = new ArrayList<>();
        List<Runnable> ops = new ArrayList<>();
        labels.add("New Save...");
        ops.add(this::beginNameEntry);
        for (SaveSlotInfo info : saveSlots.list()) {
            labels.add("Overwrite: " + info.name());
            ops.add(() -> confirmOverwrite(info.name()));
        }
        labels.add("Back");
        ops.add(this::enterRoot);
        setItems(labels, ops);
    }

    private void beginNameEntry() {
        mode = Mode.NAME_ENTRY;
        nameBuffer.setLength(0);
        renameTarget = null;
        status = null;
    }

    private void beginRename(String target) {
        mode = Mode.NAME_ENTRY;
        nameBuffer.setLength(0);
        nameBuffer.append(target);
        renameTarget = target;
        status = null;
    }

    /** Letters/digits/space/hyphen, matching {@link SaveSlots}'s own name pattern minus underscore — no plain keycode maps to it on a US layout, and space/hyphen already cover "separate these words". */
    private void handleNameEntryInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && nameBuffer.length() > 0) {
            nameBuffer.setLength(nameBuffer.length() - 1);
        }
        for (int key = Input.Keys.A; key <= Input.Keys.Z; key++) {
            if (Gdx.input.isKeyJustPressed(key) && nameBuffer.length() < MAX_NAME_LENGTH) {
                nameBuffer.append(Input.Keys.toString(key).toLowerCase(Locale.ROOT));
            }
        }
        for (int key = Input.Keys.NUM_0; key <= Input.Keys.NUM_9; key++) {
            if (Gdx.input.isKeyJustPressed(key) && nameBuffer.length() < MAX_NAME_LENGTH) {
                nameBuffer.append(Input.Keys.toString(key));
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE) && nameBuffer.length() < MAX_NAME_LENGTH) {
            nameBuffer.append(' ');
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) && nameBuffer.length() < MAX_NAME_LENGTH) {
            nameBuffer.append('-');
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            confirmName();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (renameTarget != null) {
                enterLoad();
            } else {
                enterSave();
            }
        }
    }

    private void confirmName() {
        String name = nameBuffer.toString().trim();
        if (name.isEmpty()) {
            status = "Name can't be empty.";
            return;
        }
        if (renameTarget != null) {
            doRename(renameTarget, name);
        } else if (saveSlots.exists(name)) {
            confirmOverwrite(name);
        } else {
            doSave(name);
        }
    }

    private void confirmOverwrite(String name) {
        mode = Mode.SAVE;
        setItems(List.of("Overwrite \"" + name + "\"? Yes", "No, go back"),
                List.of(() -> doSave(name), this::enterSave));
    }

    private void doSave(String name) {
        SaveResult result = new JsonSaveRepository(saveSlots.prepareForSave(name), loadedGame.items()).save(world);
        enterSave();
        status = result instanceof SaveResult.Failure failure
                ? "Save failed: " + failure.reason()
                : "Saved as \"" + name + "\".";
    }

    private void enterLoad() {
        mode = Mode.LOAD;
        List<String> labels = new ArrayList<>();
        List<Runnable> ops = new ArrayList<>();
        List<SaveSlotInfo> saves = saveSlots.list();
        loadListSlots = saves; // R/Delete key handling in handleInput indexes into this
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

    /**
     * Builds the right {@code GameScreen} for {@code slot}'s own recorded map FIRST — same
     * ordering {@code MainMenuScreen#loadSlot} uses via {@link GameScreen#forSave}, and for the
     * same reason: so {@link GameScreen#loadFrom}'s map-mismatch check can never fire.
     */
    private void loadSlot(SaveSlotInfo slot) {
        GameScreen candidate = GameScreen.forSave(game, loadedGame, modDirectories, slot.mapLayout());
        if (candidate == null) {
            status = "Cannot load \"" + slot.name() + "\": unknown or removed map.";
            return;
        }
        SaveResult result = candidate.loadFrom(new JsonSaveRepository(saveSlots.pathFor(slot.name()), loadedGame.items()));
        if (result instanceof SaveResult.Failure failure) {
            candidate.dispose();
            status = "Load failed: " + failure.reason();
            return;
        }
        disposeOwningScreen.run();
        game.setScreen(candidate);
    }

    /**
     * Refuses to rename ONTO an existing other slot outright (shows an error, back to {@link
     * Mode#LOAD}) rather than folding into the same overwrite-confirm flow {@link #doSave} uses —
     * renaming isn't the same intent as "replace this save's contents", so silently offering to
     * destroy a different save here would surprise more than it'd help.
     */
    private void doRename(String from, String to) {
        if (from.equals(to)) {
            enterLoad();
            return;
        }
        if (saveSlots.exists(to)) {
            status = "A save named \"" + to + "\" already exists.";
            return;
        }
        try {
            saveSlots.rename(from, to);
            enterLoad();
            status = "Renamed \"" + from + "\" to \"" + to + "\".";
        } catch (RuntimeException e) {
            enterLoad();
            status = "Rename failed: " + e.getMessage();
        }
    }

    private void confirmDelete(String name) {
        setItems(List.of("Delete \"" + name + "\"? Yes", "No, go back"),
                List.of(() -> doDelete(name), this::enterLoad));
    }

    private void doDelete(String name) {
        boolean deleted = saveSlots.delete(name);
        enterLoad();
        status = deleted ? "Deleted \"" + name + "\"." : "\"" + name + "\" was already gone.";
    }

    private void goToMainMenu() {
        disposeOwningScreen.run();
        game.setScreen(new MainMenuScreen(game, loadedGame, modDirectories));
    }

    private void setItems(List<String> labels, List<Runnable> ops) {
        this.items = List.copyOf(labels);
        this.actions = List.copyOf(ops);
        this.selectedIndex = 0;
        this.status = null;
    }
}

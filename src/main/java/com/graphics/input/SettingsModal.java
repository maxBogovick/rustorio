package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.render.SettingsModalLayout;
import com.graphics.render.SettingsModalView;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.ConfigureBuildingAction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.FieldSpec;
import com.rustorio.domain.building.FieldType;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The one settings editor every {@link EditableBuilding} shares — {@code WebMiner} and {@code
 * Interpreter} today, a third building tomorrow for free, no new code here or in {@link
 * InputHandler}. Replaces two near-identical hand-written editors that used to live directly in
 * {@link InputHandler} — a live bug report from the owner: "любое редактирование параметров
 * сделано очень неудобно... тут так и просится общий механизм."
 *
 * <p>Same "point at it, click, edit, Enter/Esc" shape those two already had, generalized: {@link
 * #openIfEditable} seeds every field's buffer from {@link EditableBuilding#currentFieldValues} at
 * open time; {@link #handleInput} intercepts ALL other input while {@link #isOpen()} — {@link
 * InputHandler#handle} routes to it instead of anything else, the same discipline {@code
 * PauseMenu}'s own {@code Mode.NAME_ENTRY} already uses, for the same reason (WASD/R/U/C/F/G on
 * the very keystrokes being typed would be live bugs, not edge cases).
 *
 * <p>No {@code Gdx.input.getTextInput} — checked against the actual LWJGL3 desktop backend
 * bytecode (see the editors this replaces, in version control history): it unconditionally calls
 * {@code listener.canceled()} and would have silently never worked at all.
 */
final class SettingsModal {

    private static final int MAX_TEXT_LENGTH = 300;
    private static final int MAX_NUMBER_LENGTH = 6;

    private boolean open;
    private int x;
    private int y;
    private String title = "";
    private List<String> readOnlyLines = List.of();
    private List<FieldSpec> fields = List.of();
    private List<StringBuilder> buffers = List.of();
    private int focusedIndex;

    boolean isOpen() {
        return open;
    }

    /**
     * Opens on {@code building} at {@code (x, y)} if it implements {@link EditableBuilding} — a
     * silent no-op otherwise, same "point at it, nothing happens if it doesn't apply" discipline
     * every other per-building interaction in {@link InputHandler} already follows.
     *
     * <p>{@code label} is passed in rather than read off the building: the name to show is the
     * PROTOTYPE's, and this class has no registry to resolve one. Reading {@code type().label()}
     * here titled every modded building with the vanilla kind it borrows.
     */
    void openIfEditable(Building building, String label, int x, int y) {
        if (!(building instanceof EditableBuilding editable)) {
            return;
        }
        this.open = true;
        this.x = x;
        this.y = y;
        this.title = label + "  (" + x + ", " + y + ")";
        this.readOnlyLines = editable.readOnlyInfo();
        this.fields = editable.editableFields();
        List<StringBuilder> newBuffers = new ArrayList<>();
        for (String value : editable.currentFieldValues()) {
            newBuffers.add(new StringBuilder(value));
        }
        this.buffers = newBuffers;
        this.focusedIndex = 0;
    }

    /**
     * Runs INSTEAD of everything else in {@link InputHandler#handle} while {@link #isOpen()} —
     * same letters/digits/BACKSPACE/ENTER/ESCAPE shape {@code PauseMenu#handleNameEntryInput}
     * already uses, generalized over whichever field is currently focused: {@link FieldType#TEXT}
     * gets the URL/field-list punctuation ({@code .}/{@code /}/{@code :} via SEMICOLON+Shift — no
     * unshifted colon on a US layout/{@code -}/{@code _} via MINUS+Shift/{@code ,}), {@link
     * FieldType#NUMBER} gets digits only.
     */
    void handleInput(World world, ActionHistory history) {
        handleFieldClick();
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            focusedIndex = (focusedIndex + 1) % fields.size();
        }
        StringBuilder active = buffers.get(focusedIndex);
        FieldType type = fields.get(focusedIndex).type();
        int maxLength = type == FieldType.NUMBER ? MAX_NUMBER_LENGTH : MAX_TEXT_LENGTH;
        boolean shift = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && active.length() > 0) {
            active.setLength(active.length() - 1);
        }
        for (int key = Input.Keys.NUM_0; key <= Input.Keys.NUM_9; key++) {
            if (Gdx.input.isKeyJustPressed(key) && active.length() < maxLength) {
                active.append(Input.Keys.toString(key));
            }
        }
        if (type == FieldType.TEXT) {
            for (int key = Input.Keys.A; key <= Input.Keys.Z; key++) {
                if (Gdx.input.isKeyJustPressed(key) && active.length() < maxLength) {
                    active.append(Input.Keys.toString(key).toLowerCase(Locale.ROOT));
                }
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.PERIOD) && active.length() < maxLength) {
                active.append('.');
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.SLASH) && active.length() < maxLength) {
                active.append('/');
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.SEMICOLON) && active.length() < maxLength) {
                active.append(shift ? ':' : ';');
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.MINUS) && active.length() < maxLength) {
                active.append(shift ? '_' : '-');
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.COMMA) && active.length() < maxLength) {
                active.append(',');
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
            commit(world, history);
            return;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            open = false;
        }
    }

    /** Mouse click on a field row focuses it — checked every frame this is open, same shape {@code InspectionPanelLayout}'s own hit-testing already uses. */
    private void handleFieldClick() {
        if (!Gdx.input.justTouched()) {
            return;
        }
        int hit = SettingsModalLayout.hitTestField(Gdx.input.getX(), Gdx.input.getY(),
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), readOnlyLines.size(), fields.size());
        if (hit >= 0) {
            focusedIndex = hit;
        }
    }

    private void commit(World world, ActionHistory history) {
        List<String> values = new ArrayList<>();
        for (StringBuilder buffer : buffers) {
            values.add(buffer.toString());
        }
        history.perform(world, new ConfigureBuildingAction(x, y, values));
        open = false;
    }

    @Nullable
    SettingsModalView view() {
        if (!open) {
            return null;
        }
        List<String> labels = new ArrayList<>();
        List<String> values = new ArrayList<>();
        for (int i = 0; i < fields.size(); i++) {
            labels.add(fields.get(i).label());
            values.add(buffers.get(i).toString());
        }
        return new SettingsModalView(title, readOnlyLines, labels, values, focusedIndex,
                "Tab: next field   Enter: save   Esc: cancel");
    }
}

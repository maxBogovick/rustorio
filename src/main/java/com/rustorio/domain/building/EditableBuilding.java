package com.rustorio.domain.building;

import java.util.List;

/**
 * A building the player can configure through the generic settings modal ({@code
 * com.graphics.input}'s own settings-modal class, opened by a plain click — see that package for
 * why not {@code Gdx.input.getTextInput}). Replaces two near-identical hand-written editors
 * ({@code WebMiner}'s URL/interval/backoff, {@code Interpreter}'s field list) that used to each
 * carry their own copy of the same keyboard-polling/commit/undo code in {@code InputHandler} — a
 * live bug report from the owner: "любое редактирование параметров сделано очень неудобно...
 * тут так и просится общий механизм." A THIRD such building now costs one class implementing this
 * interface, zero new lines in {@code InputHandler}.
 *
 * <p>{@link #editableFields}/{@link #currentFieldValues} are called ONCE, when the modal opens —
 * they seed the on-screen buffers the player then types into; nothing here is polled per frame.
 * {@link #applyEdits} is called ONCE, on commit, with exactly {@link #editableFields}{@code
 * .size()} strings in the SAME order — implementations may assume that arity, the same way {@code
 * Codec#decode} may assume its own {@code encode}'s shape (see that interface's own javadoc).
 *
 * <p>Deliberately returns no {@link com.rustorio.domain.action.PlayerAction} — that would make
 * {@code com.rustorio.domain.building} depend on {@code com.rustorio.domain.action}, which already
 * depends back on {@code building} (to name which one it's editing) and would be a package cycle.
 * {@code com.rustorio.domain.action.ConfigureBuildingAction} is the single generic action every
 * implementer shares, built from THIS interface instead of one action class per building.
 */
public interface EditableBuilding {

    /** Which fields the modal shows, in display order — the same order {@link #currentFieldValues}/{@link #applyEdits} use. */
    List<FieldSpec> editableFields();

    /** The current value of each field in {@link #editableFields}, same order, same size — what the modal's buffers start as. */
    List<String> currentFieldValues();

    /** Apply exactly {@link #editableFields}{@code .size()} new values, same order — malformed input (e.g. an empty number field) is this method's own job to default sensibly, not the caller's. */
    void applyEdits(List<String> newValues);

    /** Extra read-only context shown above the editable fields — empty for a building with nothing to add (most of them). Computed once, at modal-open time, from this building's own state only (no {@code World} access here — see {@code Interpreter}'s own javadoc for why its live extraction preview isn't part of this). */
    default List<String> readOnlyInfo() {
        return List.of();
    }
}

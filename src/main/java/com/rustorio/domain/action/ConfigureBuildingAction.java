package com.rustorio.domain.action;

import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.world.World;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The generic commit for {@code com.graphics.input}'s settings modal — ANY {@link
 * EditableBuilding} at {@code (x, y)}, not one class per building. Replaces {@code
 * ConfigureWebMinerAction}/{@code ConfigureInterpreterAction}, which existed only because there was
 * no common interface yet to write this against; see {@link EditableBuilding}'s own javadoc for the
 * live bug report that prompted the merge.
 *
 * <p>Refuses (returns {@code false}, remembers nothing) for an empty cell or a building that isn't
 * {@link EditableBuilding} at all — same shape {@link RotateAction} follows for a building with
 * nothing to rotate. Mutates the placed instance in place via {@link EditableBuilding#applyEdits}
 * rather than swapping in a replacement, same reasoning as {@code WebMiner#setUrl}'s own javadoc.
 */
public final class ConfigureBuildingAction implements PlayerAction {

    private final int x;
    private final int y;
    private final List<String> newValues;

    private @Nullable List<String> previousValues;

    public ConfigureBuildingAction(int x, int y, List<String> newValues) {
        this.x = x;
        this.y = y;
        this.newValues = newValues;
    }

    @Override
    public boolean apply(World world) {
        Building current = world.peek(x, y).orElse(null);
        if (!(current instanceof EditableBuilding editable)) {
            return false;
        }
        previousValues = editable.currentFieldValues();
        editable.applyEdits(newValues);
        return true;
    }

    /** Restores every field {@link #apply} overwrote — a no-op if the cell no longer holds an {@link EditableBuilding} at all (demolished since, say). */
    @Override
    public void undo(World world) {
        if (previousValues == null) {
            return;
        }
        Building current = world.peek(x, y).orElse(null);
        if (current instanceof EditableBuilding editable) {
            editable.applyEdits(previousValues);
        }
    }
}

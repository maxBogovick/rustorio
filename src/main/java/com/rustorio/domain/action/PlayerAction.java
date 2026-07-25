package com.rustorio.domain.action;

import com.rustorio.domain.world.World;

/**
 * Command pattern: a player intent, reified as an object that can apply itself to the world and
 * undo exactly that. Where the command came from — a mouse click, a future menu, a redo — stops
 * mattering once it exists; applying and undoing it always works the same way.
 */
public interface PlayerAction {

    /**
     * Apply this action to the world.
     *
     * @return {@code true} if something actually changed — worth remembering for undo; {@code
     *         false} if it couldn't be applied (the cell was occupied, say), with nothing to
     *         remember
     */
    boolean apply(World world);

    /** Undo exactly what {@link #apply} did. Only called if {@code apply} returned {@code true}. */
    void undo(World world);
}

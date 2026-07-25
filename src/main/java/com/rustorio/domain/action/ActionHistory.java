package com.rustorio.domain.action;

import com.rustorio.domain.world.World;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * History of applied {@link PlayerAction}s: what's been done can be undone; what's been undone
 * can be redone, until something new is done.
 */
public final class ActionHistory {

    private final Deque<PlayerAction> done = new ArrayDeque<>();
    private final Deque<PlayerAction> undone = new ArrayDeque<>();

    /** Apply an action to the world; if it actually changed anything, remember it for undo. */
    public void perform(World world, PlayerAction action) {
        if (action.apply(world)) {
            done.push(action);
            undone.clear(); // a new action cuts off the old "future" — its redo is gone
        }
    }

    public void undo(World world) {
        if (done.isEmpty()) {
            return;
        }
        PlayerAction action = done.pop();
        action.undo(world);
        undone.push(action);
    }

    public void redo(World world) {
        if (undone.isEmpty()) {
            return;
        }
        PlayerAction action = undone.pop();
        action.apply(world);
        done.push(action);
    }
}

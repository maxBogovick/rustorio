package com.rustorio.domain.action;

import com.rustorio.domain.world.World;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * History of applied {@link PlayerAction}s: what's been done can be undone; what's been undone
 * can be redone, until something new is done.
 */
public final class ActionHistory {

    /**
     * A memory limit, not a UX decision — nothing here claims 200 is the "right" number of undo
     * steps for a player. Without a cap, every {@code done} entry (a {@code RemoveAction} holding
     * a demolished {@code Belt}, which holds the rest of its {@code BeltSegment}, and so on) lives
     * for the whole session; see P1-06 in BUG_FIX_PROGRESS.md.
     */
    private static final int MAX_DEPTH = 200;

    private final Deque<PlayerAction> done = new ArrayDeque<>();
    private final Deque<PlayerAction> undone = new ArrayDeque<>();

    /** Apply an action to the world; if it actually changed anything, remember it for undo. */
    public void perform(World world, PlayerAction action) {
        if (action.apply(world)) {
            done.push(action);
            while (done.size() > MAX_DEPTH) {
                done.removeLast(); // oldest entry — no longer undoable, free to be garbage-collected
            }
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

    /**
     * Discard both stacks. History belongs to a specific world's state — after a save is loaded,
     * the world underneath every recorded action is gone, so undoing or redoing them would act on
     * state they were never applied to. Call this after a successful load, never before it starts
     * (a failed load leaves the world untouched, so the old history is still valid — see P1-03 in
     * BUG_FIX_PROGRESS.md).
     */
    public void clear() {
        done.clear();
        undone.clear();
    }
}

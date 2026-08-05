package com.rustorio.api.mod;

import com.rustorio.api.content.ContentId;

/**
 * Published before a building is placed, while the placement can still be refused — the cancellable
 * counterpart to {@link BuildingPlacedEvent}, which only reports what already happened.
 *
 * <p>Not a {@code record}, unlike every other event here, and deliberately so: a handler answers by
 * calling {@link #cancel()}, which needs state the event carries back to the engine. A record could
 * not do that without handing out something mutable anyway.
 *
 * <p>Published only for a player's own placement, never while a save or an undone action is being
 * restored — a mod that refuses a building must not be able to delete one out of a factory that
 * already exists simply by being installed.
 *
 * <p>Fires only after the engine has already found the placement legal (in bounds, cell free,
 * terrain suitable), so a handler never has to re-implement those rules to decide. Cancelling
 * leaves the world exactly as it was and the player's click does nothing.
 */
public final class BuildingPlaceEvent {

    private final ContentId prototypeId;
    private final int x;
    private final int y;
    private boolean cancelled;

    public BuildingPlaceEvent(ContentId prototypeId, int x, int y) {
        this.prototypeId = prototypeId;
        this.x = x;
        this.y = y;
    }

    public ContentId prototypeId() {
        return prototypeId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    /** Refuse this placement. Once cancelled the event stays cancelled: no handler can un-cancel another's refusal. */
    public void cancel() {
        cancelled = true;
    }

    public boolean isCancelled() {
        return cancelled;
    }
}

package com.graphics.input;

import com.rustorio.api.content.ContentId;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's own small grid of pinned buildings — up to nine, keys 1-9, icons only.
 *
 * <p>It starts EMPTY and grows, which is the opposite of what the old bottom strip did. That strip
 * listed every registered prototype: fine at twelve, unreadable at twenty, and off both edges of
 * the screen at twenty-nine — a panel that gets worse with every mod installed is a panel that
 * punishes the thing this engine exists to make easy. The catalogue lives in the build panel now;
 * this holds only what one player actually reached for.
 *
 * <p><b>Growth in rows of three.</b> An empty or barely-used bar shows one row rather than a grid
 * of holes: three cells up to three pins, six up to six, nine after that. {@link #visibleCells} is
 * the whole rule, and it is here rather than in the renderer because "how big is my bar" is a fact
 * about its contents.
 */
final class QuickBar {

    /** Three per row — the shape the owner asked for, and the reason growth happens in threes. */
    static final int COLUMNS = 3;
    /** Three rows of three. Nine is also exactly what keys 1-9 can address, which is not a coincidence. */
    static final int MAX_SLOTS = COLUMNS * 3;

    private final List<ContentId> pinned = new ArrayList<>();

    /** What is pinned, in slot order — an unmodifiable view, so the renderer cannot quietly reorder the player's bar. */
    List<ContentId> slots() {
        return List.copyOf(pinned);
    }

    boolean isEmpty() {
        return pinned.isEmpty();
    }

    /**
     * How many cells to draw: always a whole number of rows of {@link #COLUMNS}, at least one row,
     * never more than {@link #MAX_SLOTS}. An empty bar still shows its first row — otherwise there
     * is nothing on screen to tell a new player the bar exists at all.
     */
    int visibleCells() {
        int rowsNeeded = Math.max(1, (pinned.size() + COLUMNS - 1) / COLUMNS);
        return Math.min(MAX_SLOTS, rowsNeeded * COLUMNS);
    }

    /** How many rows those cells occupy — what the layout needs to know where the bar's top edge is. */
    int visibleRows() {
        return visibleCells() / COLUMNS;
    }

    /**
     * Pins {@code prototypeId} into the first free cell and answers where it landed, or the cell it
     * already occupied — pinning the same building twice must not consume a second slot, which is
     * easy to do by accident when the catalogue is one click away.
     *
     * <p>Returns {@code -1} when the bar is full and this is something new: refusing is deliberate,
     * because the alternative is silently evicting a building the player pinned on purpose. The
     * caller reports it; see {@code InputHandler}.
     */
    int pin(ContentId prototypeId) {
        int existing = pinned.indexOf(prototypeId);
        if (existing >= 0) {
            return existing;
        }
        if (pinned.size() >= MAX_SLOTS) {
            return -1;
        }
        pinned.add(prototypeId);
        return pinned.size() - 1;
    }

    /** Removes whatever sits in {@code index}, closing the gap — cells stay contiguous, so the grid never shows a hole between two pins. */
    void unpin(int index) {
        if (index >= 0 && index < pinned.size()) {
            pinned.remove(index);
        }
    }

    /** What is in {@code index}, or empty for a cell that is drawn but not filled — every cell of a partly-used row. */
    java.util.Optional<ContentId> at(int index) {
        return index >= 0 && index < pinned.size()
                ? java.util.Optional.of(pinned.get(index))
                : java.util.Optional.empty();
    }

    /**
     * Replaces the whole bar — how a saved layout is restored. Ignores anything past {@link
     * #MAX_SLOTS} and any duplicate, so a hand-edited settings file cannot produce a bar the player
     * could never have built themselves.
     */
    void restore(List<ContentId> saved) {
        pinned.clear();
        for (ContentId id : saved) {
            if (pinned.size() >= MAX_SLOTS) {
                break;
            }
            if (!pinned.contains(id)) {
                pinned.add(id);
            }
        }
    }
}

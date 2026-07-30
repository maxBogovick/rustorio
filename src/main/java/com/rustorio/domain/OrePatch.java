package com.rustorio.domain;

/**
 * A circular ore deposit — the shape both {@link PatchOreLayout} and {@link RandomOreLayout} are
 * built from. Used to be declared identically, twice, as a private nested record in each; pulled
 * out here so the two implementations share one definition instead of two that could silently
 * drift apart (P4-03, BUG_FIX_PROGRESS.md).
 */
record OrePatch(int cx, int cy, int radius, ItemType ore) {

    boolean contains(int x, int y) {
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}

package com.rustorio.domain;

/**
 * A circular terrain obstacle (water or rock) — the {@link Terrain} counterpart to {@link
 * OrePatch}, kept as its own tiny record rather than sharing one with {@code OrePatch}: the two
 * carry different payloads ({@link ItemType} vs {@link Terrain}) for genuinely different concerns, and
 * {@code OrePatch} is already shipped, tested code this task has no reason to reshape just to
 * share three lines of circle arithmetic (X-02, DEV_TASKS.md).
 */
record TerrainPatch(int cx, int cy, int radius, Terrain terrain) {

    boolean contains(int x, int y) {
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}

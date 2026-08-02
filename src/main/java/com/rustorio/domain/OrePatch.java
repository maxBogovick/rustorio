package com.rustorio.domain;

/**
 * A circular ore deposit — the shape {@link PatchOreLayout}, {@link RandomOreLayout} and {@link
 * AuthoredOreLayout} are all built from. Used to be declared identically, twice, as a private
 * nested record in each of the first two; pulled out here so they share one definition instead of
 * two that could silently drift apart (P4-03, BUG_FIX_PROGRESS.md).
 *
 * <p>Public (not package-private, unlike most of this package's small helper types): {@code
 * com.rustorio.mod.MapJsonLoader} needs to construct one per patch a mod's {@code
 * content/maps/*.json} declares, to hand {@link AuthoredMap} — the only door into the mod's own
 * package that doesn't require duplicating this exact shape as a second DTO.
 */
public record OrePatch(int cx, int cy, int radius, ItemType ore) {

    boolean contains(int x, int y) {
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}

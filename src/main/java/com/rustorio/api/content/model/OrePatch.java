package com.rustorio.api.content.model;

/**
 * A circular ore deposit — the shape {@link com.rustorio.domain.PatchOreLayout}, {@link
 * com.rustorio.domain.RandomOreLayout} and {@link com.rustorio.domain.AuthoredOreLayout} are all
 * built from. Used to be declared identically, twice, as a private nested record in each of the
 * first two; pulled out here so they share one definition instead of two that could silently drift
 * apart (P4-03, BUG_FIX_PROGRESS.md).
 *
 * <p>Public (not package-private, unlike most of this package's small helper types): {@code
 * com.rustorio.mod.MapJsonLoader} needs to construct one per patch a mod's {@code
 * content/maps/*.json} declares, to hand {@link AuthoredMap} — the only door into the mod's own
 * package that doesn't require duplicating this exact shape as a second DTO.
 *
 * <p>{@link #contains} is public because layout rasterizers live in {@code com.rustorio.domain},
 * not this package — package-private visibility was enough when both sat in {@code domain}.
 */
public record OrePatch(int cx, int cy, int radius, ItemType ore) {

    public boolean contains(int x, int y) {
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= radius * radius;
    }
}

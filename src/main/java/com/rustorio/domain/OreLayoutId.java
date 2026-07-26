package com.rustorio.domain;

/**
 * What map an {@link OreLayout} is — enough to tell "this save's ore map" from "the world's
 * current ore map" without comparing every cell. Persisted alongside a save (see
 * {@code com.rustorio.persistence.WorldSnapshot}) so {@code JsonSaveRepository.load} can refuse a
 * save whose map doesn't match the one currently loaded, instead of silently placing miners on
 * cells that used to have ore under a different seed.
 *
 * @param kind   which {@link OreLayout} implementation — {@code "patch"} for
 *               {@link PatchOreLayout}, {@code "random"} for {@link RandomOreLayout}
 * @param seed   the seed the layout was rolled from; meaningless (and ignored) for
 *               {@code "patch"}, since it's the same fixed map every time
 * @param width  the map width the layout was generated for
 * @param height the map height the layout was generated for
 */
public record OreLayoutId(String kind, long seed, int width, int height) {
}

package com.rustorio.api.dsl;

/**
 * Fluent door onto {@link com.rustorio.api.mod.RegistrationContext} for the common "register my
 * content" cases — items, recipes, maps, fluids, buildings — without threading {@code ContentId.of}
 * and telescope {@code BuildingPrototype} constructors by hand.
 *
 * <p>Obtained from {@link com.rustorio.api.mod.RegistrationContext#content()}. Paths without a
 * {@code ':'} are resolved under the current mod's namespace.
 */
public interface ContentDsl {

    ItemDraft item(String path);

    RecipeDraft recipe(String path);

    MapDraft map(String path);

    FluidDraft fluid(String path);

    BuildingDsl building(String path);
}

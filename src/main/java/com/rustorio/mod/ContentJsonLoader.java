package com.rustorio.mod;

import com.rustorio.api.mod.RegistrationContext;
import java.nio.file.Path;

/**
 * Loads one mod's {@code content/} directory — {@code items/}, then {@code recipes/}, then {@code
 * buildings/} (that order: a recipe can reference an item this same call just registered, a
 * building's cost can reference either) — into the shared {@link RegistrationContext}. A missing
 * {@code content/} directory, or any of its three subdirectories, isn't an error: a mod can be
 * code-only, and a data mod need not use every content kind.
 */
final class ContentJsonLoader {

    private ContentJsonLoader() {
    }

    static void loadInto(Path modDirectory, ModId modId, RegistrationContext context) {
        Path content = modDirectory.resolve("content");
        ItemJsonLoader.loadInto(content.resolve("items"), modId, context.items());
        RecipeJsonLoader.loadInto(content.resolve("recipes"), modId, context);
        BuildingJsonLoader.loadInto(content.resolve("buildings"), modId, context);
    }
}

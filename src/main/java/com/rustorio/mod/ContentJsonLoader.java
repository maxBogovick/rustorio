package com.rustorio.mod;

import com.rustorio.api.mod.RegistrationContext;
import java.nio.file.Path;

/**
 * Loads one mod's {@code content/} directory — {@code kinds/}, then {@code items/}, then {@code
 * recipes/}, then {@code buildings/} (that order: kinds first since a recipe or building may want
 * to join one declared in this same mod, though nothing actually REQUIRES that ordering to
 * resolve — see {@code RecipeKindJsonLoader}'s own javadoc; a recipe can reference an item this
 * same call just registered, a building's cost can reference either) — into the shared {@link
 * RegistrationContext}. A missing {@code content/} directory, or any of its four subdirectories,
 * isn't an error: a mod can be code-only, and a data mod need not use every content kind.
 */
final class ContentJsonLoader {

    private ContentJsonLoader() {
    }

    static void loadInto(Path modDirectory, ModId modId, RegistrationContext context) {
        Path content = modDirectory.resolve("content");
        RecipeKindJsonLoader.loadInto(content.resolve("kinds"), modId, context.kinds());
        ItemJsonLoader.loadInto(content.resolve("items"), modId, context.items());
        MapJsonLoader.loadInto(content.resolve("maps"), modId, context);
        RecipeJsonLoader.loadInto(content.resolve("recipes"), modId, context);
        BuildingJsonLoader.loadInto(content.resolve("buildings"), modId, context);
    }
}

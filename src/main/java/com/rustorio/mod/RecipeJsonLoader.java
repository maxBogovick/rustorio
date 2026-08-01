package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import java.nio.file.Path;
import java.util.List;

/**
 * Reads {@code content/recipes/*.json}: {@code ingredients} (array), {@code output}, {@code time},
 * {@code kind} — either one of the 12 {@link BuildingType} names (the recipe joins that kind's own
 * SHARED vanilla pool) or a bare/namespaced reference (same resolution as {@code ingredients}/
 * {@code output} below) matching some building's own {@code BuildingPrototype#recipeKind()} — the
 * private pool a {@code Furnace}-archetype building defaults to using its own id for (see {@code
 * BuildingJsonLoader}'s {@code "kind"} field) — so a custom archetype's recipes never collide or go
 * ambiguous against the vanilla pools, or another mod's, purely from JSON — no Java required.
 */
final class RecipeJsonLoader {

    private RecipeJsonLoader() {
    }

    static void loadInto(Path recipesDir, ModId modId, RegistrationContext context) {
        for (Path file : JsonNodes.listJsonFilesSorted(recipesDir)) {
            JsonNode root = JsonNodes.readTree(file);
            List<String> ingredientRefs = JsonNodes.requireTextArray(root, "ingredients", file);
            String outputRef = JsonNodes.requireText(root, "output", file);
            int time = JsonNodes.requireInt(root, "time", file);
            ContentId kind = resolveKind(JsonNodes.requireText(root, "kind", file), modId);

            List<ItemType> ingredients = ingredientRefs.stream()
                    .map(ref -> resolveItem(ref, modId, context.items(), file))
                    .toList();
            ItemType output = resolveItem(outputRef, modId, context.items(), file);
            context.addRecipe(new Recipe(ingredients, output, time, kind));
        }
    }

    private static ItemType resolveItem(String ref, ModId modId, Registry<ItemType> items, Path file) {
        ContentId id = ref.indexOf(':') >= 0 ? ContentId.of(ref) : new ContentId(modId.value(), ref);
        return items.peek(id).orElseThrow(() -> new ModLoadException(file + ": item '" + id
                + "' referenced by this recipe is not registered (register it — in this mod or an "
                + "earlier-loaded dependency — before this recipe file is read)"));
    }

    /**
     * A {@link BuildingType} name resolves to that kind's own shared vanilla pool (unchanged
     * behavior for every existing recipe file); anything else is a bare/namespaced reference to a
     * building's own custom {@code recipeKind} — same bare-path-in-this-mod's-own-namespace
     * convention {@link #resolveItem} already uses, deliberately NOT validated against a live
     * registry here (unlike an item reference): a "kind" is just an identifier, not itself a
     * registered thing, so there's nothing to look up — an unmatched custom kind simply means no
     * building currently claims it, which is a legitimate (if useless) state, not an error.
     */
    private static ContentId resolveKind(String text, ModId modId) {
        for (BuildingType type : BuildingType.values()) {
            if (type.name().equals(text)) {
                return type.contentId();
            }
        }
        return text.indexOf(':') >= 0 ? ContentId.of(text) : new ContentId(modId.value(), text);
    }
}

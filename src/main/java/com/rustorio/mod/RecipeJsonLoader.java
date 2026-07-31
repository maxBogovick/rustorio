package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Reads {@code content/recipes/*.json}: {@code ingredients} (array), {@code output}, {@code time},
 * {@code kind} (an existing {@link BuildingType} constant — a recipe still names one of the fixed
 * machine kinds rather than an arbitrary mod-defined one, since {@code Recipe}/{@code Furnace}
 * dispatch on that enum, not on a registered prototype id). Item references ({@code
 * ingredients}/{@code output}) are either a bare path (resolved in the CURRENT mod's own namespace)
 * or a full {@code "namespace:path"} (any other mod's item, including vanilla's).
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
            BuildingType kind = parseKind(JsonNodes.requireText(root, "kind", file), file);

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

    private static BuildingType parseKind(String text, Path file) {
        try {
            return BuildingType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(file + ": unknown 'kind' \"" + text + "\" (expected one of "
                    + Arrays.toString(BuildingType.values()) + ")");
        }
    }
}

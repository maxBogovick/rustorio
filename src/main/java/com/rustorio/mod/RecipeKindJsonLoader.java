package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.RecipeKind;
import java.nio.file.Path;

/**
 * Reads {@code content/kinds/*.json} — one recipe pool per file: {@code path} (this kind's own
 * {@link ContentId} path, under the owning mod's namespace) and {@code label} (a display name).
 * Loaded FIRST, before items/recipes/buildings ({@link ContentJsonLoader}), so a recipe or
 * building in the SAME mod can reference a kind declared earlier in its own {@code content/kinds/}
 * — though nothing here actually requires that ordering to resolve correctly, since a kind
 * reference is just a bare/namespaced {@link ContentId} string resolved the same way an item
 * reference is (see {@code RecipeJsonLoader.resolveKind}/{@code BuildingJsonLoader.resolveKind|})
 * — the real cross-file "does this kind actually exist" check happens once, after everything is
 * loaded, in {@code ModLoader#validateContent}.
 */
final class RecipeKindJsonLoader {

    private RecipeKindJsonLoader() {
    }

    static void loadInto(Path kindsDir, ModId modId, Registry<RecipeKind> kinds) {
        for (Path file : JsonNodes.listJsonFilesSorted(kindsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            String path = JsonNodes.requireText(root, "path", file);
            ContentId id = new ContentId(modId.value(), path);
            String label = JsonNodes.requireLocalizedText(root, "label", file, id.toString(), ContentLocale.current());

            kinds.register(id, new RecipeKind(id, label));
        }
    }
}

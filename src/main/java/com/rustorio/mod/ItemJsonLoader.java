package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Reads {@code content/items/*.json} — one item per file: {@code path} (this item's own {@link
 * ContentId} path, under the owning mod's namespace), {@code label} (a plain string, or an object
 * of {@code {"en": "...", "ru": "..."}} — see {@link JsonNodes#requireLocalizedText}), {@code
 * researchGrade} (defaults to {@code false}), {@code colorRgb} as {@code "#RRGGBB"}, {@code shape}
 * (an {@link ItemShape} constant name).
 */
final class ItemJsonLoader {

    private ItemJsonLoader() {
    }

    static void loadInto(Path itemsDir, ModId modId, Registry<ItemType> items) {
        for (Path file : JsonNodes.listJsonFilesSorted(itemsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            String path = JsonNodes.requireText(root, "path", file);
            ContentId id = new ContentId(modId.value(), path);
            String label = JsonNodes.requireLocalizedText(root, "label", file, id.toString(), ContentLocale.current());
            boolean researchGrade = JsonNodes.optionalBoolean(root, "researchGrade", false);
            int colorRgb = JsonNodes.requireColorRgb(root, "colorRgb", file);
            ItemShape shape = parseShape(JsonNodes.requireText(root, "shape", file), file);

            items.register(id, new ItemType(id, label, researchGrade, colorRgb, shape));
        }
    }

    private static ItemShape parseShape(String text, Path file) {
        try {
            return ItemShape.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(file + ": unknown 'shape' \"" + text + "\" (expected one of "
                    + Arrays.toString(ItemShape.values()) + ")");
        }
    }
}

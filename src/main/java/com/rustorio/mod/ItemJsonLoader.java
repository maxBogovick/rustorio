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
 * ContentId} path, under the owning mod's namespace), {@code label}, {@code researchGrade}
 * (defaults to {@code false}), {@code colorRgb} as {@code "#RRGGBB"}, {@code shape} (an {@link
 * ItemShape} constant name).
 */
final class ItemJsonLoader {

    private ItemJsonLoader() {
    }

    static void loadInto(Path itemsDir, ModId modId, Registry<ItemType> items) {
        for (Path file : JsonNodes.listJsonFilesSorted(itemsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            String path = JsonNodes.requireText(root, "path", file);
            String label = JsonNodes.requireText(root, "label", file);
            boolean researchGrade = JsonNodes.optionalBoolean(root, "researchGrade", false);
            int colorRgb = parseColor(JsonNodes.requireText(root, "colorRgb", file), file);
            ItemShape shape = parseShape(JsonNodes.requireText(root, "shape", file), file);

            ContentId id = new ContentId(modId.value(), path);
            items.register(id, new ItemType(id, label, researchGrade, colorRgb, shape));
        }
    }

    private static int parseColor(String text, Path file) {
        if (text.length() != 7 || text.charAt(0) != '#') {
            throw new ModLoadException(file + ": field 'colorRgb' must be \"#RRGGBB\": \"" + text + "\"");
        }
        try {
            return Integer.parseInt(text.substring(1), 16);
        } catch (NumberFormatException e) {
            throw new ModLoadException(file + ": field 'colorRgb' is not valid hex: \"" + text + "\"");
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

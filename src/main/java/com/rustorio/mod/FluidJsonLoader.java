package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.FluidType;
import java.nio.file.Path;

/**
 * Reads {@code content/fluids/*.json} — one fluid per file: {@code path} (this fluid's own {@link
 * ContentId} path, under the owning mod's namespace), {@code label} (a plain string, or an object
 * of {@code {"en": "...", "ru": "..."}} — see {@link JsonNodes#requireLocalizedText}) and {@code
 * colorRgb} as {@code "#RRGGBB"}.
 *
 * <p>Deliberately the same three fields, read the same way, as {@link ItemJsonLoader}'s first three:
 * a fluid is a different KIND of content, not a differently-authored one, and a modder who has
 * written an item file already knows how to write this one.
 */
final class FluidJsonLoader {

    private FluidJsonLoader() {
    }

    static void loadInto(Path fluidsDir, ModId modId, Registry<FluidType> fluids) {
        for (Path file : JsonNodes.listJsonFilesSorted(fluidsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            String path = JsonNodes.requireText(root, "path", file);
            ContentId id = new ContentId(modId.value(), path);
            String label = JsonNodes.requireLocalizedText(root, "label", file, id.toString(), ContentLocale.current());
            int colorRgb = JsonNodes.requireColorRgb(root, "colorRgb", file);

            fluids.register(id, new FluidType(id, label, colorRgb));
        }
    }
}

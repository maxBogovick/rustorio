package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.TechType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code content/techs/*.json}: {@code path} (this technology's own id under the owning mod's
 * namespace), {@code label} (a plain string or an object of locale to string), {@code cost} in
 * research points, an optional {@code prerequisites} array of bare or namespaced references, and
 * an optional {@code effects} array of {@link com.rustorio.api.mod.TechEffect} ids this tech grants
 * while unlocked.
 *
 * <p>Prerequisites and effects are NOT resolved against a live registry here, unlike a recipe's
 * ingredients. A mod may legitimately name a technology or effect registered by a mod that loads
 * later in the same round, so there is nothing to look up yet; {@code ModLoader}'s own validation
 * pass, which runs once every mod has had its say, is where a name matching nothing surfaces.
 */
final class TechJsonLoader {

    private TechJsonLoader() {
    }

    static void loadInto(Path techsDir, ModId modId, RegistrationContext context) {
        for (Path file : JsonNodes.listJsonFilesSorted(techsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            JsonNodes.rejectUnknownFields(root, file, "technology",
                    List.of("path", "label", "cost", "prerequisites", "effects"));
            ContentId id = new ContentId(modId.value(), JsonNodes.requireText(root, "path", file));
            String label = JsonNodes.requireLocalizedText(root, "label", file, id.toString(), ContentLocale.current());
            int cost = JsonNodes.requireInt(root, "cost", file);
            List<ContentId> prerequisites = parseIdList(root, "prerequisites", modId, file);
            List<ContentId> effects = parseIdList(root, "effects", modId, file);
            try {
                context.techs().register(id, new TechType(id, label, cost, prerequisites, effects));
            } catch (IllegalArgumentException e) {
                throw new ModLoadException(file + ": " + e.getMessage(), e);
            }
        }
    }

    private static List<ContentId> parseIdList(JsonNode root, String field, ModId modId, Path file) {
        JsonNode node = root.get(field);
        if (node == null || node.isNull()) {
            return List.of();
        }
        if (!node.isArray()) {
            throw new ModLoadException(file + ": field '" + field + "' must be an array of id references");
        }
        List<ContentId> ids = new ArrayList<>();
        for (JsonNode entry : node) {
            if (!entry.isTextual()) {
                throw new ModLoadException(file + ": every entry of '" + field + "' must be a string");
            }
            String reference = entry.asText();
            ids.add(reference.indexOf(':') >= 0
                    ? ContentId.of(reference)
                    : new ContentId(modId.value(), reference));
        }
        return ids;
    }
}

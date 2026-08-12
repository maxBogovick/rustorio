package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.building.VisibilityRule;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads {@code "visibleWhen"} on a building JSON file — same bare-or-namespaced reference
 * convention as every other loader in this package.
 */
final class VisibilityRuleJsonParser {

    private VisibilityRuleJsonParser() {
    }

    static VisibilityRule parse(JsonNode root, ModId modId, RegistrationContext context, Path file) {
        JsonNode node = root.get("visibleWhen");
        if (node == null || node.isNull()) {
            throw new IllegalStateException("parse called without visibleWhen on " + file);
        }
        return parseNode(node, modId, context, file);
    }

    private static VisibilityRule parseNode(JsonNode node, ModId modId, RegistrationContext context, Path file) {
        if (node.has("produced")) {
            ContentId itemId = resolveRef(JsonNodes.requireText(node, "produced", file), modId);
            return new VisibilityRule.Produced(itemId);
        }
        if (node.has("placed")) {
            ContentId buildingId = resolveRef(JsonNodes.requireText(node, "placed", file), modId);
            return new VisibilityRule.Placed(buildingId);
        }
        if (node.has("unlocked")) {
            ContentId techId = resolveRef(JsonNodes.requireText(node, "unlocked", file), modId);
            return new VisibilityRule.Unlocked(techId);
        }
        if (node.has("effect")) {
            ContentId effectId = resolveRef(JsonNodes.requireText(node, "effect", file), modId);
            return new VisibilityRule.Effect(effectId);
        }
        if (node.has("any")) {
            return new VisibilityRule.Any(parseArray(node.get("any"), modId, context, file, "any"));
        }
        if (node.has("all")) {
            return new VisibilityRule.All(parseArray(node.get("all"), modId, context, file, "all"));
        }
        throw new ModLoadException(file + ": field 'visibleWhen' must name one of produced, placed, "
                + "unlocked, effect, any, all");
    }

    private static List<VisibilityRule> parseArray(JsonNode array, ModId modId, RegistrationContext context,
            Path file, String field) {
        if (array == null || !array.isArray() || array.isEmpty()) {
            throw new ModLoadException(file + ": field 'visibleWhen." + field + "' must be a non-empty array");
        }
        List<VisibilityRule> rules = new ArrayList<>();
        for (JsonNode child : array) {
            rules.add(parseNode(child, modId, context, file));
        }
        return rules;
    }

    private static ContentId resolveRef(String ref, ModId modId) {
        return ref.indexOf(':') >= 0 ? ContentId.of(ref) : new ContentId(modId.value(), ref);
    }
}

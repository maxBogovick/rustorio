package com.rustorio.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemShape;
import java.util.Arrays;
import java.util.Set;

/**
 * Per-field shape checks for the "self-named" content kinds (items, buildings, kinds, maps — all
 * carry their own {@code path}) — cheap, immediate feedback in the editor UI. These deliberately
 * reuse the SAME enums the real loaders ({@code com.rustorio.mod.ItemJsonLoader}/{@code
 * BuildingJsonLoader}) switch on ({@link ItemShape}, {@link BuildingType}), so the editor can never
 * accept a value the game would then reject — but they're not a full substitute for it: {@link
 * ValidateHandler} runs the actual {@code ModLoader.loadAll} before any relaunch, which is the only
 * check that also catches cross-file problems (a building's cost item that doesn't exist, a recipe
 * or building naming a kind that doesn't actually exist) these per-field checks can't see in
 * isolation.
 */
final class Validators {

    private static final Set<String> PLACEMENT_RULES = Set.of("ALWAYS", "NEEDS_ORE", "NEEDS_PASSABLE_TERRAIN");

    private Validators() {
    }

    static void item(String modId, ObjectNode body) {
        String path = EditorJson.requireText(body, "path");
        validateContentIdPath(modId, path);
        EditorJson.requireLabel(body, "label");
        String colorRgb = EditorJson.requireText(body, "colorRgb");
        if (!colorRgb.matches("#[0-9A-Fa-f]{6}")) {
            throw new ApiException(400, "colorRgb must be \"#RRGGBB\": \"" + colorRgb + "\"");
        }
        String shape = EditorJson.requireText(body, "shape");
        if (!isValidEnum(ItemShape.class, shape)) {
            throw new ApiException(400, "unknown shape \"" + shape + "\" (expected one of "
                    + Arrays.toString(ItemShape.values()) + ")");
        }
        JsonNode researchGrade = body.get("researchGrade");
        if (researchGrade != null && !researchGrade.isNull() && !researchGrade.isBoolean()) {
            throw new ApiException(400, "researchGrade must be a boolean");
        }
    }

    static void building(String modId, ObjectNode body) {
        String path = EditorJson.requireText(body, "path");
        validateContentIdPath(modId, path);
        EditorJson.requireLabel(body, "label");
        String archetype = EditorJson.requireText(body, "archetype");
        if (!isValidEnum(BuildingType.class, archetype)) {
            throw new ApiException(400, "unknown archetype \"" + archetype + "\" (expected one of "
                    + Arrays.toString(BuildingType.values()) + ")");
        }
        JsonNode cost = body.get("cost");
        if (cost == null || !cost.isObject() || !cost.hasNonNull("item") || !cost.hasNonNull("amount")) {
            throw new ApiException(400, "'cost' must be an object with 'item' (string) and 'amount' (integer)");
        }
        String placement = EditorJson.requireText(body, "placement");
        if (!PLACEMENT_RULES.contains(placement)) {
            throw new ApiException(400, "unknown placement \"" + placement + "\" (expected one of " + PLACEMENT_RULES + ")");
        }
        EditorJson.requireText(body, "texture");
    }

    static void recipe(ObjectNode body) {
        JsonNode ingredients = body.get("ingredients");
        if (ingredients == null || !ingredients.isArray() || ingredients.isEmpty()) {
            throw new ApiException(400, "'ingredients' must be a non-empty array of item references");
        }
        for (JsonNode ingredient : ingredients) {
            if (!ingredient.isTextual()) {
                throw new ApiException(400, "every element of 'ingredients' must be a string");
            }
        }
        EditorJson.requireText(body, "output");
        EditorJson.requireInt(body, "time");
        // "kind" — one of the 12 BuildingType names, a registered RecipeKind's own path, or a
        // building's own path (RecipeJsonLoader.resolveKind resolves all three the same way). The
        // FRONTEND only ever offers a real one of these (a <select>, not free text — see
        // renderKindSelectOptions in app.js), so a per-field check here would just duplicate that;
        // whether the chosen value actually still exists is a cross-file question this editor
        // defers to ValidateHandler's real ModLoader.loadAll pass (ModLoader#validateContent) for,
        // same as every other content reference.
        EditorJson.requireText(body, "kind");
    }

    static void kind(String modId, ObjectNode body) {
        String path = EditorJson.requireText(body, "path");
        validateContentIdPath(modId, path);
        EditorJson.requireLabel(body, "label");
    }

    private static final Set<String> PAINTABLE_TERRAIN = Set.of("WATER", "ROCK");

    /**
     * Per-field checks for a map's own {@code orePatches}/{@code terrainPatches} circles — whether
     * an {@code ore} reference actually names a registered item is, like a recipe's {@code kind},
     * left to {@link ValidateHandler}'s real {@code ModLoader.loadAll} pass (see that class's own
     * javadoc): this editor offers the item picker as a {@code <select>} of what actually exists, so
     * a per-field check here would only duplicate it.
     */
    static void map(String modId, ObjectNode body) {
        String path = EditorJson.requireText(body, "path");
        validateContentIdPath(modId, path);
        EditorJson.requireLabel(body, "label");
        validatePatchArray(body, "orePatches", true);
        validatePatchArray(body, "terrainPatches", false);
    }

    private static void validatePatchArray(ObjectNode body, String field, boolean ore) {
        JsonNode array = body.get(field);
        if (array == null || array.isNull()) {
            return;
        }
        if (!array.isArray()) {
            throw new ApiException(400, "'" + field + "' must be an array");
        }
        for (JsonNode patch : array) {
            if (!patch.isObject() || !patch.hasNonNull("cx") || !patch.hasNonNull("cy") || !patch.hasNonNull("radius")) {
                throw new ApiException(400, "every entry of '" + field + "' must have integer 'cx', 'cy' and 'radius'");
            }
            if (patch.get("radius").asInt() <= 0) {
                throw new ApiException(400, "'" + field + "' radius must be positive");
            }
            if (ore) {
                if (!patch.hasNonNull("ore") || !patch.get("ore").isTextual()) {
                    throw new ApiException(400, "every entry of 'orePatches' must have a string 'ore' item reference");
                }
            } else {
                String terrain = patch.path("terrain").asText("");
                if (!PAINTABLE_TERRAIN.contains(terrain)) {
                    throw new ApiException(400, "unknown terrain \"" + terrain + "\" (expected one of " + PAINTABLE_TERRAIN + ")");
                }
            }
        }
    }

    private static void validateContentIdPath(String modId, String path) {
        try {
            new ContentId(modId, path);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
    }

    private static <E extends Enum<E>> boolean isValidEnum(Class<E> type, String text) {
        try {
            Enum.valueOf(type, text);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}

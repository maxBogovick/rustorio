package com.rustorio.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemShape;
import java.util.Arrays;
import java.util.Set;

/**
 * Per-field shape checks for the two "self-named" content kinds (items, buildings — both carry
 * their own {@code path}) — cheap, immediate feedback in the editor UI. These deliberately reuse
 * the SAME enums the real loaders ({@code com.rustorio.mod.ItemJsonLoader}/{@code
 * BuildingJsonLoader}) switch on ({@link ItemShape}, {@link BuildingType}), so the editor can never
 * accept a value the game would then reject — but they're not a full substitute for it: {@link
 * ValidateHandler} runs the actual {@code ModLoader.loadAll} before any relaunch, which is the only
 * check that also catches cross-file problems (a building's cost item that doesn't exist, a recipe
 * conflict) these per-field checks can't see in isolation.
 */
final class Validators {

    private static final Set<String> PLACEMENT_RULES = Set.of("ALWAYS", "NEEDS_ORE", "NEEDS_PASSABLE_TERRAIN");

    private Validators() {
    }

    static void item(ObjectNode body) {
        String path = EditorJson.requireText(body, "path");
        validateContentIdPath(path);
        EditorJson.requireText(body, "label");
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

    static void building(ObjectNode body) {
        String path = EditorJson.requireText(body, "path");
        validateContentIdPath(path);
        EditorJson.requireText(body, "label");
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
        String kind = EditorJson.requireText(body, "kind");
        if (!isValidEnum(BuildingType.class, kind)) {
            throw new ApiException(400, "unknown kind \"" + kind + "\" (expected one of "
                    + Arrays.toString(BuildingType.values()) + ")");
        }
    }

    private static void validateContentIdPath(String path) {
        try {
            new ContentId("rustorio", path);
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

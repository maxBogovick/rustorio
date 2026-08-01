package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;

/**
 * Reads {@code content/buildings/*.json} — a building PROTOTYPE as pure data (cost, placement,
 * texture, tuning), reusing an EXISTING archetype's Java behavior rather than writing a new class.
 * {@code archetype} names one of the 12 {@link BuildingType} constants; its {@code behavior}/{@code
 * restoreBehavior}/{@code codec} are borrowed wholesale from {@link VanillaBuildings#frozen()} —
 * the exact mechanism {@code com.examplemod.ExampleMod} already proved for its "steel press" in
 * Phases 5-6, here driven by JSON instead of a Java literal. Genuinely NEW behavior stays a code
 * mod's job, never this loader's — it never constructs a {@code Building} itself, only data.
 *
 * <p>Two more optional fields make a {@code Furnace}-archetype building (FURNACE/PRESS/ASSEMBLER)
 * fully self-contained with no Java at all: {@code "kind"} (bare/namespaced, same convention as
 * {@code "cost".item} below) names this building's own recipe pool — defaults to its own {@code
 * id} when omitted, a PRIVATE pool no other building shares unless it explicitly names the same
 * one — and {@code "fuel"} (an item reference, same resolution) names what it burns as fuel, or is
 * simply omitted for none. See {@link BuildingPrototype}'s own javadoc for {@code recipeKind}/
 * {@code fuelItem}.
 */
final class BuildingJsonLoader {

    private static final Map<String, PlacementRule> PLACEMENT_RULES = Map.of(
            "ALWAYS", PlacementRule.ALWAYS,
            "NEEDS_ORE", PlacementRule.NEEDS_ORE,
            "NEEDS_PASSABLE_TERRAIN", PlacementRule.NEEDS_PASSABLE_TERRAIN);

    private BuildingJsonLoader() {
    }

    static void loadInto(Path buildingsDir, ModId modId, RegistrationContext context) {
        for (Path file : JsonNodes.listJsonFilesSorted(buildingsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            String path = JsonNodes.requireText(root, "path", file);
            ContentId id = new ContentId(modId.value(), path);
            String label = JsonNodes.requireLocalizedText(root, "label", file, id.toString(), ContentLocale.current());
            BuildingType archetype = parseArchetype(JsonNodes.requireText(root, "archetype", file), file);
            BuildingPrototype archetypePrototype = VanillaBuildings.frozen().get(VanillaBuildings.idFor(archetype));

            JsonNode costNode = root.get("cost");
            if (costNode == null) {
                throw new ModLoadException(file + ": missing required object field 'cost'");
            }
            String costItemRef = JsonNodes.requireText(costNode, "item", file);
            int costAmount = JsonNodes.requireInt(costNode, "amount", file);
            ItemType costItem = resolveItem(costItemRef, modId, context, file);

            PlacementRule placement = parsePlacement(JsonNodes.requireText(root, "placement", file), file);
            ContentId texture = ContentId.of(JsonNodes.requireText(root, "texture", file));
            int footprintWidth = root.has("footprintWidth") ? JsonNodes.requireInt(root, "footprintWidth", file) : 1;
            int footprintHeight = root.has("footprintHeight") ? JsonNodes.requireInt(root, "footprintHeight", file) : 1;
            int bufferMax = root.has("bufferMax") ? JsonNodes.requireInt(root, "bufferMax", file) : 0;
            int speedMultiplier = root.has("speedMultiplier") ? JsonNodes.requireInt(root, "speedMultiplier", file) : 1;
            boolean acceptsSpeedEffects = JsonNodes.optionalBoolean(root, "acceptsSpeedEffects", false);
            // Private pool by default (this building's own id) — see the class javadoc.
            ContentId recipeKind = root.has("kind") ? resolveKind(JsonNodes.requireText(root, "kind", file), modId) : id;
            ItemType fuelItem = root.has("fuel") ? resolveItem(JsonNodes.requireText(root, "fuel", file), modId, context, file) : null;

            context.buildings().register(id, new BuildingPrototype(id, label, new BuildingCost(costItem, costAmount),
                    placement, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier, acceptsSpeedEffects,
                    archetypePrototype.behavior(), archetypePrototype.restoreBehavior(), archetypePrototype.codec(),
                    recipeKind, fuelItem));
        }
    }

    private static ItemType resolveItem(String ref, ModId modId, RegistrationContext context, Path file) {
        ContentId id = resolveRef(ref, modId);
        return context.items().peek(id).orElseThrow(() -> new ModLoadException(file + ": item '" + id
                + "' referenced by this building's cost is not registered"));
    }

    /** Bare path (resolved in the CURRENT mod's own namespace) or a full {@code "namespace:path"} — the same convention every content reference in this loader follows. */
    private static ContentId resolveRef(String ref, ModId modId) {
        return ref.indexOf(':') >= 0 ? ContentId.of(ref) : new ContentId(modId.value(), ref);
    }

    /**
     * A {@code "kind"} value is either one of the 12 {@link BuildingType} names (opts this
     * building into that kind's own shared vanilla pool — {@code ContentId} segments are lowercase
     * only, so {@code "PRESS"} would otherwise fail {@link #resolveRef} outright) or a
     * bare/namespaced reference, exactly mirroring {@code RecipeJsonLoader.resolveKind} — the two
     * MUST agree, since a recipe's own {@code "kind"} is resolved the identical way.
     */
    private static ContentId resolveKind(String text, ModId modId) {
        for (BuildingType type : BuildingType.values()) {
            if (type.name().equals(text)) {
                return type.contentId();
            }
        }
        return resolveRef(text, modId);
    }

    private static BuildingType parseArchetype(String text, Path file) {
        try {
            return BuildingType.valueOf(text);
        } catch (IllegalArgumentException e) {
            throw new ModLoadException(file + ": unknown 'archetype' \"" + text + "\" (expected one of "
                    + Arrays.toString(BuildingType.values()) + ")");
        }
    }

    private static PlacementRule parsePlacement(String text, Path file) {
        PlacementRule rule = PLACEMENT_RULES.get(text);
        if (rule == null) {
            throw new ModLoadException(file + ": unknown 'placement' \"" + text + "\" (expected one of "
                    + PLACEMENT_RULES.keySet() + ")");
        }
        return rule;
    }
}

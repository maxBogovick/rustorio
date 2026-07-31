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
            String label = JsonNodes.requireText(root, "label", file);
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

            ContentId id = new ContentId(modId.value(), path);
            context.buildings().register(id, new BuildingPrototype(id, label, new BuildingCost(costItem, costAmount),
                    placement, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier, acceptsSpeedEffects,
                    archetypePrototype.behavior(), archetypePrototype.restoreBehavior(), archetypePrototype.codec()));
        }
    }

    private static ItemType resolveItem(String ref, ModId modId, RegistrationContext context, Path file) {
        ContentId id = ref.indexOf(':') >= 0 ? ContentId.of(ref) : new ContentId(modId.value(), ref);
        return context.items().peek(id).orElseThrow(() -> new ModLoadException(file + ": item '" + id
                + "' referenced by this building's cost is not registered"));
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

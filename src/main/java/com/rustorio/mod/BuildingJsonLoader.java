package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TraitKey;
import com.rustorio.domain.building.VanillaCategories;
import com.rustorio.domain.building.Traits;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.VanillaTraits;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Reads {@code content/buildings/*.json} — a building PROTOTYPE as pure data (cost, placement,
 * texture, tuning), reusing an EXISTING archetype's Java behavior rather than writing a new class.
 * {@code archetype} names one of the 12 {@link BuildingType} constants; its {@code behavior}/{@code
 * restoreBehavior}/{@code codec} are borrowed wholesale from {@link VanillaBuildings#frozen()} —
 * the exact mechanism {@code com.examplemod.ExampleMod} already proved for its "steel press" in
 * Phases 5-6, here driven by JSON instead of a Java literal. Genuinely NEW behavior stays a code
 * mod's job, never this loader's — it never constructs a {@code Building} itself, only data.
 *
 * <p>Two optional fields, {@code "fluidInput"} and {@code "fluidOutput"}, name the fluids a
 * {@code Pump}- or {@code Boiler}-archetype building draws and produces — a fluid reference in the
 * same bare-or-namespaced form as every other reference here. Together with {@code "fuel"} and
 * {@code "placement": "ADJACENT_TO_WATER"} they are what make a mod's own oil derrick or refinery a
 * JSON file rather than a Java class.
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

    private BuildingJsonLoader() {
    }

    static void loadInto(Path buildingsDir, ModId modId, RegistrationContext context) {
        for (Path file : JsonNodes.listJsonFilesSorted(buildingsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            JsonNodes.rejectUnknownFields(root, file, "building", List.of("path", "label", "archetype",
                    "cost", "placement", "texture", "footprintWidth", "footprintHeight", "bufferMax",
                    "speedMultiplier", "acceptsSpeedEffects", "kind", "fuel", "fluidInput", "fluidOutput",
                    "power", "category"));
            String path = JsonNodes.requireText(root, "path", file);
            ContentId id = new ContentId(modId.value(), path);
            String label = JsonNodes.requireLocalizedText(root, "label", file, id.toString(), ContentLocale.current());
            BuildingType archetype = parseArchetype(JsonNodes.requireText(root, "archetype", file), file);
            BuildingPrototype archetypePrototype = VanillaBuildings.frozen().get(VanillaBuildings.idFor(archetype));

            JsonNode costNode = root.get("cost");
            if (costNode == null) {
                throw new ModLoadException(file + ": missing required object field 'cost'");
            }
            JsonNodes.rejectUnknownFields(costNode, file, "building 'cost'", List.of("item", "amount"));
            String costItemRef = JsonNodes.requireText(costNode, "item", file);
            int costAmount = JsonNodes.requireInt(costNode, "amount", file);
            ItemType costItem = resolveItem(costItemRef, modId, context, file);

            PlacementRule placement = parsePlacement(JsonNodes.requireText(root, "placement", file), modId, context, file);
            ContentId texture = ContentId.of(JsonNodes.requireText(root, "texture", file));
            int footprintWidth = root.has("footprintWidth") ? JsonNodes.requireInt(root, "footprintWidth", file) : 1;
            int footprintHeight = root.has("footprintHeight") ? JsonNodes.requireInt(root, "footprintHeight", file) : 1;
            int bufferMax = root.has("bufferMax") ? JsonNodes.requireInt(root, "bufferMax", file) : 0;
            int speedMultiplier = root.has("speedMultiplier") ? JsonNodes.requireInt(root, "speedMultiplier", file) : 1;
            boolean acceptsSpeedEffects = JsonNodes.optionalBoolean(root, "acceptsSpeedEffects", false);
            // Private pool by default (this building's own id) — see the class javadoc.
            ContentId recipeKind = root.has("kind") ? resolveKind(JsonNodes.requireText(root, "kind", file), modId) : id;
            ItemType fuelItem = root.has("fuel") ? resolveItem(JsonNodes.requireText(root, "fuel", file), modId, context, file) : null;
            // A bag, not three named locals: the two fluid ports and the power block are read the
            // same way any future trait will be, and nothing below this line mentions them by name.
            Map<TraitKey<?>, Object> traits = new LinkedHashMap<>();
            traits.put(VanillaTraits.FLUID_INPUT, resolveOptionalFluid(root, "fluidInput", modId, context, file));
            traits.put(VanillaTraits.FLUID_OUTPUT, resolveOptionalFluid(root, "fluidOutput", modId, context, file));
            traits.put(VanillaTraits.POWER, readOptionalPower(root, file));
            traits.put(VanillaCategories.CATEGORY, readOptionalCategory(root, modId));

            context.buildings().register(id, new BuildingPrototype(id, label, new BuildingCost(costItem, costAmount),
                    placement, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier, acceptsSpeedEffects,
                    archetypePrototype.behavior(), archetypePrototype.restoreBehavior(), archetypePrototype.codec(),
                    recipeKind, fuelItem, Traits.of(traits)));
        }
    }

    /**
     * {@code "power"} — {@code { "radius": n }} for a pole, {@code { "output": n }} for a
     * generator, {@code { "demand": n }} for a machine that needs electricity, or absent for a
     * building with nothing to do with power (which is nearly every one). Absent means {@code null}
     * rather than a zeroed spec: "not electrical" and "electrical, asking for nothing" are different
     * claims, and only the first is what an ordinary building means.
     */
    private static @Nullable PowerSpec readOptionalPower(JsonNode root, Path file) {
        JsonNode power = root.get("power");
        if (power == null || power.isNull()) {
            return null;
        }
        if (!power.isObject()) {
            throw new ModLoadException(file + ": field 'power' must be an object like "
                    + "{ \"radius\": 5 }, { \"output\": 100 } or { \"demand\": 10 }");
        }
        // The nested block the published schema cannot police: ContentSchemaTest only compares
        // top-level keys, so a misspelled "demmand" inside here was invisible from both ends —
        // silently a building that declares it is electrical and then asks for nothing.
        JsonNodes.rejectUnknownFields(power, file, "building 'power'", List.of("radius", "output", "demand"));
        if (power.isEmpty()) {
            throw new ModLoadException(file + ": field 'power' is empty — omit it entirely for a "
                    + "building that has nothing to do with electricity, since an empty block still "
                    + "declares one that does and asks for nothing");
        }
        return new PowerSpec(optionalCount(power, "radius", file), optionalCount(power, "output", file),
                optionalCount(power, "demand", file));
    }

    /** One non-negative number inside {@code "power"} — absent means zero, which is what "this building is not that kind of electrical thing" looks like. */
    private static int optionalCount(JsonNode power, String field, Path file) {
        if (!power.has(field)) {
            return 0;
        }
        int value = JsonNodes.requireInt(power, field, file);
        if (value < 0) {
            throw new ModLoadException(file + ": field 'power." + field + "' must not be negative: " + value);
        }
        return value;
    }

    /**
     * {@code "fluidInput"}/{@code "fluidOutput"} — a fluid reference in the same bare-or-namespaced
     * form every other reference in this loader takes, or absent for a building that touches no
     * fluid (which is almost all of them). Reported against the FILE and the field, like every other
     * error here, so a modder who misspells a fluid learns which line to fix.
     */
    /**
     * Which build-panel tab this building belongs in. Optional, and absent means {@link
     * VanillaCategories#OTHER} — every mod written before this key existed must keep loading, so a
     * missing category can never be an error.
     *
     * <p>Bare {@code "logistics"} resolves against {@code rustorio}, not against the declaring mod:
     * a modder writing {@code "category": "logistics"} means the vanilla tab, the same convention
     * {@code "cost".item} and {@code "kind"} already follow for references. A namespaced value
     * ({@code "mymod:robots"}) is taken as-is and becomes a tab of its own.
     */
    private static @Nullable ContentId readOptionalCategory(JsonNode root, ModId modId) {
        String text = JsonNodes.optionalText(root, "category", "");
        if (text.isBlank()) {
            return null;
        }
        return text.indexOf(':') >= 0 ? ContentId.of(text) : new ContentId("rustorio", text);
    }

    private static @Nullable FluidType resolveOptionalFluid(JsonNode root, String field, ModId modId,
            RegistrationContext context, Path file) {
        if (!root.has(field)) {
            return null;
        }
        ContentId id = resolveRef(JsonNodes.requireText(root, field, file), modId);
        return context.fluids().peek(id).orElseThrow(() -> new ModLoadException(file + ": field '" + field
                + "' refers to unknown fluid '" + id + "'"));
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

    /**
     * A {@code "placement"} value is either one of the vanilla rule names in the {@code
     * NEEDS_ORE} spelling every building file has always used, or a bare/namespaced reference to a
     * rule some code mod registered — resolved exactly the way {@code "kind"} is above, and for the
     * same reason: the uppercase names predate rules being registered content, and a {@link
     * ContentId} path is lowercase, so the two spellings can never collide.
     */
    private static PlacementRule parsePlacement(String text, ModId modId, RegistrationContext context, Path file) {
        ContentId id = text.equals(text.toUpperCase(Locale.ROOT))
                ? new ContentId("rustorio", text.toLowerCase(Locale.ROOT))
                : resolveRef(text, modId);
        return context.placementRules().peek(id).orElseThrow(() -> new ModLoadException(
                file + ": unknown 'placement' \"" + text + "\" (expected one of "
                        + context.placementRules().knownIds() + ")"));
    }
}

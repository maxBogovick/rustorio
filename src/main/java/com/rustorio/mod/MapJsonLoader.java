package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OrePatch;
import com.rustorio.domain.Terrain;
import com.rustorio.domain.TerrainPatch;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads {@code content/maps/*.json} — a hand-placed {@link AuthoredMap}: {@code path} (this map's
 * own {@link ContentId} path) and two optional circle-patch arrays, {@code orePatches} ({@code cx},
 * {@code cy}, {@code radius}, {@code ore} — an item reference, same bare/namespaced convention as
 * {@link BuildingJsonLoader}'s {@code cost.item}) and {@code terrainPatches} ({@code cx}, {@code
 * cy}, {@code radius}, {@code terrain} — {@code "WATER"} or {@code "ROCK"}; {@code "GROUND"} is
 * never painted, it's simply what a cell is when no patch claims it, exactly like {@link
 * com.rustorio.domain.PatchOreLayout}'s own hardcoded map).
 *
 * <p>Loaded AFTER items (see {@link ContentJsonLoader}): an ore patch's {@code ore} reference is
 * resolved and validated immediately, against the items already registered so far, the same way
 * {@link BuildingJsonLoader#resolveItem} validates a building's cost item — a map naming an unknown
 * item fails to load with the file and field named, not silently at some later, harder-to-trace
 * point.
 */
final class MapJsonLoader {

    private static final Map<String, Terrain> PAINTABLE_TERRAIN = Map.of("WATER", Terrain.WATER, "ROCK", Terrain.ROCK);

    private MapJsonLoader() {
    }

    static void loadInto(Path mapsDir, ModId modId, RegistrationContext context) {
        for (Path file : JsonNodes.listJsonFilesSorted(mapsDir)) {
            JsonNode root = JsonNodes.readTree(file);
            String path = JsonNodes.requireText(root, "path", file);
            ContentId id = new ContentId(modId.value(), path);

            List<OrePatch> orePatches = new ArrayList<>();
            for (JsonNode patchNode : optionalArray(root, "orePatches", file)) {
                ItemType ore = resolveItem(JsonNodes.requireText(patchNode, "ore", file), modId, context, file);
                orePatches.add(new OrePatch(JsonNodes.requireInt(patchNode, "cx", file), JsonNodes.requireInt(patchNode, "cy", file),
                        requirePositiveRadius(patchNode, file), ore));
            }

            List<TerrainPatch> terrainPatches = new ArrayList<>();
            for (JsonNode patchNode : optionalArray(root, "terrainPatches", file)) {
                Terrain terrain = parseTerrain(JsonNodes.requireText(patchNode, "terrain", file), file);
                terrainPatches.add(new TerrainPatch(JsonNodes.requireInt(patchNode, "cx", file), JsonNodes.requireInt(patchNode, "cy", file),
                        requirePositiveRadius(patchNode, file), terrain));
            }

            context.maps().register(id, new AuthoredMap(id, orePatches, terrainPatches));
        }
    }

    private static List<JsonNode> optionalArray(JsonNode root, String field, Path file) {
        JsonNode value = root.get(field);
        if (value == null || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new ModLoadException(file + ": field '" + field + "' must be an array");
        }
        List<JsonNode> elements = new ArrayList<>();
        value.forEach(elements::add);
        return elements;
    }

    private static int requirePositiveRadius(JsonNode patchNode, Path file) {
        int radius = JsonNodes.requireInt(patchNode, "radius", file);
        if (radius <= 0) {
            throw new ModLoadException(file + ": patch 'radius' must be positive, was " + radius);
        }
        return radius;
    }

    private static Terrain parseTerrain(String text, Path file) {
        Terrain terrain = PAINTABLE_TERRAIN.get(text);
        if (terrain == null) {
            throw new ModLoadException(file + ": unknown 'terrain' \"" + text + "\" (expected one of " + PAINTABLE_TERRAIN.keySet() + ")");
        }
        return terrain;
    }

    private static ItemType resolveItem(String ref, ModId modId, RegistrationContext context, Path file) {
        ContentId id = resolveRef(ref, modId);
        return context.items().peek(id).orElseThrow(() -> new ModLoadException(file + ": ore patch references unknown item '" + id + "'"));
    }

    /** Bare path (resolved in the CURRENT mod's own namespace) or a full {@code "namespace:path"} — same convention as {@link BuildingJsonLoader#resolveRef}. */
    private static ContentId resolveRef(String ref, ModId modId) {
        return ref.indexOf(':') >= 0 ? ContentId.of(ref) : new ContentId(modId.value(), ref);
    }
}

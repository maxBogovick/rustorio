package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OrePatch;
import com.rustorio.domain.TerrainPatch;
import com.rustorio.domain.VanillaItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link MapJsonLoader}: reads a mod-authored {@code content/maps/*.json} into an {@link
 * AuthoredMap} — the same "immediate, file-named validation" contract {@link BuildingJsonLoader}
 * already holds its own item reference to, since a broken map should fail loudly at load time, not
 * silently produce an empty or wrong ore layout.
 */
class MapJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void loadsOreAndTerrainPatchesResolvingBareAndNamespacedItemRefs() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        ContentId ownItemId = new ContentId("testmod", "moonrock");
        context.items().register(ownItemId, new ItemType(ownItemId, "Moonrock", false, 0xaaaaaa, ItemShape.CIRCLE));
        write("valley.json", """
                { "path": "valley", "label": "Valley",
                  "orePatches": [
                    { "cx": 6, "cy": 5, "radius": 3, "ore": "moonrock" },
                    { "cx": 20, "cy": 20, "radius": 2, "ore": "rustorio:coal" }
                  ],
                  "terrainPatches": [
                    { "cx": 50, "cy": 50, "radius": 8, "terrain": "WATER" }
                  ] }
                """);

        MapJsonLoader.loadInto(tempDir, modId, context);

        AuthoredMap map = context.maps().peek(ContentId.of("testmod:valley")).orElseThrow();
        assertEquals(2, map.orePatches().size());
        assertEquals(6, map.orePatches().get(0).cx(), "a bare 'moonrock' reference must resolve in the CURRENT mod's own namespace");
        assertEquals(new OrePatch(20, 20, 2, VanillaItems.COAL), map.orePatches().get(1),
                "a full 'rustorio:coal' reference must resolve regardless of which mod declared the map");
        assertEquals(List.of(new TerrainPatch(50, 50, 8, VanillaItems.WATER)), map.terrainPatches(),
                "the legacy \"WATER\" spelling every map file written before terrain became content still uses must keep resolving");
    }

    @Test
    void mapWithNoPatchesAtAllLoadsAsAnEmptyMap() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("blank.json", """
                { "path": "blank", "label": "Blank" }
                """);

        MapJsonLoader.loadInto(tempDir, modId, context);

        AuthoredMap map = context.maps().peek(ContentId.of("testmod:blank")).orElseThrow();
        assertTrue(map.orePatches().isEmpty());
        assertTrue(map.terrainPatches().isEmpty());
    }

    @Test
    void unresolvedOreReferenceFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird",
                  "orePatches": [ { "cx": 1, "cy": 1, "radius": 1, "ore": "unobtainium" } ] }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> MapJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("testmod:unobtainium"), thrown.getMessage());
    }

    /**
     * The point of terrain becoming content: a mod's own item can be an obstacle, without the
     * engine having heard of it. Fails on the old code, where terrain was an enum of exactly
     * WATER and ROCK and anything else was rejected by name.
     */
    @Test
    void terrainPatchResolvesAModsOwnItem() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        ContentId swampId = new ContentId("testmod", "swamp");
        ItemType swamp = new ItemType(swampId, "Swamp", false, 0x3a5f3a, ItemShape.SQUARE);
        context.items().register(swampId, swamp);
        write("bog.json", """
                { "path": "bog", "label": "Bog",
                  "terrainPatches": [ { "cx": 7, "cy": 8, "radius": 4, "terrain": "swamp" } ] }
                """);

        MapJsonLoader.loadInto(tempDir, modId, context);

        AuthoredMap map = context.maps().peek(ContentId.of("testmod:bog")).orElseThrow();
        assertEquals(List.of(new TerrainPatch(7, 8, 4, swamp)), map.terrainPatches());
    }

    @Test
    void unresolvedTerrainReferenceFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird",
                  "terrainPatches": [ { "cx": 1, "cy": 1, "radius": 1, "terrain": "quicksand" } ] }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> MapJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("testmod:quicksand"), thrown.getMessage());
    }

    @Test
    void nonPositiveRadiusFailsWithAClearMessage() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        VanillaItems.registerAll(context.items());
        write("weird.json", """
                { "path": "weird", "label": "Weird",
                  "orePatches": [ { "cx": 1, "cy": 1, "radius": 0, "ore": "rustorio:iron_ore" } ] }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> MapJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains("radius"), thrown.getMessage());
    }

    private void write(String fileName, String content) throws IOException {
        Files.createDirectories(tempDir);
        Files.writeString(tempDir.resolve(fileName), content);
    }
}

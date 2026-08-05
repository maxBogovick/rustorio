package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void loadsARecipeReferencingItsOwnModsItems() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        ContentId oreId = ContentId.of("testmod:copper_ore");
        ContentId plateId = ContentId.of("testmod:copper_plate");
        context.items().register(oreId, new ItemType(oreId, "Copper Ore", false, 0xB87333, ItemShape.CIRCLE));
        context.items().register(plateId, new ItemType(plateId, "Copper Plate", false, 0xCC8844, ItemShape.SQUARE));
        write("smelt.json", """
                { "ingredients": ["copper_ore"], "output": "copper_plate", "time": 5, "kind": "FURNACE" }
                """);

        RecipeJsonLoader.loadInto(tempDir, modId, context);

        // peek(), not get(0): this test drives the loader straight, with no freeze() after it,
        // and the id below is the one the loader derives from the file name.
        Recipe recipe = context.recipes().peek(ContentId.of("testmod:smelt")).orElseThrow();
        assertEquals(plateId, recipe.output().id());
        assertEquals(5, recipe.time());
        assertEquals(BuildingType.FURNACE.contentId(), recipe.type(), "a BuildingType name joins that kind's own shared vanilla pool");
    }

    @Test
    void resolvesAFullyQualifiedReferenceToAnotherModsItem() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        ContentId vanillaIron = ContentId.of("rustorio:iron_plate");
        ContentId output = ContentId.of("testmod:gizmo");
        context.items().register(vanillaIron, new ItemType(vanillaIron, "Iron Plate", false, 1, ItemShape.SQUARE));
        context.items().register(output, new ItemType(output, "Gizmo", true, 2, ItemShape.TRIANGLE));
        write("gizmo.json", """
                { "ingredients": ["rustorio:iron_plate"], "output": "gizmo", "time": 3, "kind": "PRESS" }
                """);

        RecipeJsonLoader.loadInto(tempDir, modId, context);

        assertEquals(vanillaIron,
                context.recipes().peek(ContentId.of("testmod:gizmo")).orElseThrow().ingredients().get(0).id());
    }

    @Test
    void referencingAnUnregisteredItemNamesTheFileAndTheMissingId() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        Path file = write("broken.json", """
                { "ingredients": ["ghost_ore"], "output": "ghost_ore", "time": 1, "kind": "FURNACE" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class,
                () -> RecipeJsonLoader.loadInto(tempDir, modId, context));
        assertTrue(thrown.getMessage().contains(file.toString()));
        assertTrue(thrown.getMessage().contains("testmod:ghost_ore"));
    }

    /**
     * A "kind" that isn't one of the 12 {@link BuildingType} names is no longer an error — it's a
     * custom archetype's own private recipe pool (a {@code BuildingJsonLoader}-configured
     * building's own {@code "kind"} field resolves the SAME way), the whole point of this feature:
     * a JSON-only mod names any pool it likes, with no Java and no fixed enum to pick from.
     */
    @Test
    void customKindResolvesAsABareReferenceInThisModsOwnNamespace() throws IOException {
        GameRegistrationContext context = new GameRegistrationContext();
        ContentId item = ContentId.of("testmod:x");
        context.items().register(item, new ItemType(item, "X", false, 0, ItemShape.CIRCLE));
        write("weird.json", """
                { "ingredients": ["x"], "output": "x", "time": 1, "kind": "replicator" }
                """);

        RecipeJsonLoader.loadInto(tempDir, modId, context);

        assertEquals(ContentId.of("testmod:replicator"),
                context.recipes().peek(ContentId.of("testmod:weird")).orElseThrow().type());
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}

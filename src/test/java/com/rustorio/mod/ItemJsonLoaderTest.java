package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.model.ItemType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ItemJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void loadsAValidItem() throws IOException {
        write("copper_ore.json", """
                { "path": "copper_ore", "label": "Copper Ore", "researchGrade": false,
                  "colorRgb": "#B87333", "shape": "CIRCLE" }
                """);

        Registry<ItemType> items = new Registry<>();
        ItemJsonLoader.loadInto(tempDir, modId, items);

        ItemType item = items.peek(ContentId.of("testmod:copper_ore")).orElseThrow();
        assertEquals("Copper Ore", item.label());
        assertEquals(false, item.researchGrade());
        assertEquals(0xB87333, item.colorRgb());
        assertEquals(ItemShape.CIRCLE, item.shape());
    }

    @Test
    void researchGradeDefaultsToFalse() throws IOException {
        write("gear.json", """
                { "path": "gear", "label": "Gear", "colorRgb": "#FFFFFF", "shape": "TRIANGLE" }
                """);

        Registry<ItemType> items = new Registry<>();
        ItemJsonLoader.loadInto(tempDir, modId, items);

        assertEquals(false, items.peek(ContentId.of("testmod:gear")).orElseThrow().researchGrade());
    }

    @Test
    void missingDirectoryIsNotAnError() {
        Registry<ItemType> items = new Registry<>();
        ItemJsonLoader.loadInto(tempDir.resolve("does-not-exist"), modId, items);
        // No exception, nothing registered — nothing further to assert.
    }

    @Test
    void malformedColorNamesTheFileAndField() throws IOException {
        Path file = write("bad_color.json", """
                { "path": "bad_color", "label": "Bad", "colorRgb": "red", "shape": "CIRCLE" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class,
                () -> ItemJsonLoader.loadInto(tempDir, modId, new Registry<>()));
        assertTrue(thrown.getMessage().contains(file.toString()));
        assertTrue(thrown.getMessage().contains("colorRgb"));
    }

    @Test
    void unknownShapeNamesTheAllowedValues() throws IOException {
        write("bad_shape.json", """
                { "path": "bad_shape", "label": "Bad", "colorRgb": "#FFFFFF", "shape": "HEXAGON" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class,
                () -> ItemJsonLoader.loadInto(tempDir, modId, new Registry<>()));
        assertTrue(thrown.getMessage().contains("CIRCLE"));
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}

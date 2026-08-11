package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.RecipeKind;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecipeKindJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void loadsAValidKind() throws IOException {
        write("steel_smelting.json", """
                { "path": "steel_smelting", "label": "Steel Smelting" }
                """);

        Registry<RecipeKind> kinds = new Registry<>();
        RecipeKindJsonLoader.loadInto(tempDir, modId, kinds);

        RecipeKind kind = kinds.peek(ContentId.of("testmod:steel_smelting")).orElseThrow();
        assertEquals("Steel Smelting", kind.label());
        assertEquals(ContentId.of("testmod:steel_smelting"), kind.id());
    }

    @Test
    void missingDirectoryIsNotAnError() {
        Registry<RecipeKind> kinds = new Registry<>();
        RecipeKindJsonLoader.loadInto(tempDir.resolve("does-not-exist"), modId, kinds);
        // No exception, nothing registered — nothing further to assert.
    }

    @Test
    void missingLabelNamesTheFileAndField() throws IOException {
        Path file = write("no_label.json", """
                { "path": "no_label" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class,
                () -> RecipeKindJsonLoader.loadInto(tempDir, modId, new Registry<>()));
        assertTrue(thrown.getMessage().contains(file.toString()));
        assertTrue(thrown.getMessage().contains("label"));
    }

    private Path write(String fileName, String content) throws IOException {
        Path file = tempDir.resolve(fileName);
        Files.writeString(file, content);
        return file;
    }
}

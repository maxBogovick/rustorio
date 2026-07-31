package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModJsonReaderTest {

    @TempDir
    Path tempDir;

    private final ModJsonReader reader = new ModJsonReader();

    @Test
    void parsesAValidCodeModDescriptor() throws IOException {
        Path file = writeModJson("""
                {
                  "id": "examplemod",
                  "version": "1.0.0",
                  "entryPoint": "com.example.mod.ExampleMod",
                  "dependencies": [ { "modId": "rustorio", "range": ">=1.0.0" } ]
                }
                """);

        ModDescriptor descriptor = reader.read(file);

        assertEquals(new ModId("examplemod"), descriptor.id());
        assertEquals(SemVer.parse("1.0.0"), descriptor.version());
        assertEquals("com.example.mod.ExampleMod", descriptor.entryPoint());
        assertEquals(1, descriptor.dependencies().size());
        assertEquals(new ModId("rustorio"), descriptor.dependencies().get(0).modId());
        assertTrue(descriptor.dependencies().get(0).range().matches(SemVer.parse("1.5.0")));
    }

    @Test
    void entryPointIsNullForADataOnlyMod() throws IOException {
        Path file = writeModJson("""
                { "id": "puredata", "version": "1.0.0" }
                """);

        ModDescriptor descriptor = reader.read(file);

        assertNull(descriptor.entryPoint());
        assertEquals(0, descriptor.dependencies().size());
    }

    @Test
    void missingIdFieldNamesTheFileAndTheField() throws IOException {
        Path file = writeModJson("""
                { "version": "1.0.0" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> reader.read(file));

        assertTrue(thrown.getMessage().contains(file.toString()), "message must name the file");
        assertTrue(thrown.getMessage().contains("'id'"), "message must name the missing field");
    }

    @Test
    void malformedVersionNamesTheField() throws IOException {
        Path file = writeModJson("""
                { "id": "examplemod", "version": "not-a-version" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> reader.read(file));

        assertTrue(thrown.getMessage().contains("'version'"));
    }

    @Test
    void invalidModIdFormatIsRejected() throws IOException {
        Path file = writeModJson("""
                { "id": "Not-Valid", "version": "1.0.0" }
                """);

        assertThrows(ModLoadException.class, () -> reader.read(file));
    }

    @Test
    void dependenciesFieldMustBeAnArray() throws IOException {
        Path file = writeModJson("""
                { "id": "examplemod", "version": "1.0.0", "dependencies": "rustorio" }
                """);

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> reader.read(file));
        assertTrue(thrown.getMessage().contains("'dependencies'"));
    }

    @Test
    void dependencyRangeDefaultsToStarWhenAbsent() throws IOException {
        Path file = writeModJson("""
                { "id": "examplemod", "version": "1.0.0",
                  "dependencies": [ { "modId": "rustorio" } ] }
                """);

        ModDescriptor descriptor = reader.read(file);

        assertTrue(descriptor.dependencies().get(0).range().matches(SemVer.parse("0.0.1")),
                "an absent range must accept any version, same as an explicit \"*\"");
    }

    private Path writeModJson(String content) throws IOException {
        Path file = tempDir.resolve("mod.json");
        Files.writeString(file, content);
        return file;
    }
}

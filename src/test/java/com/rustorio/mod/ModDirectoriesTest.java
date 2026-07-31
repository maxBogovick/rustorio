package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModDirectoriesTest {

    @TempDir
    Path tempDir;

    @Test
    void listsOnlySubdirectoriesSortedByName() throws IOException {
        Files.createDirectory(tempDir.resolve("zeta"));
        Files.createDirectory(tempDir.resolve("alpha"));
        Files.createFile(tempDir.resolve("not_a_mod.txt"));

        List<Path> found = ModDirectories.discover(tempDir);

        assertEquals(List.of(tempDir.resolve("alpha"), tempDir.resolve("zeta")), found);
    }

    @Test
    void missingModsRootIsEmptyNotAnError() {
        assertEquals(List.of(), ModDirectories.discover(tempDir.resolve("does_not_exist")));
    }
}

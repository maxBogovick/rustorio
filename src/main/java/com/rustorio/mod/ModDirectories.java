package com.rustorio.mod;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Turns "the mods folder" into the {@code List<Path>} {@link ModLoader#loadAll} wants — every
 * direct subdirectory of {@code modsRoot}, sorted by name (the same order {@link ModLoader} would
 * re-sort into anyway; sorted here too so callers that print or diff this list get a stable one).
 * A missing {@code modsRoot} isn't an error: it means "no mods installed", same as an empty
 * {@code content/} subdirectory isn't an error to the content loaders.
 *
 * <p>Exists so a real game bootstrap ({@code com.graphics.screen.GameScreen}) and the local
 * content editor (a separate dev tool, {@code com.rustorio.editor}) discover mods the exact same
 * way {@code PhaseSevenAcceptanceTest} already proves the loader itself works with — one place
 * that knows "a mod is a directory directly under {@code resources/mods/}", not two.
 */
public final class ModDirectories {

    private ModDirectories() {
    }

    public static List<Path> discover(Path modsRoot) {
        if (!Files.isDirectory(modsRoot)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(modsRoot)) {
            return entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list mod directories under " + modsRoot, e);
        }
    }
}

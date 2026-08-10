package com.rustorio.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Locates what the build PRODUCED, for the ratchets that check artifacts rather than source text.
 * {@link SourceCodeScanner} is the same idea one step earlier in the pipeline: it reads {@code
 * src/main/java}, this reads {@code build/}.
 *
 * <p>Paths are relative, exactly like {@code SourceCodeScanner}'s callers ({@code
 * Path.of("src", "main", "java")}) — the working directory is the module root under Gradle and
 * under every IDE runner this project has been used with. That is deliberately the whole mechanism:
 * an earlier version of these ratchets took the jar path from a {@code systemProperty} the {@code
 * test} task injected, which worked under Gradle and failed outright anywhere else. A test that
 * only runs inside one launcher is a test people stop running.
 *
 * <p>Public rather than package-private (the default for a helper here): its callers do not live in
 * this package. {@code ModApiSurfaceTest} and {@code ModBoundaryRulesTest} sit in {@code
 * com.rustorio.mod} because they ask {@code ModClassLoader}'s own predicate instead of restating its
 * allowlist, and moving them here would trade one source of truth for two.
 *
 * <p>Every lookup here FAILS rather than returning empty when the artifact is missing. An empty
 * result would quietly turn "nothing forbidden was found" into "nothing was looked at", which is
 * the one way a ratchet can go green while measuring nothing.
 */
public final class BuildOutputs {

    private static final Path LIBS = Path.of("build", "libs");
    private static final Path CLASSES = Path.of("build", "classes", "java");

    /** Gradle names a source set's output directory after the source set: {@code webminerMod} -> here. */
    private static final String MOD_SOURCE_SET_SUFFIX = "Mod";

    private BuildOutputs() {
    }

    /**
     * Compiled classes of every in-repo mod, one directory per mod. Found by scanning, never by
     * naming a mod — the same rule the build follows when it discovers {@code src/mods}, and the
     * reason adding a second mod needs no edit here.
     */
    public static List<Path> modClassesDirs() {
        List<Path> dirs = new ArrayList<>();
        for (Path candidate : listing(CLASSES, "*" + MOD_SOURCE_SET_SUFFIX)) {
            if (Files.isDirectory(candidate)) {
                dirs.add(candidate);
            }
        }
        if (dirs.isEmpty()) {
            throw new IllegalStateException("no compiled mod classes under " + CLASSES + " — expected "
                    + "one directory per source set in src/mods. Run `./gradlew testClasses` first");
        }
        return List.copyOf(dirs);
    }

    /**
     * The published {@code rustorio-api} jar. Matched by name pattern rather than by full file name
     * because the version is part of it and this must not need editing at every version bump.
     */
    public static Path apiJar() {
        List<Path> matches = listing(LIBS, "rustorio-api-*.jar");
        if (matches.size() != 1) {
            throw new IllegalStateException("expected exactly one build/libs/rustorio-api-*.jar, found "
                    + matches + ". Run `./gradlew apiJar` first (the `test` task already depends on "
                    + "it, so this normally only bites a hand-run from an IDE); more than one means "
                    + "a stale jar from an older version is still there, so run `./gradlew clean`");
        }
        return matches.getFirst();
    }

    /**
     * Binary names of every class in a jar, in the jar's own entry order. {@code package-info} is
     * included: it is a real entry a consumer receives, and a surface check that skipped it would
     * be describing a jar slightly different from the one that ships.
     */
    public static List<String> classNamesIn(Path jar) {
        List<String> names = new ArrayList<>();
        try (JarFile jarFile = new JarFile(jar.toFile())) {
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                String entryName = entries.nextElement().getName();
                if (entryName.endsWith(".class")) {
                    names.add(entryName.substring(0, entryName.length() - ".class".length())
                            .replace('/', '.'));
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + jar, e);
        }
        if (names.isEmpty()) {
            throw new IllegalStateException(jar + " contains no classes at all — a packaging pattern "
                    + "that matches nothing satisfies every 'ships nothing forbidden' check perfectly");
        }
        return List.copyOf(names);
    }

    private static List<Path> listing(Path directory, String glob) {
        if (!Files.isDirectory(directory)) {
            throw new IllegalStateException(directory + " does not exist — tests run from the module "
                    + "root, and this one needs the build outputs that `./gradlew build` produces "
                    + "there. Check the working directory if you are launching from an IDE");
        }
        List<Path> matches = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory, glob)) {
            for (Path path : stream) {
                matches.add(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list " + directory, e);
        }
        matches.sort(null); // filesystem order differs between machines; a ratchet's report must not
        return matches;
    }
}

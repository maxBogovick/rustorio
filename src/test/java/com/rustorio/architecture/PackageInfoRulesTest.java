package com.rustorio.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every package under {@code src/main/java} must carry a {@code package-info.java} annotated
 * {@code @NullMarked}. All thirteen packages satisfy this today; until this test existed, that was
 * true by habit rather than by check — nothing in the build looked, so the first package added
 * without one would have been found by a reader, or not at all.
 *
 * <p><b>Why it matters beyond tidiness.</b> {@code package-info.java} is where this codebase states
 * a package's allowed dependencies in prose, right next to the code they constrain; a package
 * without one starts life with no stated boundary for {@link PackageBoundaryRulesTest} to be the
 * machine-checked half of. The {@code @NullMarked} half is load-bearing in a different way: it is
 * the JSpecify declaration an IDE and any external analyzer read to know that unannotated types
 * here mean non-null. NullAway happens to be configured by package prefix in {@code build.gradle}
 * rather than by this annotation, so a missing one doesn't fail compilation — which is exactly why
 * it can go missing unnoticed.
 *
 * <p><b>Why source scanning and not ArchUnit.</b> ArchUnit reads compiled classes, and a package
 * that has no {@code package-info.java} produces no {@code package-info} class to notice the
 * absence of — the missing case is invisible by construction. The file system is the only place
 * where "this package has no such file" is observable.
 *
 * <p><b>Scope: {@code src/main/java} only.</b> {@code src/tools} and {@code src/editor} are dev
 * tooling with NullAway deliberately switched off ({@code build.gradle}), so requiring a null
 * contract from them would assert something the build doesn't enforce; test sources reach across
 * layers on purpose and are excluded for the same reason {@link PackageBoundaryRulesTest} excludes
 * them.
 */
class PackageInfoRulesTest {

    /** {@code src/main/java}, resolved from the module root Gradle already runs tests from. */
    private static final Path SRC_MAIN = Path.of("src", "main", "java");

    private static final String PACKAGE_INFO = "package-info.java";

    @Test
    void everyPackageInMainSourcesDeclaresItsNullContract() {
        List<String> missingFile = new ArrayList<>();
        List<String> missingAnnotation = new ArrayList<>();

        for (Path directory : packageDirectories()) {
            Path packageInfo = directory.resolve(PACKAGE_INFO);
            if (!Files.exists(packageInfo)) {
                missingFile.add(packageName(directory));
                continue;
            }
            // Stripped first: a commented-out or quoted "@NullMarked" is not a declaration, and
            // this test would otherwise accept one that the compiler never sees.
            String source = SourceCodeScanner.stripCommentsAndLiterals(read(packageInfo));
            if (!source.contains("@NullMarked")) {
                missingAnnotation.add(packageName(directory));
            }
        }

        assertTrue(missingFile.isEmpty(),
                "every package under src/main/java needs a package-info.java stating its allowed "
                        + "dependencies; add one to: " + missingFile);
        assertTrue(missingAnnotation.isEmpty(),
                "package-info.java is where a package declares @NullMarked (JSpecify); without it "
                        + "unannotated types carry no null contract for readers or analyzers: "
                        + missingAnnotation);
    }

    /**
     * Directories holding at least one {@code .java} file — a directory that only groups
     * subdirectories ({@code com}, {@code com/rustorio}) is not a package in the sense this rule
     * cares about and would fail it for no reason. Ordered ({@link LinkedHashSet} over a sorted
     * walk) so a failure message names the same packages in the same order on every machine.
     */
    private static Set<Path> packageDirectories() {
        Set<Path> directories = new LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(SRC_MAIN)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                directories.add(file.getParent());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to walk " + SRC_MAIN, e);
        }
        return directories;
    }

    private static String packageName(Path directory) {
        return SRC_MAIN.relativize(directory).toString().replace(java.io.File.separatorChar, '.');
    }

    private static String read(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read " + file, e);
        }
    }
}

package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.mod.EngineVersion;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * {@link EngineVersion#CURRENT} is a hand-written copy of the version in {@code build.gradle},
 * because the game is routinely run from a source set with no jar manifest to read it from. A copy
 * that nothing checks is a copy that goes stale, and a stale engine version silently mis-answers
 * every mod's {@code minEngineVersion}: the whole point of that field is to refuse a mod that needs
 * a newer engine, which it cannot do if it thinks the engine is a version it isn't.
 */
class EngineVersionTest {

    private static final Pattern GRADLE_VERSION = Pattern.compile("^version\\s*=\\s*'([^']+)'");

    @Test
    void theConstantMatchesTheVersionDeclaredInTheBuild() {
        assertEquals(versionFromBuildGradle(), EngineVersion.CURRENT,
                "версия движка в коде разошлась с build.gradle — почини ту, что неверна, "
                        + "и не подгоняй тест");
    }

    @Test
    void theConstantParsesAsASemanticVersion() {
        assertTrue(SemVer.parse(EngineVersion.CURRENT).major() >= 0,
                "версия движка обязана разбираться тем же SemVer, каким сравниваются моды");
    }

    private static String versionFromBuildGradle() {
        Path buildFile = Path.of("build.gradle");
        try {
            List<String> lines = Files.readAllLines(buildFile);
            for (String line : lines) {
                Matcher matcher = GRADLE_VERSION.matcher(line.trim());
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("не читается " + buildFile, e);
        }
        throw new AssertionError("в build.gradle не нашлась строка version = '...'");
    }
}

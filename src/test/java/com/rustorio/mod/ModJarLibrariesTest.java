package com.rustorio.mod;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mod's third-party libraries have to be INSIDE its jar, and this is the only place that can
 * tell. {@link ModClassLoader} delegates to the parent for {@code com.rustorio.api}, {@code
 * com.rustorio.domain} and the JDK; every other name is looked up in the mod's own jar and nowhere
 * else. A mod that compiles against a library the engine happens to ship is therefore perfectly
 * green at build time and broken the instant a player loads it.
 *
 * <p>That is not hypothetical — it is what this test was written for. {@code
 * com.webminer.JsonFieldExtractor} imports Jackson, every other test of it passed, and loading it
 * the way the game does threw {@code NoClassDefFoundError:
 * com/fasterxml/jackson/databind/ObjectMapper}. The other tests all reach the mod's classes through
 * {@code sourceSets.webminerMod.output.classesDirs} on the test classpath, where the parent
 * classloader supplies Jackson and the gap cannot show.
 *
 * <p>So this loads the REAL jar off disk through the REAL loader. Naming the classes explicitly is
 * the point: each one is a class whose static initialiser or field types reach a library, which is
 * exactly what fails, and a listing that walks the jar automatically would quietly pass on a jar
 * containing nothing at all.
 */
class ModJarLibrariesTest {

    private static final Path WEBMINER_JAR = Path.of("resources", "mods", "webminer", "webminer.jar");

    @Test
    void everyWebminerClassThatUsesALibraryLoadsFromTheJarTheGameActuallyReads() throws Exception {
        assertTrue(Files.isRegularFile(WEBMINER_JAR),
                "the mod jar must be built before this runs — `test` depends on `webminerModJar` for exactly this reason");

        ModClassLoader loader = new ModClassLoader(WEBMINER_JAR.toUri().toURL(), getClass().getClassLoader());

        // Class.forName with initialize=true: the failure is in a static initialiser (an
        // ObjectMapper field), so a load that stops short of running it would prove nothing.
        assertDoesNotThrow(() -> Class.forName("com.webminer.JsonFieldExtractor", true, loader),
                "Jackson must travel inside the mod jar — the engine having it on its own classpath is not enough");
        assertDoesNotThrow(() -> Class.forName("com.webminer.ResponsePreview", true, loader),
                "jsoup must travel inside the mod jar for the same reason");
    }
}

package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.rustorio.api.mod.RustorioMod;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the loader works against a REAL {@code .jar} file, compiled in-process by {@link
 * TestModJarBuilder} — not a fixture of pre-built {@code .class} files (E7-04's own critierion:
 * "this is what makes it a real engine, not just an open codebase").
 */
class ModJarLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsARealJarsRustorioModImplementationViaServiceLoader() {
        Path jar = tempDir.resolve("examplemod.jar");
        String source = """
                package com.testmod;
                public final class ExampleEntryPoint implements com.rustorio.api.mod.RustorioMod {
                    @Override public String toString() { return "example-entry-point-marker"; }
                }
                """;
        TestModJarBuilder.build(jar,
                Map.of("com.testmod.ExampleEntryPoint", source),
                Map.of(RustorioMod.class.getName(), "com.testmod.ExampleEntryPoint"));

        RustorioMod mod = ModJarLoader.loadEntryPoint(jar, "com.testmod.ExampleEntryPoint", getClass().getClassLoader());

        assertEquals("com.testmod.ExampleEntryPoint", mod.getClass().getName());
        assertEquals("example-entry-point-marker", mod.toString());
        assertInstanceOf(ModClassLoader.class, mod.getClass().getClassLoader(),
                "the entry point must be loaded by this mod's own isolating classloader, not the test's");
    }

    @Test
    void missingServiceRegistrationFailsWithAClearMessage() {
        Path jar = tempDir.resolve("noentry.jar");
        String source = """
                package com.testmod;
                public final class NotRegistered implements com.rustorio.api.mod.RustorioMod {
                }
                """;
        // No META-INF/services entry at all — ServiceLoader will find nothing.
        TestModJarBuilder.build(jar, Map.of("com.testmod.NotRegistered", source), Map.of());

        ModLoadException thrown = assertThrows(ModLoadException.class,
                () -> ModJarLoader.loadEntryPoint(jar, "com.testmod.NotRegistered", getClass().getClassLoader()));
        assertEquals(true, thrown.getMessage().contains(jar.toString()));
    }

    @Test
    void mismatchedDeclaredEntryPointIsRejected() {
        Path jar = tempDir.resolve("mismatch.jar");
        String source = """
                package com.testmod;
                public final class RealEntryPoint implements com.rustorio.api.mod.RustorioMod {
                }
                """;
        TestModJarBuilder.build(jar,
                Map.of("com.testmod.RealEntryPoint", source),
                Map.of(RustorioMod.class.getName(), "com.testmod.RealEntryPoint"));

        ModLoadException thrown = assertThrows(ModLoadException.class,
                () -> ModJarLoader.loadEntryPoint(jar, "com.testmod.WrongClaimedName", getClass().getClassLoader()));
        assertEquals(true, thrown.getMessage().contains("com.testmod.WrongClaimedName"));
        assertEquals(true, thrown.getMessage().contains("com.testmod.RealEntryPoint"));
    }

    @Test
    void twoModsWithAClassOfTheSameNameLoadTwoGenuinelyDifferentClasses() {
        String template = """
                package com.testmod;
                public final class Marker implements com.rustorio.api.mod.RustorioMod {
                    @Override public String toString() { return "%s"; }
                }
                """;
        Path jarA = tempDir.resolve("a.jar");
        Path jarB = tempDir.resolve("b.jar");
        TestModJarBuilder.build(jarA, Map.of("com.testmod.Marker", template.formatted("mod-a")),
                Map.of(RustorioMod.class.getName(), "com.testmod.Marker"));
        TestModJarBuilder.build(jarB, Map.of("com.testmod.Marker", template.formatted("mod-b")),
                Map.of(RustorioMod.class.getName(), "com.testmod.Marker"));

        RustorioMod modA = ModJarLoader.loadEntryPoint(jarA, "com.testmod.Marker", getClass().getClassLoader());
        RustorioMod modB = ModJarLoader.loadEntryPoint(jarB, "com.testmod.Marker", getClass().getClassLoader());

        assertNotEquals(modA.getClass(), modB.getClass(), "same-named classes from different mod jars must be genuinely different Class objects");
        assertEquals("mod-a", modA.toString());
        assertEquals("mod-b", modB.toString());
    }
}

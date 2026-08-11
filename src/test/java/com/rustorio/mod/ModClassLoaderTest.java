package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The classloader contract in isolation, independent of {@link ModJarLoader}/{@code ServiceLoader}
 * — a jar with nothing but an unrelated placeholder class is enough to test what {@link
 * ModClassLoader#loadClass} does and doesn't reach.
 */
class ModClassLoaderTest {

    private static final List<String> MOVED_CONTENT_MODELS = List.of(
            "FluidType",
            "ItemType",
            "ItemShape",
            "RecipeKind",
            "TechType",
            "OrePatch",
            "TerrainPatch",
            "AuthoredMap",
            "Recipe");

    private static final List<String> MOVED_VANILLA_HELPERS = List.of(
            "VanillaItems",
            "VanillaSprites",
            "VanillaTechs",
            "VanillaTechEffects",
            "VanillaFluids");

    @TempDir
    Path tempDir;

    private ModClassLoader newLoaderOverAnArbitraryJar() throws Exception {
        Path jar = tempDir.resolve("placeholder.jar");
        String source = "package com.testmod; public final class Placeholder {}";
        TestModJarBuilder.build(jar, Map.of("com.testmod.Placeholder", source), Map.of());
        URL jarUrl = jar.toUri().toURL();
        return new ModClassLoader(jarUrl, getClass().getClassLoader());
    }

    @Test
    void apiAndDomainPackagesDelegateToTheParent() throws Exception {
        ModClassLoader loader = newLoaderOverAnArbitraryJar();

        assertDoesNotThrow(() -> loader.loadClass("com.rustorio.api.mod.RustorioMod"));
        for (String simpleName : MOVED_CONTENT_MODELS) {
            assertDoesNotThrow(() -> loader.loadClass("com.rustorio.api.content.model." + simpleName));
        }
        for (String simpleName : MOVED_VANILLA_HELPERS) {
            assertDoesNotThrow(() -> loader.loadClass("com.rustorio.api.content.vanilla." + simpleName));
        }
        assertDoesNotThrow(() -> loader.loadClass("com.rustorio.domain.building.BeltSegment"));
        assertDoesNotThrow(() -> loader.loadClass("com.rustorio.domain.building.SimpleCrafter"));
        assertDoesNotThrow(() -> loader.loadClass("com.rustorio.domain.building.FluidPort"));
    }

    /**
     * After the physical move, old {@code com.rustorio.domain.<Name>} FQCNs simply do not exist on
     * the classpath. {@code ModClassLoader} still parent-delegates {@code domain.*}, so this is not
     * an allowlist deny — it is ClassNotFound from the parent once the types left that package.
     */
    @Test
    void movedContentModelFqcnNoLongerExistUnderDomain() throws Exception {
        ModClassLoader loader = newLoaderOverAnArbitraryJar();

        for (String simpleName : MOVED_CONTENT_MODELS) {
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("com.rustorio.domain." + simpleName),
                    "domain." + simpleName + " must CNFE after the physical move to content.model");
        }
        for (String simpleName : MOVED_VANILLA_HELPERS) {
            assertThrows(ClassNotFoundException.class,
                    () -> loader.loadClass("com.rustorio.domain." + simpleName),
                    "domain." + simpleName + " must CNFE after the physical move to content.vanilla");
        }
    }

    @Test
    void worldActionsNetworksAndVanillaConcretesAreNotReachable() throws Exception {
        ModClassLoader loader = newLoaderOverAnArbitraryJar();

        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.world.World"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.action.PlayerAction"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.building.FluidNetwork"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.building.PowerNetwork"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.building.Pipe"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.building.VanillaBuildings"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.building.BuildingFactory"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.domain.building.NetworkWiring"));
    }

    @Test
    void persistenceAndGraphicsAreNotReachableEvenThoughTheyAreOnTheParentClasspath() throws Exception {
        ModClassLoader loader = newLoaderOverAnArbitraryJar();

        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.persistence.JsonSaveRepository"));
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.graphics.render.Textures"));
    }

    @Test
    void theModLoaderPackageItselfIsNotReachable() throws Exception {
        ModClassLoader loader = newLoaderOverAnArbitraryJar();

        // A mod must not be able to reach into the loader's own implementation package —
        // com.rustorio.mod is neither an allowlisted prefix nor part of the mod's own jar.
        assertThrows(ClassNotFoundException.class, () -> loader.loadClass("com.rustorio.mod.ModLoadException"));
    }

    @Test
    void ownJarClassesAreFound() throws Exception {
        ModClassLoader loader = newLoaderOverAnArbitraryJar();

        assertDoesNotThrow(() -> loader.loadClass("com.testmod.Placeholder"));
    }
}

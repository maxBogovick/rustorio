package com.rustorio.api.building;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.architecture.BuildOutputs;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Phase-2 facades must ship in {@code rustorio-api} and extend the domain contracts — a mod that
 * {@code implements com.rustorio.api.building.Building} is still a {@code domain.building.Building}
 * for the engine. (The reverse is not true: existing domain implementations are not subtypes of
 * the facade, and do not need to be.)
 */
class ApiFacadeSurfaceTest {

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

    @Test
    void apiJarShipsBuildingFacadesAndContentCatalogs() {
        List<String> shipped = BuildOutputs.classNamesIn(BuildOutputs.apiJar());
        assertTrue(shipped.contains("com.rustorio.api.building.Building"),
                "facade Building must ship in rustorio-api");
        assertTrue(shipped.contains("com.rustorio.api.building.Codec"));
        assertTrue(!shipped.contains("com.rustorio.api.building.TickContext"),
                "TickContext must not be facaded — see api.building package-info");
        assertTrue(shipped.contains("com.rustorio.api.content.model.package-info"),
                "content.model catalog package must ship");
        for (String simpleName : MOVED_CONTENT_MODELS) {
            assertTrue(shipped.contains("com.rustorio.api.content.model." + simpleName),
                    simpleName + " must ship from api.content.model after the physical move");
            assertTrue(!shipped.contains("com.rustorio.domain." + simpleName),
                    "domain." + simpleName + " must be absent from rustorio-api after the move");
        }
        assertTrue(shipped.contains("com.rustorio.api.content.vanilla.package-info"),
                "content.vanilla catalog package must ship");
        for (String simpleName : MOVED_VANILLA_HELPERS) {
            assertTrue(shipped.contains("com.rustorio.api.content.vanilla." + simpleName),
                    simpleName + " must ship from api.content.vanilla after the physical move");
            assertTrue(!shipped.contains("com.rustorio.domain." + simpleName),
                    "domain." + simpleName + " must be absent from rustorio-api after the move");
        }
    }

    @Test
    void implementingTheFacadeSatisfiesTheDomainContract() {
        assertTrue(com.rustorio.domain.building.Building.class.isAssignableFrom(Building.class),
                "api.building.Building extends domain.building.Building — mod code implementing the "
                        + "facade remains usable by the engine");
        assertTrue(com.rustorio.domain.building.PlacementRule.class.isAssignableFrom(PlacementRule.class));
        // TickContext is intentionally NOT facaded — see api.building package-info.
    }
}

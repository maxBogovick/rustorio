package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.architecture.BuildOutputs;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * {@link ModBuildingApiAllowlist} must be fully present in {@code rustorio-api}, and nothing else
 * from {@code domain.building} may ship. Complements {@link ModApiSurfaceTest} (jar ⊆ delegated).
 */
class ModBuildingApiSurfaceTest {

    private static final String BUILDING_PREFIX = "com.rustorio.domain.building.";

    @Test
    void apiJarShipsEveryAllowlistedBuildingTypeAndNoOtherBuildingTypes() {
        Set<String> shippedBuilding = new TreeSet<>();
        for (String className : BuildOutputs.classNamesIn(BuildOutputs.apiJar())) {
            if (className.startsWith(BUILDING_PREFIX)) {
                shippedBuilding.add(className.contains("$")
                        ? className.substring(0, className.indexOf('$'))
                        : className);
            }
        }

        List<String> missing = new ArrayList<>();
        for (String allowed : ModBuildingApiAllowlist.outerTypes()) {
            if (!shippedBuilding.contains(allowed)) {
                missing.add(allowed);
            }
        }
        assertEquals(List.of(), missing,
                "allowlisted building API types missing from rustorio-api — update the apiJar includes");

        List<String> extras = new ArrayList<>();
        for (String shipped : shippedBuilding) {
            if (!ModBuildingApiAllowlist.isAllowed(shipped)) {
                extras.add(shipped);
            }
        }
        assertEquals(List.of(), extras,
                "rustorio-api ships domain.building kitchen types — drop them from apiJar or add to "
                        + "ModBuildingApiAllowlist on purpose");
        assertTrue(shippedBuilding.contains(BUILDING_PREFIX + "SimpleCrafter"));
        assertTrue(shippedBuilding.contains(BUILDING_PREFIX + "BeltSegment"));
        assertFalse(shippedBuilding.contains(BUILDING_PREFIX + "FluidNetwork"));
        assertFalse(shippedBuilding.contains(BUILDING_PREFIX + "VanillaBuildings"));
    }
}

package com.rustorio.mod;

import java.util.Set;

/**
 * Which {@code com.rustorio.domain.building} types a mod may name — the published half of that
 * package. Everything else (networks, vanilla concretes, {@code BuildingFactory},
 * {@code VanillaBuildings}, …) is engine kitchen: present on the game classpath, absent from
 * {@code rustorio-api}, and refused by {@link ModClassLoader}.
 *
 * <p>Single source of truth for the classloader. The {@code apiJar} task must ship exactly these
 * types (plus their nested classes); {@code ModBuildingApiSurfaceTest} holds the jar to this set.
 * Nested classes ({@code SimpleCrafter$State}) are allowed when their outer type is.
 */
final class ModBuildingApiAllowlist {

    private static final String PKG = "com.rustorio.domain.building.";

    /**
     * Outer binary names only. Keep sorted; add a type here only when a supported mod path must
     * name it (L2 DSL, L3 Building/TransportNode, TickContext ports).
     */
    private static final Set<String> OUTER = Set.of(
            PKG + "BehaviorFactory",
            PKG + "BeltSegment",
            PKG + "BeltState",
            PKG + "Building",
            PKG + "BuildingCost",
            PKG + "BuildingImage",
            PKG + "BuildingPrototype",
            PKG + "BuildingServices",
            PKG + "Codec",
            PKG + "EditableBuilding",
            PKG + "FieldSpec",
            PKG + "FieldType",
            PKG + "FluidPort",
            PKG + "InspectableBuilding",
            PKG + "PlacementRule",
            PKG + "PowerSpec",
            PKG + "RestoreFactory",
            PKG + "ServiceKey",
            PKG + "SettlesEachTick",
            PKG + "SimpleCrafter",
            PKG + "SimpleCrafterSpec",
            PKG + "StateMigration",
            PKG + "TickContext",
            PKG + "TraitKey",
            PKG + "Traits",
            PKG + "TransportNode",
            PKG + "VanillaCategories",
            PKG + "VanillaPlacementRules",
            PKG + "VanillaTraits",
            PKG + "ViewableBuilding",
            PKG + "VisibilityReferenceCheck",
            PKG + "VisibilityRule");

    private ModBuildingApiAllowlist() {
    }

    static boolean isAllowed(String binaryName) {
        if (OUTER.contains(binaryName)) {
            return true;
        }
        int nested = binaryName.indexOf('$');
        if (nested < 0) {
            return false;
        }
        return OUTER.contains(binaryName.substring(0, nested));
    }

    /** Outer types only — for the jar inventory test. */
    static Set<String> outerTypes() {
        return OUTER;
    }
}

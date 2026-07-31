package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression test for C1 (CODE_REVIEW_2026-07-28.md): the build-ghost's read-only geometry check
 * ({@link OverlayRenderer#canPlaceHere}) must agree EXACTLY with what {@link World#place} itself
 * does — for every vanilla {@link BuildingPrototype}, not just 1x1 ones. Before C1 was fixed, the
 * ghost only ever checked the ANCHOR cell, so a multi-cell {@code ASSEMBLER} could show a green
 * ("buildable") outline over a cell whose footprint overlapped an occupied neighbor; the click
 * that followed then silently did nothing, with no visible cause.
 *
 * <p>{@link OverlayRenderer#canPlaceHere} touches only {@code World}/{@code BuildingPrototype} —
 * no libGDX involved — so this runs headless, same as any other domain test (see A1,
 * CODE_REVIEW_2026-07-28.md, for why the rest of {@code com.graphics.render}/{@code
 * com.graphics.input} can't).
 */
class OverlayRendererGhostTest {

    private static BuildingPrototype prototypeFor(BuildingType type) {
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type));
    }

    /** The exact scenario from the code review's own proof: a chest at (3,2) leaves (2,2) itself free, but ASSEMBLER's 2x2 footprint anchored there still overlaps it. */
    @Test
    void ghostRefusesAssemblerWhoseFootprintOverlapsAnOccupiedNeighbor() {
        World ghostWorld = new World(10, 10);
        ghostWorld.placeChest(3, 2);
        boolean ghostSaysValid = OverlayRenderer.canPlaceHere(ghostWorld, prototypeFor(BuildingType.ASSEMBLER), 2, 2);

        World placeWorld = new World(10, 10);
        placeWorld.placeChest(3, 2);
        boolean actuallyPlaced = placeWorld.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        assertFalse(actuallyPlaced, "the footprint overlaps the chest at (3,2) — World.place must refuse it");
        assertEquals(actuallyPlaced, ghostSaysValid, "the ghost must agree with what World.place actually decides");
    }

    /**
     * The general property, for every building kind: on a map with a couple of fixed obstacles,
     * the ghost and the real placement must agree at every cell of a scanned grid — including every
     * cell a multi-cell footprint would need to check, not just the anchor.
     */
    @Test
    void ghostAgreesWithWorldPlaceForEveryBuildingTypeAcrossAScatteredGrid() {
        for (BuildingType type : BuildingType.values()) {
            BuildingPrototype prototype = prototypeFor(type);
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    World ghostWorld = worldWithObstacles();
                    boolean ghost = OverlayRenderer.canPlaceHere(ghostWorld, prototype, x, y);

                    World placeWorld = worldWithObstacles();
                    boolean actual = placeWorld.place(type, x, y, Direction.RIGHT);

                    assertEquals(actual, ghost, type + " at (" + x + "," + y + ")");
                }
            }
        }
    }

    /** A fixed pair of 1x1 obstacles — identical every call, so the ghost check and the real placement each see the same map. */
    private static World worldWithObstacles() {
        World world = new World(10, 10);
        world.placeChest(3, 2);
        world.placeChest(6, 6);
        return world;
    }

    /**
     * (Phase 8) {@link OverlayRenderer#canPlaceHere}/{@link OverlayRenderer#reasonInvalid} must
     * agree with {@link World#place} for a MODDED prototype too — no {@link BuildingType} at all —
     * not just the closed vanilla set the rest of this class exercises.
     */
    @Test
    void ghostAgreesWithWorldPlaceForAModdedPrototypeWithNoCorrespondingBuildingType() {
        ContentId steelPressId = ContentId.of("examplemod:steel_press");
        BuildingPrototype steelPress = new BuildingPrototype(
                steelPressId, "Steel Press",
                new BuildingCost(VanillaItems.IRON_PLATE, 20),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).texture(),
                10, 2, true,
                (self, direction, factory) -> new Furnace(BuildingType.PRESS, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    throw new UnsupportedOperationException("not exercised by this test");
                },
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.PRESS)).codec());
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        prototypes.register(steelPressId, steelPress);
        prototypes.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(), VanillaItems.frozen(), prototypes);

        World ghostWorld = new World(10, 10, factory);
        ghostWorld.placeChest(1, 0);
        boolean ghostSaysValid = OverlayRenderer.canPlaceHere(ghostWorld, steelPress, 1, 0);

        World placeWorld = new World(10, 10, factory);
        placeWorld.placeChest(1, 0);
        boolean actuallyPlaced = placeWorld.place(steelPressId, 1, 0, Direction.RIGHT);

        assertFalse(actuallyPlaced, "the cell is occupied by the chest");
        assertEquals(actuallyPlaced, ghostSaysValid);

        // An affordable, unobstructed cell must agree too, and must succeed both ways.
        assertTrue(OverlayRenderer.canPlaceHere(ghostWorld, steelPress, 2, 0));
        assertTrue(placeWorld.place(steelPressId, 2, 0, Direction.RIGHT));
    }
}

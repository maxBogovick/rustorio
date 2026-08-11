package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.AuthoredMap;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Cell;
import com.rustorio.domain.Direction;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaFluids;
import com.rustorio.domain.building.BuildingFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The render-only network queries the overlay is built on: every tile of one network reports the
 * same identity, two independent networks report different ones, and that identity is a coordinate
 * ({@link Cell#compareTo} order), never an object hash — because the overlay has to paint the same
 * network the same colour on every frame and across a reload, which a hash cannot promise.
 *
 * <p>On an authored empty map so every cell is passable: the standard generated map hides its water
 * and rock hundreds of cells out, and a placement test should not depend on where.
 */
class WorldNetworkQueryTest {

    private static World emptyWorld() {
        AuthoredMap map = new AuthoredMap(ContentId.of("test:flat"), List.of(), List.of());
        BuildingFactory factory = new BuildingFactory(AuthoredOreLayout.from(map), RecipeBook.standard());
        return new World(20, 20, factory);
    }

    @Test
    void everyTileOfAPipeRunSharesOneAnchorAndSeparateRunsDoNot() {
        World world = emptyWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PIPE, 6, 5);
        world.place(BuildingType.PIPE, 7, 5);
        world.place(BuildingType.PIPE, 5, 8); // a run of its own, not touching the row

        assertEquals(new Cell(5, 5), world.fluidNetworkAt(7, 5).orElseThrow(),
                "the anchor is the smallest cell of the network, whichever tile you ask from");
        assertEquals(world.fluidNetworkAt(5, 5), world.fluidNetworkAt(7, 5),
                "one network, one identity, on every tile of it");
        assertNotEquals(world.fluidNetworkAt(5, 5).orElseThrow(), world.fluidNetworkAt(5, 8).orElseThrow(),
                "two networks that do not touch are two identities");
        assertTrue(world.fluidNetworkAt(9, 9).isEmpty(), "bare ground belongs to no fluid network");
    }

    @Test
    void aPoleGridCoversItsRadiusAndDistinctGridsGetDistinctAnchors() {
        World world = emptyWorld();
        world.place(BuildingType.POLE, 5, 5); // radius 5: covers x,y within 5

        assertEquals(new Cell(5, 5), world.powerNetworkAt(5, 5).orElseThrow(),
                "a lone pole's grid is anchored at the pole itself");
        assertEquals(world.powerNetworkAt(5, 5), world.powerNetworkAt(8, 5),
                "a covered cell three tiles away is on the same grid");
        assertTrue(world.powerNetworkAt(15, 15).isEmpty(), "a cell no pole reaches is on no grid");

        world.place(BuildingType.POLE, 5, 16); // 11 tiles away — past radius, so its own grid
        assertNotEquals(world.powerNetworkAt(5, 5).orElseThrow(), world.powerNetworkAt(5, 16).orElseThrow(),
                "two grids that cannot see each other are two identities");
    }

    @Test
    void aPipeJoinsOnlyTheSidesThatContinueItsNetwork() {
        World world = emptyWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PIPE, 6, 5); // one tile to the right

        int left = world.fluidJoints(5, 5);
        assertTrue(World.hasJoint(left, Direction.RIGHT),
                "the two pipes are one network — the joint between them is drawn");
        assertFalse(World.hasJoint(left, Direction.LEFT), "nothing sits to the left, so no joint there");
        assertFalse(World.hasJoint(left, Direction.UP), "nor above");
        assertFalse(World.hasJoint(world.fluidJoints(6, 5), Direction.RIGHT),
                "the run ends here — its far side joins nothing");
        assertEquals(0, world.fluidJoints(9, 9), "bare ground is not a fluid tile and joins nothing on any side");
    }

    /**
     * The border case the joint drawing exists to show: two networks that touch must NOT be drawn
     * joined, or a player would read a water pipe and the steam pipe beside it as one run and never
     * understand why nothing flows. Membership is compared by network identity, so abutting is not
     * the same as connected.
     */
    @Test
    void abuttingPipesOfDifferentFluidsAreNotJoined() {
        World world = emptyWorld();
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PIPE, 7, 5);
        world.fluidPort(4, 5, Direction.RIGHT).orElseThrow().insert(VanillaFluids.WATER, 50);
        world.fluidPort(6, 5, Direction.RIGHT).orElseThrow().insert(VanillaFluids.STEAM, 50);
        world.place(BuildingType.PIPE, 6, 5); // closes the gap without merging the two

        assertTrue(World.hasJoint(world.fluidJoints(5, 5), Direction.RIGHT)
                        != World.hasJoint(world.fluidJoints(7, 5), Direction.LEFT),
                "the middle tile belongs to exactly one side, so exactly one of the two joints is drawn");
    }
}

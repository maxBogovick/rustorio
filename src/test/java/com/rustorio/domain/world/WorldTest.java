package com.rustorio.domain.world;

import com.rustorio.domain.BuildingType;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link World#forEachBuildingIn}: found untested during a post-Phase-4 architecture review (only
 * ever exercised indirectly, through rendering code this project can't unit-test headlessly). Its
 * {@code subMap}-plus-{@code y}-filter approach (see its javadoc, P4-04 BUG_FIX_PROGRESS.md) has a
 * sharp edge worth pinning down directly: {@code Coord} orders by {@code x} first, so {@code
 * subMap} alone doesn't constrain {@code y} for any {@code x} strictly between the bounds.
 */
class WorldTest {

    private record Coord(int x, int y) {
    }

    private static List<Coord> collect(World world, int minX, int minY, int maxX, int maxY) {
        List<Coord> visited = new ArrayList<>();
        world.forEachBuildingIn(minX, minY, maxX, maxY, (x, y, building) -> visited.add(new Coord(x, y)));
        return visited;
    }

    @Test
    void onlyVisitsBuildingsInsideTheRectangle() {
        World world = new World(20, 20);
        world.placeChest(5, 5);
        world.placeChest(15, 15); // well outside the queried rectangle below

        List<Coord> visited = collect(world, 0, 0, 10, 10);

        assertEquals(List.of(new Coord(5, 5)), visited);
    }

    /**
     * The case {@code subMap} alone can't handle: {@code x} strictly between the bounds, but
     * {@code y} far outside them. Under x-then-y ordering, {@code Coord(7, 19)} still falls
     * between {@code Coord(0, 0)} and {@code Coord(10, 10)} — only the explicit {@code y} check in
     * {@link World#forEachBuildingIn} keeps it out.
     */
    @Test
    void excludesABuildingWhoseXIsInRangeButWhoseYIsFarOutside() {
        World world = new World(20, 20);
        world.placeChest(7, 19); // x=7 is within [0,10]; y=19 is nowhere near [0,10]

        List<Coord> visited = collect(world, 0, 0, 10, 10);

        assertTrue(visited.isEmpty(), "a building must not be visited just because its X happens to be in range");
    }

    @Test
    void boundaryCellsAreIncludedOnAllFourSides() {
        World world = new World(20, 20);
        world.placeChest(2, 2); // minX, minY corner
        world.placeChest(8, 2); // maxX, minY corner
        world.placeChest(2, 8); // minX, maxY corner
        world.placeChest(8, 8); // maxX, maxY corner
        world.placeChest(1, 5); // just outside minX
        world.placeChest(9, 5); // just outside maxX

        List<Coord> visited = collect(world, 2, 2, 8, 8);

        assertEquals(4, visited.size());
        assertTrue(visited.containsAll(List.of(
                new Coord(2, 2), new Coord(8, 2), new Coord(2, 8), new Coord(8, 8))));
    }

    @Test
    void emptyRectangleVisitsNothingAndDoesNotThrow() {
        World world = new World(10, 10);
        world.placeChest(5, 5);

        // minX > maxX — e.g. the camera entirely off the map. Must not throw (TreeMap.subMap
        // would, on an inverted range) and must visit nothing.
        List<Coord> visited = collect(world, 8, 0, 2, 9);

        assertTrue(visited.isEmpty());
    }

    @Test
    void visitorReceivesTheBuildingsOwnType() {
        World world = new World(10, 10);
        world.placeMiner(6, 5); // standard map's first iron patch

        List<BuildingType> types = new ArrayList<>();
        world.forEachBuildingIn(0, 0, 9, 9, (x, y, building) -> types.add(building.type()));

        assertEquals(List.of(BuildingType.MINER), types);
    }
}

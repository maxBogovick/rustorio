package com.rustorio.domain.world;

import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    /**
     * (D-06, DEV_TASKS.md) {@link World#currentTick()} is the world's own clock: it only moves
     * when {@link World#tick()} is called, never from wall time — calling it many times back to
     * back with no delay must still land on exactly the call count, which is also the whole reason
     * changing the player's speed multiplier (1x/2x/4x) or pausing can't distort it: neither of
     * those changes how many times {@code tick()} itself gets called for a given amount of
     * simulated progress, only how often a real-time frame renders in between.
     */
    @Test
    void currentTickIncrementsExactlyOncePerTickCall() {
        World world = new World(4, 4);
        assertEquals(0, world.currentTick(), "no tick has run yet");

        world.tick();
        assertEquals(1, world.currentTick());

        for (int i = 0; i < 9; i++) {
            world.tick();
        }
        assertEquals(10, world.currentTick());
    }

    /** (D-06, DEV_TASKS.md) A {@link ProductionListener} must see the tick {@code notifyProduced} was called on, not tick 0 or the final tick. */
    @Test
    void notifyProducedTagsTheEventWithTheTickItHappenedOn() {
        World world = new World(4, 4);
        List<Long> seenTicks = new ArrayList<>();
        world.addProductionListener((tick, item) -> seenTicks.add(tick));

        world.tick();
        world.tick();
        world.notifyProduced(Item.IRON_ORE); // still tick 2 — no further tick() call yet
        world.tick();
        world.notifyProduced(Item.IRON_PLATE); // now tick 3

        assertEquals(List.of(2L, 3L), seenTicks);
    }

    /**
     * {@link World#tryManualMine}: a live bug report, not a DEV_TASKS.md card — a factory fully
     * backed up with zero spendable resources had no way forward at all. Reaching directly into an
     * empty ore cell by hand is the escape hatch.
     */
    @Test
    void manualMineCreditsInventoryFromAnEmptyOreCell() {
        World world = new World(10, 10);

        Optional<Item> mined = world.tryManualMine(6, 5); // standard map's first iron patch, see MinerTest

        assertEquals(Optional.of(Item.IRON_ORE), mined);
        assertEquals(1, world.inventory().amount(Item.IRON_ORE));
    }

    @Test
    void manualMineFailsOnAnOccupiedCell() {
        World world = new World(10, 10);
        world.placeChest(6, 5);

        assertTrue(world.tryManualMine(6, 5).isEmpty(), "a real Miner would go here, not a bare hand");
    }

    @Test
    void manualMineFailsWhereThereIsNoOre() {
        World world = new World(10, 10);

        // (0,0) is far outside every ore patch's radius in the standard map — see MinerTest.
        assertTrue(world.tryManualMine(0, 0).isEmpty());
    }

    /** Without this, a player could spam-click one cell for unlimited free ore — a miner would never move that fast. */
    @Test
    void manualMineHasAPerCellCooldown() {
        World world = new World(10, 10);

        assertTrue(world.tryManualMine(6, 5).isPresent());
        assertTrue(world.tryManualMine(6, 5).isEmpty(), "no time has passed — the cell must still be on cooldown");

        for (int i = 0; i < 90; i++) {
            world.tick();
        }
        assertTrue(world.tryManualMine(6, 5).isPresent(), "cooldown must have elapsed by now");
    }

    @Test
    void manualMineCooldownIsPerCellNotGlobal() {
        World world = new World(20, 20); // tall enough to also reach the second iron patch below

        assertTrue(world.tryManualMine(6, 5).isPresent());
        // (9, 14) is the standard map's second iron patch — a completely different cell.
        assertTrue(world.tryManualMine(9, 14).isPresent(),
                "a different cell's cooldown must not be blocked by the first one");
    }

    /**
     * (Code review finding, CODE_REVIEW_2026-07-28.md) {@link World#clear} didn't used to wipe the
     * manual-mine cooldown map. A LOAD always follows {@code clear()} with {@code
     * restoreTickCount}, which can move the clock BACKWARD (loading an earlier save after playing
     * on) — leftover stale cooldown deadlines from the OLD, higher tick count would then outlive
     * the newly-lowered clock by however far it just moved back, refusing hand-mining on those
     * cells far longer than the "a few ticks early" the cooldown field's own javadoc promises.
     */
    @Test
    void clearDropsManualMineCooldownsSoALoadWithAnEarlierTickCountDoesNotInheritThem() {
        World world = new World(10, 10);
        assertTrue(world.tryManualMine(6, 5).isPresent()); // sets a cooldown deadline of 90 against tickCount 0
        for (int i = 0; i < 1000; i++) {
            world.tick(); // tickCount now far past the cooldown deadline set above
        }

        world.clear();
        world.restoreTickCount(0); // simulates loading an earlier save, same as JsonSaveRepository.load does

        assertTrue(world.tryManualMine(6, 5).isPresent(),
                "a stale cooldown from a previous, higher tick count must not survive clear()");
    }

    /**
     * {@link World#trySpendItems}: the multi-item form of {@link World#trySpendBuildingCost}'s own
     * atomicity — a live bug report's underlying primitive (RemoveAction/GrabChestAction's undo).
     */
    @Test
    void trySpendItemsDeductsEveryRequestedItemWhenAllAreAvailable() {
        World world = new World(4, 4);
        world.creditItem(Item.GEAR, 3);
        world.creditItem(Item.COAL, 5);

        assertTrue(world.trySpendItems(Map.of(Item.GEAR, 3, Item.COAL, 5)));

        assertEquals(0, world.inventory().amount(Item.GEAR));
        assertEquals(0, world.inventory().amount(Item.COAL));
    }

    @Test
    void trySpendItemsIsAllOrNothingWhenOneKindIsShort() {
        World world = new World(4, 4);
        world.creditItem(Item.GEAR, 3);
        world.creditItem(Item.COAL, 2); // not enough — needs 5 below

        assertFalse(world.trySpendItems(Map.of(Item.GEAR, 3, Item.COAL, 5)));

        assertEquals(3, world.inventory().amount(Item.GEAR),
                "GEAR must be untouched — a partial spend would leave the two pools inconsistent");
        assertEquals(2, world.inventory().amount(Item.COAL));
    }

    /**
     * A negative quantity must be refused outright, not treated as a spend (N4,
     * NEW_BUGS_PROGRESS.md): {@code have < -100} is trivially false, so the availability check
     * "passed" and the deduction {@code have - (-100)} then CREDITED 100 items. The amounts here
     * come from a chest's contents, i.e. straight out of a save file — external data.
     */
    @Test
    void trySpendItemsRefusesNegativeAmountsInsteadOfCreditingThem() {
        World world = new World(4, 4);
        world.creditItem(Item.GEAR, 3);

        assertFalse(world.trySpendItems(Map.of(Item.GEAR, -100)), "a negative spend is not a spend");
        assertEquals(3, world.inventory().amount(Item.GEAR), "must not have been credited 100 free GEAR");

        assertFalse(world.trySpendItems(Map.of(Item.GEAR, 3, Item.COAL, -5)),
                "one negative entry poisons the whole atomic batch");
        assertEquals(3, world.inventory().amount(Item.GEAR), "and nothing at all is deducted");
        assertEquals(0, world.inventory().amount(Item.COAL));
    }

    /**
     * (X-03, DEV_TASKS.md) {@code ASSEMBLER} is the first 2x2 building — see {@code
     * Building#footprintWidth}. These pin down the multi-cell addressing {@code World} grew to
     * support it: every cell of the footprint must behave as part of the SAME building, not four
     * independent ones.
     */
    @Test
    void placingAMultiCellBuildingOccupiesEveryCellOfItsFootprint() {
        World world = new World(10, 10);

        assertTrue(world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT));

        assertFalse(world.isFree(2, 2), "anchor cell");
        assertFalse(world.isFree(3, 2), "right of anchor");
        assertFalse(world.isFree(2, 3), "below anchor");
        assertFalse(world.isFree(3, 3), "diagonal from anchor");
    }

    @Test
    void placingAMultiCellBuildingFailsIfAnyFootprintCellIsAlreadyOccupied() {
        World world = new World(10, 10);
        world.placeChest(3, 3); // sits in what would be the assembler's bottom-right cell

        assertFalse(world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT));
        assertTrue(world.isFree(2, 2), "a failed multi-cell placement must not partially occupy any cell");
        assertTrue(world.peek(3, 3).isPresent(), "the pre-existing chest must be untouched");
    }

    @Test
    void peekingAnyCellOfAMultiCellBuildingReturnsTheSameInstance() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        var atAnchor = world.peek(2, 2).orElseThrow();
        var atFarCorner = world.peek(3, 3).orElseThrow();

        assertTrue(atAnchor == atFarCorner, "every occupied cell must resolve to the SAME building object");
    }

    @Test
    void removingAMultiCellBuildingFromAnyOfItsCellsFreesTheWholeFootprint() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        assertTrue(world.removeBuilding(3, 3).isPresent(), "demolishing via a NON-anchor cell must still work");

        assertTrue(world.isFree(2, 2));
        assertTrue(world.isFree(3, 2));
        assertTrue(world.isFree(2, 3));
        assertTrue(world.isFree(3, 3));
    }

    @Test
    void aMultiCellBuildingIsVisitedExactlyOnceAtItsAnchor() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        List<Coord> visited = collect(world, 0, 0, 9, 9);

        assertEquals(List.of(new Coord(2, 2)), visited, "one entry, at the anchor — not four");
    }

    @Test
    void originOfAnyFootprintCellResolvesToTheAnchor() {
        World world = new World(10, 10);
        world.place(BuildingType.ASSEMBLER, 2, 2, Direction.RIGHT);

        assertEquals(Optional.of(new World.Coord(2, 2)), world.originOf(3, 3));
        assertEquals(Optional.empty(), world.originOf(0, 0), "a free cell has no origin");
    }

    /**
     * {@link World#statusCounts}: kept incrementally (S1, CODE_REVIEW_2026-07-28.md), read directly
     * by {@code HudRenderer.alerts()}. (Code review finding, follow-up round) The accessor returns
     * a live unmodifiable view now, not a defensive copy — this pins down that the swap didn't
     * change what callers actually observe: still accurate, still can't be mutated out from under
     * {@code World}.
     */
    @Test
    void statusCountsReflectsPlacementAndIsUnmodifiable() {
        World world = new World(10, 10);
        // Off any ore patch — restoreBuilding bypasses PlacementRule, same trick DevScene uses.
        world.restoreBuilding(0, 0, world.buildingFactory().create(BuildingType.MINER, Direction.RIGHT));

        for (int i = 0; i < 3; i++) { // MINE_TIME ticks to reach the actual extraction attempt — see MinerTest
            world.tick();
        }

        Map<BuildingStatus, Integer> counts = world.statusCounts();
        assertEquals(1, counts.getOrDefault(BuildingStatus.NO_ORE, 0));
        assertThrows(UnsupportedOperationException.class, () -> counts.put(BuildingStatus.WORKING, 99));
    }
}

package com.rustorio.domain.world;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (S-01, DEV_TASKS.md) Whole-game regression net for the domain rewrites in Phase 2 (D-01..D-07):
 * builds a small factory exercising every sealed {@code Building} subtype, runs it a fixed number
 * of deterministic ticks from a fixed seed, and hashes the resulting world state. Per-building unit
 * tests only ever exercise one building in isolation; this catches an unintended shift in tick
 * order, delivery rules, or balance across the whole simulation as a one-line hash mismatch,
 * instead of it staying invisible until a player notices.
 */
class WorldReplayTest {

    private static final long SEED = 20260726L;
    private static final int WIDTH = 60;
    private static final int HEIGHT = 16;
    private static final int TICKS = 4000;

    /**
     * Recorded baseline: the actual digest {@link #canonicalState} produced for this exact scene,
     * captured once and pasted here. A mismatch means the simulation's output changed — before
     * touching this constant, read the diff and confirm the new behavior is the one you intended
     * (e.g. a deliberate Phase 2 rule change); pasting in whatever the test prints and moving on
     * defeats the entire point of this test.
     */
    // Updated for D-01 (DEV_TASKS.md): BuildingMemento.MinerState gained a `direction` component,
    // which changes every miner's canonical memento text in this scene — not a behavior change in
    // THIS particular layout (both miners already had a belt directly ahead and nothing else beside
    // them, so directed delivery and the old broadcast delivery agree here); confirmed by diffing
    // before updating, per this constant's own javadoc above.
    //
    // Updated again for P-01 (DEV_TASKS.md): BuildingMemento.LabState's `buffer` changed from a
    // plain int to a List<ItemType>, changing this scene's Lab's canonical memento text regardless of
    // points math. The points themselves are unaffected here: this scene's Lab only ever receives
    // GEAR (see buildLine2), and GEAR is P-01's baseline unit (depth 13 / 13 = 1 point) — exactly
    // what it already earned before this task. Confirmed by diffing before updating.
    //
    // Updated again for P-02 (DEV_TASKS.md): Tech unlocking is no longer automatic — Research used
    // to unlock every tech whose cost was already covered the instant enough points accumulated,
    // and this scene runs 4000 ticks, plenty to have crossed FAST_MINING's (and maybe more techs')
    // cost partway through, silently speeding up its miners for the remainder of the run. This
    // scene never calls the new explicit World.tryUnlockTech, so nothing unlocks anymore and every
    // producer runs at its un-teched rate for the whole 4000 ticks — a real, expected behavior
    // change (not just a memento format change), confirmed by diffing before updating.
    //
    // Updated again for X-02 (DEV_TASKS.md): RandomOreLayout now also rolls water/rock terrain
    // patches from the same seeded sequence as ore. ironOreSpots() had to start rejecting
    // candidates whose footprint isn't fully passable (see footprintIsPassable below) — the very
    // first candidate this scene used to pick may no longer be the one chosen, moving spot1/spot2
    // (and therefore every building's coordinate in the canonical state) even though nothing about
    // production timing or balance changed. Confirmed by diffing before updating.
    //
    // Updated again for D-02 (DEV_TASKS.md): BuildingMemento.ChestState changed from a plain int
    // to a Map<ItemType, Integer>, changing line 1's chest's canonical memento text regardless of what
    // it actually holds — that chest only ever receives GEAR from the press ahead of it (see
    // buildLine1), so the stored quantity itself is unaffected, only its serialized shape.
    // Confirmed by diffing before updating.
    //
    // Updated again for D-05 (DEV_TASKS.md): FURNACE kind now needs coal to produce at all — see
    // buildScene's fuel-topping note below — which both restarts idle furnaces that would
    // otherwise have jammed forever without it AND adds a fuelBuffer component to
    // BuildingMemento.FurnaceState, changing both behavior and memento format at once.
    // RandomOreLayout also now rolls four extra coal patches from the same seeded sequence as ore
    // (same mechanism as X-02's terrain patches above), which can shift which candidate spot
    // ironOreSpots() picks as spot1/spot2, moving every building's coordinate again on top of
    // that. Confirmed by diffing before updating.
    //
    // Updated again for F-03 (DEV_TASKS.md): BuildingMemento.FurnaceState gained a
    // selectedRecipeOutput component (persisting Furnace#selectedRecipe, the player's standing
    // recipe preference, across save/load), changing every furnace/press's canonical memento text
    // regardless of behavior. This scene never calls Furnace#cycleRecipe, so selectedRecipe stays
    // null for both FURNACE-kind buildings the whole run — the new field always serializes as the
    // same "null" text, a pure format change. Confirmed by diffing before updating.
    //
    // Updated again for X-01 (DEV_TASKS.md): Splitter stopped being a SortRule-based filter (that
    // strategy, and its one hardcoded ORE_FORWARD rule, are deleted) and became a real round-robin
    // balancer — it alternates its two outputs regardless of item identity, and WAITS rather than
    // rerouting if its currently-assigned side has nowhere to go (see Splitter's own class
    // javadoc). Line 2's splitter used to send every GEAR to its Lab (SortRule.ORE_FORWARD routed
    // all non-ore sideways); round-robin only sends every OTHER one there now, so a new chest was
    // added at the splitter's forward output (buildLine2) to catch the rest — without it, the
    // splitter would deadlock the instant it lands on a forward turn with nothing built there.
    // This is a real behavior change (the Lab now banks roughly half as many research points over
    // the same 4000 ticks), not just a format change (BuildingMemento.SplitterState also replaced
    // its rule id field with a nextIsForward boolean) — confirmed by diffing before updating.
    //
    // Updated again for N15 (NEW_BUGS_PROGRESS.md, owner decision): Lab.POINTS_PER_GEAR went from 1
    // to 10 (and every Tech cost ×10 with it), so this scene's research total went from 100 to
    // 1000. Diffed before updating, per this constant's javadoc, and the diff is exactly one line:
    // taking the new canonical state and substituting "points=1000" back to "points=100" reproduces
    // the previous hash 565665b54b0fff6dfa758c6cdb7575674ffb632ac454a92c3af42e37504c5784 byte for
    // byte. Everything else — production totals, every building's memento, both chests saturated at
    // 100 GEAR — is unchanged, which also confirms the other fixes made in the same pass left this
    // scene's behavior alone: the Splitter arrival mark (N2) costs a tick per hand-off but these
    // lines are fully backed up, so throughput here is set by the full chests, not by the splitter;
    // RandomOreLayout's radius clamp (N9) is a no-op for any map at least 9 cells across (this one
    // is 60x16); and the Miner (N14) / Chest (N6) status changes never reach a memento at all.
    // The lab consumed exactly 100 GEAR batches (305 produced, 205 still on the map), so 1000 points
    // is exactly 100 x POINTS_PER_GEAR — the new scale, applied once per batch, and nothing else.
    //
    // Updated again: Item (enum) became ItemType (a Registry-backed record) — canonicalState()
    // now prints each item's ContentId ("rustorio:iron_ore") instead of the old enum name
    // ("IRON_ORE"), and every ItemType-valued field inside a BuildingMemento (ChestState's map
    // keys, FurnaceState's recipeOutput/pendingOutput/selectedRecipeOutput) now serializes
    // through ItemType's own (much longer) default record toString() instead of a bare enum
    // name. This is a pure format change, not a behavior one — verified by reasoning through
    // which fields changed shape, not by a literal byte diff against the previous run (the old
    // code no longer exists in this tree to regenerate it from).
    //
    // Updated again (code review finding S3): ItemType got a custom toString() returning just
    // label() instead of the record's default every-field dump — every ItemType-valued field
    // inside a BuildingMemento now prints "Iron Plate" instead of
    // "ItemType[id=rustorio:iron_plate, label=Iron Plate, researchGrade=false, colorRgb=...,
    // shape=SQUARE]". Pure format change again, same reasoning as the entry above — nothing about
    // production totals, research, or building placement moved, only how one field type renders.
    private static final String EXPECTED_HASH = "1c1e1ac73d172de40ac4d58dd03cdfbc65edbaf5beb5f49359a801def89cdbbb";

    @Test
    void factoryStateAfterFixedTicksMatchesRecordedBaseline() {
        Scene scene = buildScene();

        for (int i = 0; i < TICKS; i++) {
            // Keep both FURNACE-kind buildings fueled every tick (D-05, DEV_TASKS.md) — accept()
            // caps at FUEL_MAX and returns false once full, so this is a harmless no-op most
            // ticks, not an unbounded top-up. A real coal-mining line was considered and rejected
            // for this fixture: finding a coal patch near each furnace, routing it in from a
            // second direction without disturbing the existing straight-line layout, would add
            // real scope to a test whose job is regression-detection across the OTHER seven
            // building kinds, not modeling fuel logistics realism.
            scene.furnace1().accept(scene.world(), VanillaItems.COAL);
            scene.furnace2().accept(scene.world(), VanillaItems.COAL);
            scene.world().tick();
        }

        String actualHash = sha256(canonicalState(scene.world()));
        assertEquals(EXPECTED_HASH, actualHash,
                "world state after " + TICKS + " deterministic ticks from seed " + SEED
                        + " no longer matches the recorded baseline — actual=" + actualHash);
    }

    /**
     * The world plus a direct reference to each line's FURNACE-kind building, so the fuel top-up
     * above doesn't need to re-derive coordinates. Package-private, not private: {@link
     * WorldStateHashDeterminismTest} reuses this fixture and {@link #buildScene()} instead of
     * duplicating ~130 lines of scenario construction for its own replay-hash tests.
     */
    record Scene(World world, Building furnace1, Building furnace2) {
    }

    /**
     * Two small factory lines sharing one map, together touching every sealed {@code Building}
     * subtype: {@code Miner}, {@code Belt}, {@code Furnace} (both the {@code FURNACE} and {@code
     * PRESS} kinds), {@code Chest}, {@code Splitter}, {@code UndergroundBelt}, {@code Lab}, and
     * {@code SpeedModule}. Ore cells are located by querying the same {@link OreLayout} instance
     * the world itself uses, rather than coordinates guessed from today's output — this fixture
     * keeps working even if {@link RandomOreLayout}'s internal patch-rolling changes, as long as it
     * still hands out iron ore somewhere with room around it.
     */
    static Scene buildScene() {
        OreLayout oreLayout = new RandomOreLayout(SEED, WIDTH, HEIGHT);
        BuildingFactory buildingFactory = new BuildingFactory(oreLayout, RecipeBook.standard());
        World world = new World(WIDTH, HEIGHT, buildingFactory);

        List<int[]> ironSpots = ironOreSpots(oreLayout, WIDTH, HEIGHT);
        int[] spot1 = ironSpots.get(0);
        int[] spot2 = ironSpots.stream()
                .filter(spot -> Math.abs(spot[1] - spot1[1]) >= 3)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "fixture needs two iron ore cells at least 3 rows apart for seed " + SEED
                                + " (" + WIDTH + "x" + HEIGHT + ") — adjust SEED/WIDTH/HEIGHT if the map changed"));

        buildLine1(world, spot1[0], spot1[1]);
        buildLine2(world, spot2[0], spot2[1]);
        Building furnace1 = world.peek(spot1[0] + 2, spot1[1]).orElseThrow();
        Building furnace2 = world.peek(spot2[0] + 2, spot2[1]).orElseThrow();
        return new Scene(world, furnace1, furnace2);
    }

    /** Miner -> Belt -> Furnace (speed-upgraded) -> underground tunnel -> Belt -> Press -> Belt -> Chest. */
    private static void buildLine1(World world, int x, int y) {
        assertTrue(world.placeMiner(x, y), "line 1 miner");
        assertTrue(world.placeBelt(x + 1, y, Direction.RIGHT), "line 1 belt");
        assertTrue(world.placeFurnace(x + 2, y, Direction.RIGHT), "line 1 furnace");
        assertTrue(new UpgradeSpeedAction(x + 2, y).apply(world), "line 1 speed module");
        assertTrue(world.placeUndergroundIn(x + 3, y, Direction.RIGHT), "line 1 tunnel in");
        assertTrue(world.placeUndergroundOut(x + 6, y, Direction.RIGHT), "line 1 tunnel out");
        assertTrue(world.placeBelt(x + 7, y, Direction.RIGHT), "line 1 belt after tunnel");
        assertTrue(world.placeBelt(x + 8, y, Direction.RIGHT), "line 1 belt after tunnel");
        assertTrue(world.placePress(x + 9, y, Direction.RIGHT), "line 1 press");
        assertTrue(world.placeBelt(x + 10, y, Direction.RIGHT), "line 1 belt to chest");
        assertTrue(world.placeChest(x + 11, y), "line 1 chest");
    }

    /** Miner -> Belt -> Furnace -> Belt -> Press -> Belt -> Splitter (round-robin) -> {Chest, Lab}. */
    private static void buildLine2(World world, int x, int y) {
        assertTrue(world.placeMiner(x, y), "line 2 miner");
        assertTrue(world.placeBelt(x + 1, y, Direction.RIGHT), "line 2 belt");
        assertTrue(world.placeFurnace(x + 2, y, Direction.RIGHT), "line 2 furnace");
        assertTrue(world.placeBelt(x + 3, y, Direction.RIGHT), "line 2 belt");
        assertTrue(world.placePress(x + 4, y, Direction.RIGHT), "line 2 press");
        assertTrue(world.placeBelt(x + 5, y, Direction.RIGHT), "line 2 belt");
        assertTrue(world.placeSplitter(x + 6, y, Direction.RIGHT), "line 2 splitter");
        // X-01, DEV_TASKS.md: Splitter is a real round-robin balancer now, not a SortRule-based
        // filter (deleted) — it alternates forward/secondary regardless of item identity, and
        // WAITS if its currently-assigned side has nowhere to go (see Splitter's own class
        // javadoc for why that's deliberate). Both sides need somewhere to deliver or it
        // deadlocks the instant it lands on the empty one: forward (RIGHT) to a new chest,
        // secondary (rotate(RIGHT)=DOWN) to the Lab, same cell as before.
        assertTrue(world.placeChest(x + 7, y), "line 2 chest (splitter forward output)");
        assertTrue(world.placeLab(x + 6, y + 1), "line 2 lab (splitter secondary output)");
    }

    /**
     * Every {@code (x, y)} sitting on iron ore, restricted to cells with enough clearance for
     * {@link #buildLine1}/{@link #buildLine2} AND whose whole footprint is buildable terrain
     * (X-02, DEV_TASKS.md: {@link RandomOreLayout} now rolls water/rock obstacles too, from the
     * same seeded sequence as ore — a candidate origin having iron under it no longer guarantees
     * the cells a few tiles to its right are passable).
     */
    private static List<int[]> ironOreSpots(OreLayout oreLayout, int width, int height) {
        List<int[]> spots = new ArrayList<>();
        int marginRight = 12; // line 1 spans 12 columns from its miner
        int marginBottom = 2; // line 2's Lab sits one row below its splitter
        for (int y = 1; y < height - marginBottom; y++) {
            for (int x = 1; x < width - marginRight; x++) {
                if (oreLayout.oreAt(x, y).equals(Optional.of(VanillaItems.IRON_ORE)) && footprintIsPassable(oreLayout, x, y)) {
                    spots.add(new int[] {x, y});
                }
            }
        }
        return spots;
    }

    /**
     * Every cell either {@link #buildLine1} or {@link #buildLine2} might place a building on, from
     * this candidate origin — the widest of the two (line 1, 12 columns) plus line 2's Lab row.
     * Slightly over-checks (line 1 deliberately leaves its tunnel gap, columns +4/+5, empty — those
     * don't strictly need to be passable), but a stricter filter that still finds candidates is
     * simpler than tracking each line's exact footprint separately.
     */
    private static boolean footprintIsPassable(OreLayout oreLayout, int x, int y) {
        for (int dx = 0; dx <= 11; dx++) {
            if (!oreLayout.isPassable(x + dx, y)) {
                return false;
            }
        }
        return oreLayout.isPassable(x + 6, y + 1);
    }

    /**
     * Everything that must survive as "the state of the game": production totals, research, and
     * every building's coordinate plus its {@code BuildingMemento}, assembled in a fixed order.
     *
     * <p>{@code ProductionStats.Snapshot}'s inner map comes from {@code Map.copyOf}, whose
     * iteration order the JDK deliberately randomizes per JVM run (see {@code
     * java.util.ImmutableCollections}'s salt) — reading it through {@code VanillaItems.frozen()}'s
     * own {@code rawId} order instead of iterating the map directly is what keeps this canonical
     * form, and therefore the hash, stable across separate test runs and not just within one.
     * {@code Research.Snapshot}'s {@code
     * unlocked} set is a real {@code EnumSet} (see its compact constructor), whose iteration order
     * is specified to follow enum declaration order, so it needs no such treatment.
     *
     * <p>Package-private, not private: this is the canonical-dump half of the replay-hash tool
     * {@link WorldStateHashDeterminismTest} needs — kept here rather than duplicated because it
     * must stay byte-for-byte identical to what {@link #EXPECTED_HASH} above was computed from.
     */
    static String canonicalState(World world) {
        StringBuilder state = new StringBuilder();

        ProductionStats.Snapshot stats = world.stats().snapshot();
        for (ItemType item : VanillaItems.frozen().iterate()) {
            state.append(item.id()).append('=').append(stats.totals().getOrDefault(item, 0L)).append(';');
        }
        state.append('\n');

        state.append(world.research().snapshot()).append('\n');

        world.forEachBuildingIn(0, 0, world.width() - 1, world.height() - 1,
                (x, y, building) -> state.append(x).append(',').append(y).append(':')
                        .append(building.memento()).append('\n'));

        return state.toString();
    }

    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available on every JVM", e);
        }
    }
}

package com.rustorio;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Terrain;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Headless benchmark: how many milliseconds one {@link World#tick()} takes on a scene of a given
 * size — no libGDX window. Answers a concrete question ("does this need a sleeping/waking entity
 * registry?") instead of guessing, per this project's rule: never optimize without a measurement.
 *
 * <p>The scene is {@code lanes} parallel belt lines of {@code laneLength} tiles each, feeding a
 * chest, kept constantly full from the tail end — a deliberately narrow scenario (belts only, the
 * most numerous building) that answers "is segment-based ticking itself expensive," not "how does
 * a realistic mixed factory perform."
 *
 * <p>Run: {@code ./gradlew benchmark} (defaults) or
 * {@code ./gradlew benchmark --args="lanes laneLength measuredTicks"}.
 */
public final class Benchmark {

    private static final int WARMUP_TICKS = 200;

    private Benchmark() {
    }

    public static void main(String[] args) {
        int lanes = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int laneLength = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        int measuredTicks = args.length > 2 ? Integer.parseInt(args[2]) : 1000;

        Scene scene = buildScene(lanes, laneLength);
        int totalBuildings = lanes * (laneLength + 1); // +1 — the chest at the end of each line

        for (int i = 0; i < WARMUP_TICKS; i++) {
            scene.tick(); // JIT warmup — excluded from the measurement
        }

        long start = System.nanoTime();
        for (int i = 0; i < measuredTicks; i++) {
            scene.tick();
        }
        long elapsedNanos = System.nanoTime() - start;
        double msPerTick = elapsedNanos / 1_000_000.0 / measuredTicks;

        System.out.printf("Buildings: %d (%d lanes of %d + a chest)%n", totalBuildings, lanes, laneLength);
        System.out.printf("Measured ticks: %d (plus %d warmup)%n", measuredTicks, WARMUP_TICKS);
        System.out.printf("Average step time: %.4f ms/tick%n", msPerTick);
    }

    /**
     * (X-02, DEV_TASKS.md) Uses {@link FlatOreLayout} — no ore, no terrain, any size — rather than
     * the default {@code BuildingFactory.standard()}: that default's {@code PatchOreLayout} is a
     * fixed 256x256 grid (X-04), and this scene's height ({@code lanes * 2}) already exceeds that
     * at the default 300 lanes; a cell off the end of a layout's own generated grid now reports
     * impassable (fail closed — see {@code PlacementRule}), where the old terrain-blind {@code
     * PlacementRule.ALWAYS} silently ignored the mismatch. {@link com.rustorio.domain.RandomOreLayout}
     * was considered instead and rejected: this scene tiles belts across roughly half of a
     * 100000+-cell map, and random water/rock patches would collide with some of them often enough
     * to make the benchmark fail unpredictably depending on lane count — a performance measurement
     * tool has no reason to depend on terrain generation at all.
     */
    private static Scene buildScene(int lanes, int laneLength) {
        int width = laneLength + 2;
        int height = lanes * 2;
        BuildingFactory buildingFactory = new BuildingFactory(new FlatOreLayout(), RecipeBook.standard());
        World world = new World(width, height, buildingFactory);
        List<Belt> tails = new ArrayList<>(lanes);
        for (int lane = 0; lane < lanes; lane++) {
            int y = lane * 2; // every other row — adjacent lanes' belts never touch, segments never merge
            for (int x = 0; x < laneLength; x++) {
                world.placeBelt(x, y, Direction.RIGHT);
            }
            world.placeChest(laneLength, y);

            for (int x = 0; x < laneLength; x++) {
                belt(world, x, y).accept(world, VanillaItems.IRON_ORE); // pack the line with cargo up front
            }
            tails.add(belt(world, 0, y));
        }
        return new Scene(world, tails);
    }

    private static Belt belt(World world, int x, int y) {
        return (Belt) world.peek(x, y).orElseThrow();
    }

    /** The world plus each lane's tail — every tick, top up a tail whose cargo has moved on. */
    private record Scene(World world, List<Belt> tails) {
        void tick() {
            for (Belt tail : tails) {
                tail.accept(world, VanillaItems.IRON_ORE); // tail busy -> rejected, harmlessly
            }
            world.tick();
        }
    }

    /**
     * No ore, no terrain, unbounded — this benchmark measures belt-tick cost, not ore or terrain
     * mechanics, so the simplest honest {@link OreLayout} is one that never has an opinion about
     * either (X-02, DEV_TASKS.md).
     */
    private static final class FlatOreLayout implements OreLayout {
        @Override
        public Optional<ItemType> oreAt(int x, int y) {
            return Optional.empty();
        }

        @Override
        public Optional<ItemType> extract(int x, int y) {
            return Optional.empty();
        }

        @Override
        public OreLayoutId id() {
            return new OreLayoutId("benchmark-flat", 0, 0, 0);
        }

        @Override
        public Terrain terrainAt(int x, int y) {
            return Terrain.GROUND;
        }

        @Override
        public Map<Integer, Integer> depletionSnapshot() {
            return Map.of(); // no ore, nothing ever extracted — see the class javadoc
        }

        @Override
        public void restoreDepletion(Map<Integer, Integer> snapshot) {
            // not exercised — this layout is never saved/loaded, only ticked
        }
    }
}

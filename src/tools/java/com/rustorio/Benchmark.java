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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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

    /**
     * The accepted ceiling for one tick: the measured baseline of 0.88 ms plus 15% headroom. It
     * lives here, in the only place that can compare it to a real measurement, rather than in the
     * prose of the rule files — a threshold quoted in five documents and checked by a human reading
     * the output is not a threshold, and this project had exactly that until now.
     *
     * <p>Machine-dependent by nature: the baseline was measured on the owner's machine, which is
     * why this is not a CI gate. On slower hardware, override with {@code BENCHMARK_THRESHOLD_MS}
     * rather than editing this constant, so the recorded number keeps meaning what it says.
     */
    private static final double DEFAULT_THRESHOLD_MS = 1.02;

    /**
     * The verdict is the median of this many measured batches, not a single one. Six consecutive
     * runs on an idle machine, with the code unchanged, spread from 0.8841 to 1.0136 ms/tick —
     * 14.7%, with two of them within 1% of the threshold. A single-shot gate on numbers like that
     * fails on innocent changes, and a gate that cries wolf gets overridden and then ignored. The
     * median of five is stable against one unlucky batch while still catching a real regression,
     * which moves every batch at once.
     */
    private static final int MEASURED_BATCHES = 5;

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

        double[] batches = new double[MEASURED_BATCHES];
        for (int batch = 0; batch < MEASURED_BATCHES; batch++) {
            long start = System.nanoTime();
            for (int i = 0; i < measuredTicks; i++) {
                scene.tick();
            }
            batches[batch] = (System.nanoTime() - start) / 1_000_000.0 / measuredTicks;
        }
        double[] sorted = batches.clone(); // keep run order for the printout, sort only for the median
        Arrays.sort(sorted);
        double msPerTick = sorted[MEASURED_BATCHES / 2];

        // Locale.ROOT throughout: the numbers end up in reports and greps, and a decimal comma on a
        // machine with a Russian locale makes "0,9705" unparseable to everything that reads them.
        System.out.printf(Locale.ROOT, "Buildings: %d (%d lanes of %d + a chest)%n",
                totalBuildings, lanes, laneLength);
        System.out.printf(Locale.ROOT, "Measured: %d batches of %d ticks (plus %d warmup)%n",
                MEASURED_BATCHES, measuredTicks, WARMUP_TICKS);
        StringBuilder runs = new StringBuilder();
        for (double batch : batches) {
            runs.append(String.format(Locale.ROOT, " %.4f", batch));
        }
        System.out.printf(Locale.ROOT, "Batches:%s ms/tick (spread %.1f%%)%n", runs,
                (sorted[MEASURED_BATCHES - 1] / sorted[0] - 1) * 100);
        System.out.printf(Locale.ROOT, "Median step time: %.4f ms/tick%n", msPerTick);

        double threshold = threshold();
        System.out.printf(Locale.ROOT, "Threshold: %.4f ms/tick%s%n", threshold,
                threshold == DEFAULT_THRESHOLD_MS ? "" : " (overridden via BENCHMARK_THRESHOLD_MS)");
        if (msPerTick > threshold) {
            System.out.printf(Locale.ROOT, "FAIL: over the threshold by %.1f%% — the change is not accepted%n",
                    (msPerTick / threshold - 1) * 100);
            System.exit(1); // fails `./gradlew benchmark`, so the verdict is the command's, not the reader's
        }
        System.out.printf(Locale.ROOT, "PASS: %.1f%% of the threshold%n", msPerTick / threshold * 100);
    }

    /** Reads the ceiling, allowing a slower machine to raise it for one run without editing code. */
    private static double threshold() {
        String override = System.getenv("BENCHMARK_THRESHOLD_MS");
        return override == null || override.isBlank() ? DEFAULT_THRESHOLD_MS : Double.parseDouble(override);
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

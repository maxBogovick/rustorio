package com.rustorio;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaFluids;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.OreLayoutId;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.FluidPort;
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
 * chest, kept constantly full from the tail end — which answers "is segment-based ticking itself
 * expensive" — plus, in the rows between them, {@code pipeRows} rows of plumbing each ending in a
 * tank, a generator and a pole. Still not a realistic mixed factory, but it now contains every
 * subsystem that ticks, which the belts-only scene did not: fluids and electricity arrived and the
 * gate went on measuring a world without them.
 *
 * <p>Run: {@code ./gradlew benchmark} (defaults) or
 * {@code ./gradlew benchmark --args="lanes laneLength measuredTicks pipeRows"}; {@code pipeRows=0}
 * reproduces the older belts-only scene, whose own threshold no longer applies (see {@link
 * #DEFAULT_THRESHOLD_MS}).
 */
public final class Benchmark {

    private static final int WARMUP_TICKS = 200;

    /**
     * The accepted ceiling for one tick: the measured baseline plus 15% headroom. It lives here, in
     * the only place that can compare it to a real measurement, rather than in the prose of the rule
     * files — a threshold quoted in five documents and checked by a human reading the output is not
     * a threshold, and this project had exactly that until now.
     *
     * <p><b>Re-measured when plumbing and a power grid joined the scene</b> (owner decision: the
     * gate should cover them, since a threshold that only ever saw belts said nothing about the
     * subsystems most likely to regress). Five consecutive runs on an idle machine gave medians of
     * 1.3319, 1.3382, 1.3630, 1.3949 and 1.4036 ms/tick; the baseline is the median of those,
     * 1.3630, and 15% over it is 1.57. The previous number, 1.02, described the belts-only scene and
     * was retired with it rather than quietly reused — run {@code --args="300 100 1000 0"} to
     * reproduce that older scene, though nothing gates on it any more.
     *
     * <p>Machine-dependent by nature: the baseline was measured on the owner's machine, which is
     * why this is not a CI gate. On slower hardware, override with {@code BENCHMARK_THRESHOLD_MS}
     * rather than editing this constant, so the recorded number keeps meaning what it says.
     */
    private static final double DEFAULT_THRESHOLD_MS = 1.57;

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
        int pipeRows = args.length > 3 ? Integer.parseInt(args[3]) : 100;

        Scene scene = buildScene(lanes, laneLength, pipeRows);
        int totalBuildings = lanes * (laneLength + 1) + scene.networkBuildings(); // +1 per lane — the chest at its end

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
        System.out.printf(Locale.ROOT, "Buildings: %d (%d lanes of %d + a chest%s)%n",
                totalBuildings, lanes, laneLength,
                pipeRows == 0 ? "" : String.format(Locale.ROOT,
                        "; plus %d plumbing/grid buildings across %d rows", scene.networkBuildings(), pipeRows));
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
    private static Scene buildScene(int lanes, int laneLength, int pipeRows) {
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
        List<FluidPort> steamPorts = new ArrayList<>();
        int networkBuildings = layNetworks(world, lanes, laneLength, pipeRows, steamPorts);
        return new Scene(world, tails, steamPorts, networkBuildings);
    }

    /**
     * Plumbing and a grid in the odd rows the belt lanes leave empty — off by default, because the
     * recorded threshold above was measured against the belts-only scene and a number is only a gate
     * while it keeps describing the same thing. Run {@code --args="300 100 1000 20"} to include them.
     *
     * <p>What it measured the first time it was run, on an idle machine: the belts-only scene at
     * 30300 buildings takes ~0.98 ms/tick, and adding 10000 plumbing/grid buildings (a third more
     * buildings) takes it to ~1.43 — so those buildings cost roughly 45 ns each per tick against a
     * belt's ~32. A pipe is therefore NOT free per tick despite having no {@code tick} of its own:
     * every building is still walked twice a tick and has its {@code Appearance} read, and a pipe's
     * allocates. Worth knowing before anyone quotes "a network costs nothing per tick" as a budget.
     *
     * <p>Each row is its own fluid network (belt rows separate them), fed by a full tank so a
     * generator actually converts rather than idling on an empty port — idling would measure the
     * cheap branch and call it the cost. One pole per row wires that row's generator into a grid, and
     * neighbouring rows' poles are within reach of each other, so the grid spans the block rather
     * than being one isolated pole per row: merging is what a real factory's grid does.
     *
     * @return how many buildings this added, for the printed scene description
     */
    private static int layNetworks(World world, int lanes, int laneLength, int pipeRows, List<FluidPort> steamPorts) {
        int rows = Math.min(pipeRows, lanes);
        int placed = 0;
        for (int row = 0; row < rows; row++) {
            int y = row * 2 + 1; // the empty row between two belt lanes
            for (int x = 0; x < laneLength - 3; x++) {
                if (world.place(BuildingType.PIPE, x, y)) {
                    placed++;
                }
            }
            if (world.place(BuildingType.TANK, laneLength - 3, y)) {
                placed++;
            }
            if (world.place(BuildingType.GENERATOR, laneLength - 2, y, Direction.LEFT)) {
                placed++; // faces the tank, so it draws steam from it
            }
            if (world.place(BuildingType.POLE, laneLength - 1, y)) {
                placed++;
            }
            // Topped up every tick, the same way a lane's tail belt is: a network that drains partway
            // through the run would spend the rest of it measuring an idle generator's cheap branch
            // and calling that the cost of a working one.
            world.fluidPort(laneLength - 4, y, Direction.RIGHT).ifPresent(steamPorts::add);
        }
        return placed;
    }

    private static Belt belt(World world, int x, int y) {
        return (Belt) world.peek(x, y).orElseThrow();
    }

    /** The world plus each lane's tail — every tick, top up a tail whose cargo has moved on. {@code networkBuildings} is what {@code layNetworks} added, for the printed description only. */
    private record Scene(World world, List<Belt> tails, List<FluidPort> steamPorts, int networkBuildings) {
        void tick() {
            for (Belt tail : tails) {
                tail.accept(world, VanillaItems.IRON_ORE); // tail busy -> rejected, harmlessly
            }
            for (FluidPort port : steamPorts) {
                port.insert(VanillaFluids.STEAM, 1000); // network full -> takes nothing, harmlessly
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
        public java.util.Optional<ItemType> terrainAt(int x, int y) {
            return java.util.Optional.empty();
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

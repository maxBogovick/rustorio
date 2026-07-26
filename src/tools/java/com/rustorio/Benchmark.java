package com.rustorio;

import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.building.Belt;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;

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

    private static Scene buildScene(int lanes, int laneLength) {
        World world = new World(laneLength + 2, lanes * 2);
        List<Belt> tails = new ArrayList<>(lanes);
        for (int lane = 0; lane < lanes; lane++) {
            int y = lane * 2; // every other row — adjacent lanes' belts never touch, segments never merge
            for (int x = 0; x < laneLength; x++) {
                world.placeBelt(x, y, Direction.RIGHT);
            }
            world.placeChest(laneLength, y);

            for (int x = 0; x < laneLength; x++) {
                belt(world, x, y).accept(world, Item.IRON_ORE); // pack the line with cargo up front
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
                tail.accept(world, Item.IRON_ORE); // tail busy -> rejected, harmlessly
            }
            world.tick();
        }
    }
}

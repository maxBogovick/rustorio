package com.example.rustoriomod;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.TickContext;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A building with behaviour of its own: every {@link #PERIOD} ticks it mints one of this mod's own
 * items and pushes it onto whatever it faces.
 *
 * <p>Deliberately the smallest thing that is still a real building — it shows the four contracts a
 * mod archetype actually has to satisfy, and nothing else:
 *
 * <ul>
 *   <li>{@link Building} — what it does each tick, and how it looks;
 *   <li>{@link InspectableBuilding} — what the inspection panel shows, decided here rather than by
 *       a branch inside the engine's renderer;
 *   <li>{@link #state()} — a plain record the engine's save layer serialises through this mod's own
 *       {@code Codec} (see {@link TemplateMod}), with no Jackson anywhere in this project;
 *   <li>{@link TickContext} — the only door onto the world, and the only engine type a building
 *       ever holds.
 * </ul>
 *
 * <p>Note what is NOT here: no {@code type()} naming a vanilla building kind. An archetype's
 * identity is its own {@link #prototypeId()}, and the engine asks the prototype for everything it
 * used to infer from that borrowed constant — its label, its cost, its rules.
 */
final class SparkGenerator implements Building, InspectableBuilding {

    /** Ticks between sparks. The game runs at 60 ticks/second, so this is one every two seconds. */
    private static final int PERIOD = 120;

    private final ContentId prototypeId;
    private final Direction direction;
    /**
     * What this generator mints, handed in by the prototype's behaviour lambda, which reads it off
     * the registry of the world being built. Never a static constant and never looked up per tick:
     * a save restored into a different world hands out different {@link ItemType} instances, and
     * this engine compares items by identity.
     */
    private final ItemType output;

    private int ticksUntilNextSpark;
    private @Nullable ItemType held;

    SparkGenerator(ContentId prototypeId, Direction direction, ItemType output) {
        this(prototypeId, direction, output, PERIOD);
    }

    /** Restore constructor — the countdown is this archetype's own saved state, so a reload does not restart it. */
    SparkGenerator(ContentId prototypeId, Direction direction, ItemType output, int ticksUntilNextSpark) {
        this.prototypeId = prototypeId;
        this.direction = direction;
        this.output = output;
        this.ticksUntilNextSpark = ticksUntilNextSpark;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        ItemType waiting = held;
        if (waiting != null) {
            deliver(world, x, y, waiting);
            return;
        }
        if (--ticksUntilNextSpark > 0) {
            return;
        }
        ticksUntilNextSpark = PERIOD;
        held = output;
        world.notifyProduced(output);
        deliver(world, x, y, output);
    }

    /** One item at a time: if the belt ahead is full the spark simply waits, which is the same back-pressure every vanilla producer has. */
    private void deliver(TickContext world, int x, int y, ItemType item) {
        if (world.offerForward(x + direction.dx(), y + direction.dy(), item)) {
            held = null;
        }
    }

    /**
     * Kept cheap on purpose: this runs on every frame the panel is open, so it formats fields and
     * nothing more. Anything expensive belongs where the underlying data changes.
     */
    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        return List.of("Next spark in: " + ticksUntilNextSpark + " ticks");
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public BuildingStatus status() {
        return held == null ? BuildingStatus.WORKING : BuildingStatus.OUTPUT_FULL;
    }

    @Override
    public Appearance appearance() {
        // Borrowing a vanilla sprite. A mod ships its own art by putting a .png in its own
        // `textures/` directory — the game packs every mod's textures into its atlas at startup.
        return Appearance.of(VanillaSprites.MINER);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new SparkGenerator(prototypeId, direction.rotate(), output, ticksUntilNextSpark));
    }

    @Override
    public ContentId prototypeId() {
        return prototypeId;
    }

    @Override
    public SparkState state() {
        return new SparkState(direction, ticksUntilNextSpark);
    }

    /** This archetype's saved state: plain JDK types only, which is what lets the engine persist it with no annotations in this project. */
    record SparkState(Direction direction, int ticksUntilNextSpark) {
    }
}

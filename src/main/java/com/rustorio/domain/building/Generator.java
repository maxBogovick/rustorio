package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidType;
import java.util.Optional;

/**
 * Burns a fluid into electricity: the last link of the chain, and the building that stops steam
 * from being a dead end. It draws from the fluid network on the side it faces and hands the power
 * to whichever grid covers it — which it never learns anything about, because {@code World} asks it
 * for output and delivers that somewhere on its behalf (see {@link PowerProducer}).
 *
 * <p><b>It produces only what it is asked for, once per tick, before anything else ticks.</b> There
 * is no buffer and no charge to carry: power that nothing draws this tick is gone, since storing it
 * is what an accumulator would be for and there is no accumulator. Which means a generator with a
 * full steam pipe next to it and no machines on the grid still burns its steam — a real cost of
 * running a generator you do not need, and the same way a real one behaves.
 *
 * <p>Which fluid it burns and how much power it makes are data ({@link
 * BuildingPrototype#fluidInput()}, {@link BuildingPrototype#power()}), so this class names neither
 * steam nor any number: a mod's own gas turbine is a JSON file on this archetype.
 */
public final class Generator implements Building, PowerProducer {

    /**
     * How much steam one tick of full output burns. Anchored on Factorio's chain: a boiler there
     * feeds two steam engines, so a generator burns HALF a boiler's output (the boiler makes 10 —
     * see {@link Boiler} — this burns 5), which is the 1 boiler : 2 generators ratio. The output it
     * produces for that steam lives on the prototype ({@code power.output}), set so one generator
     * powers ten electric miners — Factorio's own 900 kW engine to 90 kW drill, ten to one.
     */
    private static final long BURNED_PER_TICK = 5;

    private final BuildingType type;
    private final Direction direction;
    private final BuildingPrototype prototype;

    /** Recomputed once per {@link #produce}, not once per render frame — see {@link BuildingStatus}. */
    private BuildingStatus status = BuildingStatus.NO_INPUT;

    public Generator(BuildingType type, Direction direction, BuildingPrototype prototype) {
        this.type = type;
        this.direction = direction;
        this.prototype = prototype;
    }

    @Override
    public long produce(TickContext world, int x, int y) {
        FluidType burned = prototype.fluidInput();
        PowerSpec spec = prototype.power();
        if (burned == null || spec == null || spec.output() == 0) {
            return 0; // a prototype that names no fuel or no output produces nothing — a no-op, not a guess
        }
        Optional<FluidPort> source = world.fluidPort(x, y, direction);
        if (source.isEmpty()) {
            status = BuildingStatus.NO_INPUT;
            return 0;
        }
        long taken = source.orElseThrow().extract(burned, BURNED_PER_TICK);
        if (taken < BURNED_PER_TICK) {
            // Part of a tick's fuel is not part of a tick's power: a generator either runs this tick
            // or does not. Whatever partial draw happened is spent anyway — the same "fuel committed
            // when the machine started" rule a boiler already follows.
            status = BuildingStatus.NO_INPUT;
            return 0;
        }
        status = BuildingStatus.WORKING;
        return spec.output();
    }

    /** The status field this archetype already keeps, handed over without building an {@link Appearance} — see {@link Building#status()}. */
    @Override
    public BuildingStatus status() {
        return status;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture(), status);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Generator(type, direction.rotate(), prototype));
    }

    @Override
    public BuildingType type() {
        return type;
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public GeneratorState state() {
        return new GeneratorState(direction);
    }
}

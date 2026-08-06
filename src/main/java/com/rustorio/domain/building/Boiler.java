package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import java.util.Optional;

/**
 * Burns an item to turn one fluid into another: the middle link of the chain, and the first machine
 * in the game that touches two fluid networks and an item at once. Water arrives from the network
 * BEHIND it, steam leaves into the one in FRONT — its facing is the output side, the same
 * convention every directional building here uses — and coal arrives the ordinary way, offered by a
 * belt or an inserter into {@link #accept}.
 *
 * <p><b>Nothing is buffered, and that is the safety property.</b> A boiler moves fluid within a
 * single tick or not at all: it first asks how much room the steam side has, then draws exactly
 * that much water. The order matters — drawing water first and discovering afterwards that the
 * steam had nowhere to go would destroy it, silently, every tick a factory ran backed up. The test
 * named for that case is the one that would catch it coming back.
 *
 * <p>Which fluids those are is data ({@link BuildingPrototype#fluidInput()}/{@link
 * BuildingPrototype#fluidOutput()}), as is what it burns ({@link BuildingPrototype#fuelItem()}) —
 * so this class names neither water, nor steam, nor coal, and a mod's own refinery is a JSON file
 * rather than a second Java class. One unit in, one unit out: a conversion ratio is a balance
 * decision nobody has made, and 1:1 is the one value that needs no justification.
 */
public final class Boiler implements Building {

    /**
     * How many ticks one item of fuel burns, and how much fluid is converted per tick. These are a
     * balance pass now, anchored on Factorio's own chain rather than picked freely: a boiler there
     * feeds exactly TWO steam engines, so this one converts twice a generator's burn rate (a
     * generator drinks 5 steam a tick — see {@code Generator} — so the boiler makes 10). One water
     * becomes one steam (the 1:1 the class javadoc argues for), so a boiler also drinks 10 water a
     * tick, which is exactly one pump's output — the clean 1 pump : 1 boiler : 2 generators the
     * player can count on. 60 ticks is one second at the fixed step, so one coal drives the boiler
     * for a second.
     */
    private static final int BURN_TICKS_PER_FUEL = 60;

    private static final long CONVERTED_PER_TICK = 10;

    /** Room for a handful of fuel, the same shallow buffer a {@link Furnace} keeps, for the same reason: a machine that hoards fuel starves its neighbors. */
    private static final int FUEL_MAX = 5;

    private final BuildingType type;
    private final Direction direction;
    private final BuildingPrototype prototype;

    private int fuelBuffer;
    private int burnTicksLeft;
    /** Recomputed once per {@link #tick}, not once per render frame — see {@link BuildingStatus}. */
    private BuildingStatus status = BuildingStatus.NO_INPUT;

    public Boiler(BuildingType type, Direction direction, BuildingPrototype prototype) {
        this.type = type;
        this.direction = direction;
        this.prototype = prototype;
    }

    /** Restore constructor used by this prototype's registered {@code RestoreFactory}. */
    Boiler(BuildingType type, BoilerState state, BuildingPrototype prototype) {
        this(type, state.direction(), prototype);
        this.fuelBuffer = state.fuelBuffer();
        this.burnTicksLeft = state.burnTicksLeft();
    }

    /** Takes exactly this prototype's own fuel item, up to {@link #FUEL_MAX} — everything else is refused, so a belt of ore won't clog it. */
    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (!item.equals(prototype.fuelItem()) || fuelBuffer >= FUEL_MAX) {
            return false;
        }
        fuelBuffer++;
        return true;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        FluidType drawn = prototype.fluidInput();
        FluidType produced = prototype.fluidOutput();
        if (drawn == null || produced == null) {
            return; // a prototype missing either port has no conversion to run — a no-op, not a guess
        }
        if (burnTicksLeft == 0) {
            if (fuelBuffer == 0) {
                status = BuildingStatus.NO_FUEL;
                return;
            }
            fuelBuffer--;
            burnTicksLeft = BURN_TICKS_PER_FUEL;
        }
        // Two clockwise quarter-turns is the opposite side; Direction has no `opposite()` of its own
        // and adding one to the enum for a single caller would be a wider change than this needs.
        Optional<FluidPort> source = world.fluidPort(x, y, direction.rotate().rotate());
        Optional<FluidPort> sink = world.fluidPort(x, y, direction);
        if (source.isEmpty()) {
            status = BuildingStatus.NO_INPUT;
            return;
        }
        if (sink.isEmpty()) {
            status = BuildingStatus.OUTPUT_FULL;
            return;
        }
        long room = roomFor(sink.orElseThrow(), produced);
        if (room == 0) {
            status = BuildingStatus.OUTPUT_FULL;
            return;
        }
        // Asked BEFORE any water is taken: see the class javadoc on why the other order destroys fluid.
        long taken = source.orElseThrow().extract(drawn, Math.min(CONVERTED_PER_TICK, room));
        if (taken == 0) {
            status = BuildingStatus.NO_INPUT;
            return;
        }
        sink.orElseThrow().insert(produced, taken);
        burnTicksLeft--;
        status = BuildingStatus.WORKING;
    }

    /** How much of {@code produced} would fit right now — {@code 0} if that network is full or holds something else entirely. */
    private static long roomFor(FluidPort sink, FluidType produced) {
        FluidType present = sink.fluid();
        if (present != null && !present.equals(produced)) {
            return 0;
        }
        return sink.capacity() - sink.amount();
    }

    @Override
    public Appearance appearance() {
        return fuelBuffer > 0 || burnTicksLeft > 0
                ? Appearance.of(prototype.texture(), fuelBuffer, status)
                : Appearance.of(prototype.texture(), status);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Boiler(type, new BoilerState(direction.rotate(), fuelBuffer, burnTicksLeft), prototype));
    }

    /**
     * How much fuel is on hand. Read by tests today, not by the inspection panel: that panel breaks
     * out a {@code Furnace}'s buffer and has no case for a boiler, so a boiler's fuel is currently
     * invisible in game. Worth knowing before writing UI that assumes otherwise.
     */
    public int fuelBuffer() {
        return fuelBuffer;
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
    public BoilerState state() {
        return new BoilerState(direction, fuelBuffer, burnTicksLeft);
    }
}

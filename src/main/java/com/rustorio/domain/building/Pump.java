package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.OreLayout;
import java.util.Optional;

/**
 * Stands on the shore and lifts fluid into the network in front of it — the fluid counterpart to
 * {@link Miner}, and structurally the same building: a fixed cycle, a fixed batch, delivery
 * addressed to ONE side rather than broadcast to whichever neighbor happens to accept.
 *
 * <p>Two differences from a miner, both following from fluid being a volume rather than a count.
 * There is nothing to hold between cycles: a batch either fits in the network or it does not, and
 * an offer that only partly fits is simply a partial delivery, not something to keep and retry. And
 * the vanilla shore source is inexhaustible — {@link PlacementRule#ADJACENT_TO_WATER} put this pump
 * next to water and water does not deplete, so there is no {@code NO_ORE} equivalent to report
 * after placement.
 *
 * <p>A mod's own pump can instead sit ON an ore cell ({@link PlacementRule#NEEDS_ORE}, the way
 * {@code petrochem:oil_pump} lifts {@code crude_oil} out of {@code oil_sand}) — that reserve is
 * finite the same way a {@link Miner}'s is, so every cycle also spends one {@link
 * OreLayout#extract} call the same way a miner's batch does, keyed by the SAME shared depletion
 * rule ({@code OreDepletion}). On the vanilla shore cell this is a harmless no-op: {@link
 * OreLayout#extract} on a cell with no ore under it always returns empty and changes nothing, so a
 * water pump behaves exactly as before. Spent once per cycle regardless of whether the batch was
 * actually delivered — the same reason an undelivered water batch is simply lost rather than held:
 * gating the spend on delivery would let a player avoid ever depleting a patch by leaving the pipe
 * disconnected, which defeats the point of a finite reserve.
 *
 * <p>Which fluid it lifts is {@link BuildingPrototype#fluidOutput()} — data, so this class never
 * mentions water and a mod's own "oil derrick" is a JSON file rather than a second Java class. A
 * prototype that names no output fluid produces nothing at all rather than guessing one.
 */
public final class Pump implements Building {

    /**
     * Ticks between batches, and how much each batch is. A balance pass now: one pipe's worth (100)
     * every 10 ticks averages 10 water a tick, exactly what one boiler drinks (see {@code Boiler}),
     * so one pump feeds one boiler with nothing left over and nothing short — the head of the clean
     * 1 pump : 1 boiler : 2 generators chain. Kept as a batch rather than a trickle so a pump
     * visibly pulses a short run full instead of dribbling into it.
     */
    private static final int PUMP_INTERVAL = 10;

    private static final long PUMP_BATCH = 100;

    private final BuildingType type;
    private final Direction direction;
    private final BuildingPrototype prototype;
    private final OreLayout oreLayout;

    private int cooldown = PUMP_INTERVAL;
    /** Recomputed once per {@link #tick}, not once per render frame — see {@link BuildingStatus}. */
    private BuildingStatus status = BuildingStatus.WORKING;

    public Pump(BuildingType type, Direction direction, BuildingPrototype prototype, OreLayout oreLayout) {
        this.type = type;
        this.direction = direction;
        this.prototype = prototype;
        this.oreLayout = oreLayout;
    }

    /** Restore constructor used by this prototype's registered {@code RestoreFactory}. */
    Pump(BuildingType type, Direction direction, int cooldown, BuildingPrototype prototype, OreLayout oreLayout) {
        this(type, direction, prototype, oreLayout);
        this.cooldown = cooldown;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        FluidType lifted = prototype.fluidOutput();
        if (lifted == null) {
            return; // a prototype that names no fluid has nothing to pump — not a guess, a no-op
        }
        if (--cooldown > 0) {
            return;
        }
        cooldown = PUMP_INTERVAL;
        // No-op where there is no ore under this cell (every vanilla water pump) — see the class javadoc.
        oreLayout.extract(x, y);
        Optional<FluidPort> target = world.fluidPort(x, y, direction);
        if (target.isEmpty()) {
            // No plumbing in front of it at all — the same class of problem as a miner facing a wall,
            // and reported the same way, so the player sees WHY nothing is coming out.
            status = BuildingStatus.OUTPUT_FULL;
            return;
        }
        long accepted = target.orElseThrow().insert(lifted, PUMP_BATCH);
        status = accepted > 0 ? BuildingStatus.WORKING : BuildingStatus.OUTPUT_FULL;
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
        return Optional.of(new Pump(type, direction.rotate(), cooldown, prototype, oreLayout));
    }


    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public PumpState state() {
        return new PumpState(direction, cooldown);
    }
}

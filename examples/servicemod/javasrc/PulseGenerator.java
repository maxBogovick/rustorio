package com.servicemod.jarmod;

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

/**
 * A building whose behavior is genuinely new AND depends on a capability the engine does not have:
 * every time {@link PulseService} says a pulse elapsed, it mints one item and pushes it onto
 * whatever it faces. Written entirely outside the engine, compiled into this mod's own real
 * {@code .jar}.
 *
 * <p>Three separate claims about the engine are load-bearing here, and each fails loudly if the
 * engine regresses: the behavior itself reaches a world through a registered prototype; the SERVICE
 * reaches this building through {@link TickContext#service}; and what the panel shows comes from
 * {@link InspectableBuilding} rather than from a branch in the renderer.
 *
 * <p>Degrades rather than throwing when no service is registered — the same discipline every
 * engine archetype follows for a missing input, and what makes the "same save, mod removed" half of
 * the acceptance test possible.
 */
final class PulseGenerator implements Building, InspectableBuilding {

    private final ContentId prototypeId;
    private final Direction direction;
    /**
     * What this generator mints. Handed in by the prototype's own behavior lambda, which reads it
     * off the registry of the world being built — never cached statically and never looked up per
     * tick: a save restored into a different world hands out different {@link ItemType} instances,
     * and this project matches items by identity.
     */
    private final ItemType output;
    private int produced;
    private ItemType held;
    private PulseService service;
    private boolean serviceResolved;

    PulseGenerator(ContentId prototypeId, Direction direction, ItemType output) {
        this(prototypeId, direction, output, 0);
    }

    /** Restore constructor — {@code produced} is this archetype's own saved state, which is what makes the save/load half of the acceptance test meaningful. */
    PulseGenerator(ContentId prototypeId, Direction direction, ItemType output, int produced) {
        this.prototypeId = prototypeId;
        this.direction = direction;
        this.output = output;
        this.produced = produced;
    }

    /** How many items this generator has minted in its lifetime — persisted, and shown in the panel. */
    int produced() {
        return produced;
    }

    /**
     * Resolved once and kept, never per tick: {@link TickContext#service} is a map lookup, and a
     * world's services cannot change after it is built. This is the pattern the engine's own
     * javadoc asks mods to follow.
     */
    private PulseService service(TickContext world) {
        if (!serviceResolved) {
            service = world.service(PulseService.KEY).orElse(null);
            serviceResolved = true;
        }
        return service;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held != null) {
            deliver(world, x, y);
            return;
        }
        PulseService pulses = service(world);
        if (pulses == null || !pulses.nextPulse()) {
            return;
        }
        held = output;
        produced++;
        world.notifyProduced(output);
        deliver(world, x, y);
    }

    private void deliver(TickContext world, int x, int y) {
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        return List.of("Pulses minted: " + produced,
                service(world) == null ? "Pulse service: not installed" : "Pulse service: ready");
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
        return Appearance.of(VanillaSprites.MINER);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new PulseGenerator(prototypeId, direction.rotate(), output, produced));
    }


    @Override
    public ContentId prototypeId() {
        return prototypeId;
    }

    /** Direction plus the lifetime count — plain JDK types, so the engine's generic save path serializes it with no Jackson annotation anywhere in this mod. */
    @Override
    public PulseState state() {
        return new PulseState(direction, produced);
    }

    /** This archetype's saved state. A record of plain types, exactly as the engine's {@code Codec} contract asks. */
    record PulseState(Direction direction, int produced) {
    }
}

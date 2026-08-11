package com.lightbulb;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TickContext;
import java.util.List;
import java.util.Optional;

/**
 * Placed lightbulb: each tick asks the power grid for a little electricity and swaps its sprite
 * when the answer is yes. That is the whole point of the educational mod — the crafted item becomes
 * a building that visibly burns, using the same furnace-hot/cold pattern the engine already has,
 * because there is no separate glow API for mods to call.
 *
 * <p>Data alone cannot express this: no vanilla archetype is a pure power consumer that does no
 * crafting. {@code SimpleCrafter} would demand an input item every cycle; a pole neither draws
 * power nor changes look. So this class is the L3 half of the mod.
 */
final class Lamp implements Building, InspectableBuilding {

    static final ContentId SPRITE_OFF = ContentId.of("lightbulb:lamp_off");
    static final ContentId SPRITE_ON = ContentId.of("lightbulb:lamp_on");

    private final BuildingPrototype prototype;
    private final Direction direction;

    /** Recomputed in {@link #tick}, never persisted — power is not storable, so neither is "lit". */
    private boolean lit;
    private BuildingStatus status = BuildingStatus.NO_POWER;

    Lamp(BuildingPrototype prototype, Direction direction) {
        this.prototype = prototype;
        this.direction = direction;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        PowerSpec spec = prototype.power();
        int demand = spec == null ? 0 : spec.demand();
        lit = demand == 0 || world.drawPower(x, y, demand);
        status = lit ? BuildingStatus.WORKING : BuildingStatus.NO_POWER;
    }

    @Override
    public BuildingStatus status() {
        return status;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(lit ? SPRITE_ON : SPRITE_OFF, status);
    }

    @Override
    public Optional<Direction> outputDirection() {
        // Direction is what the save stores; exposing it keeps rotation/ghost arrows honest and
        // gives the acceptance test something the codec must round-trip.
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Lamp(prototype, direction.rotate()));
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public LampState state() {
        return new LampState(direction);
    }

    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        return List.of(lit ? "Lit" : "No power — need a pole and a generator");
    }

    /** Direction only: lit is derived each tick from the live grid. */
    record LampState(Direction direction) {
    }
}

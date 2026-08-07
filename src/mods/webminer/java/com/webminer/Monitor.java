package com.webminer;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.building.BeltState;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.SettlesEachTick;
import com.rustorio.domain.building.TickContext;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * A belt-transparent pass-through — accepts one item, holds it exactly one tick (same {@link
 * SettlesEachTick} discipline the vanilla filter and splitter already use), then pushes it on —
 * which lets a player insert it between a {@link WebMiner} and whatever it feeds without changing
 * the chain's throughput at all.
 *
 * <p>What makes it a "monitor" rather than a plain belt is entirely on the READ side: clicking one
 * open shows the last response body fetched by whatever stands BEHIND it (opposite {@link
 * #direction} — the same "facing is output, behind is input" convention {@link BuildingPrototype}'s
 * own javadoc states). It holds no reference to that body itself; it asks {@link FetchService} for
 * the cell behind it at the moment the panel asks IT.
 *
 * <p>That answer used to be assembled by {@code com.graphics.render.InspectionPanelLayout}, in a
 * branch naming this class — which is why the rendering layer briefly had to know what a response
 * body was, and why a mod could not have shipped this archetype without editing the engine's own
 * renderer. {@link InspectableBuilding} is the door that replaced the branch.
 */
public final class Monitor implements Building, SettlesEachTick, InspectableBuilding {

    /** Panel text for a monitor whose upstream has fetched nothing yet — a fact about this building, so it lives here rather than in the renderer that draws it. */
    private static final String NOTHING_YET = "(no response seen behind this monitor yet)";

    private final Direction direction;
    /** Which sprite {@link #appearance} draws — injected rather than hardcoded, so a prototype reusing this archetype can ship its own art. */
    private final BuildingPrototype prototype;
    private @Nullable ItemType held;
    /** Same one-tick settle every other relay carries, so an item never crosses two cells in one tick. */
    private boolean arrivedThisTick;

    public Monitor(Direction direction, BuildingPrototype prototype) {
        this(direction, null, prototype);
    }

    /** Restore constructor — public because this archetype is registered from this mod's own jar, outside the engine. */
    public Monitor(Direction direction, @Nullable ItemType held, BuildingPrototype prototype) {
        this.direction = direction;
        this.held = held;
        this.prototype = prototype;
    }

    /**
     * The raw body most recently fetched by the cell behind this one, or a note that there isn't
     * one yet. Cheap by construction — one map lookup and no parsing (see {@link
     * InspectableBuilding}'s own warning about what this method must not do): the line is returned
     * whole, and the panel wraps it to whatever width it happens to be.
     *
     * <p>No service in this world means no fetching has happened or can happen, which reads to the
     * player exactly the same as "nothing fetched yet" — so it says that rather than exposing that
     * a mod is half-wired.
     */
    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        FetchService fetch = world.service(FetchService.KEY).orElse(null);
        if (fetch == null) {
            return List.of(NOTHING_YET);
        }
        return List.of(fetch.lastBody(x - direction.dx(), y - direction.dy()).orElse(NOTHING_YET));
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (held != null) {
            return false;
        }
        held = item;
        arrivedThisTick = true;
        return true;
    }

    @Override
    public void clearArrivalMark() {
        arrivedThisTick = false;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held == null || arrivedThisTick) {
            return;
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public BuildingStatus status() {
        return BuildingStatus.WORKING;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture());
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Monitor(direction.rotate(), held, prototype));
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public BeltState state() {
        return new BeltState(direction, held);
    }
}

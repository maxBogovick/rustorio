package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.FluidFill;
import com.rustorio.domain.FluidType;
import org.jspecify.annotations.Nullable;

/**
 * A tile of fluid plumbing: a pipe, or — with a bigger {@link BuildingPrototype#bufferMax()} — a
 * tank. One class for both, the same way {@link Furnace} serves FURNACE/PRESS/ASSEMBLER: the two
 * differ only in how much they hold, and "how much" is data on the prototype, not behavior.
 *
 * <p><b>It does not tick.</b> There is no {@code tick} override here at all, and that is the design
 * rather than an omission: a whole {@link FluidNetwork} is one bucket, so there is no tile-to-tile
 * flow to advance. What moves fluid is a machine at the edge pushing or pulling through a {@link
 * FluidPort}. Note that "does not tick" is not the same as "costs nothing": every placed building,
 * this one included, is still walked by {@code TickScheduler} and still has {@link #appearance()}
 * read for status bookkeeping once a tick — see {@link FluidNetwork}'s own javadoc.
 *
 * <p><b>It holds fluid only while it is between networks</b> — see {@link FluidNode}. Placed, it
 * asks its network what its share is; that share is what a save records, and what the tile carries
 * away when it is demolished.
 *
 * <p>Not rotatable and not upgradable: a network has no direction to face, and doubling a {@code
 * tick()} that does nothing would do nothing twice (the same reason belts and splitters refuse
 * speed effects — see {@code VanillaBuildings#registerAll}).
 */
public final class Pipe implements Building, FluidNode {

    private final BuildingType type;
    private final BuildingPrototype prototype;

    private @Nullable FluidNetwork network;

    /** What this tile carries while it belongs to no network — always {@code null}/{@code 0} while placed. */
    private @Nullable FluidType detachedFluid;
    private long detachedAmount;

    /** A brand-new, empty tile — what {@code BuildingFactory.create} builds. */
    public Pipe(BuildingType type, BuildingPrototype prototype) {
        this(type, prototype, null, 0);
    }

    /**
     * A tile restored from a save, carrying its own recorded share until it joins a network and
     * pours it in — see {@link FluidNode}'s javadoc for why a tile between networks holds anything
     * at all.
     */
    public Pipe(BuildingType type, BuildingPrototype prototype, @Nullable FluidType fluid, long amount) {
        this.type = type;
        this.prototype = prototype;
        this.detachedFluid = fluid;
        this.detachedAmount = amount;
    }

    @Override
    public long fluidCapacity() {
        return prototype.bufferMax();
    }

    @Override
    public @Nullable FluidNetwork network() {
        return network;
    }

    @Override
    public void joinNetwork(@Nullable FluidNetwork network) {
        this.network = network;
    }

    @Override
    public @Nullable FluidType detachedFluid() {
        return detachedFluid;
    }

    @Override
    public long detachedAmount() {
        return detachedAmount;
    }

    @Override
    public void setDetached(@Nullable FluidType fluid, long amount) {
        this.detachedFluid = fluid;
        this.detachedAmount = amount;
    }

    /** Always {@code WORKING}: a pipe has no notion of being stuck — see {@link Building#status()}. */
    @Override
    public BuildingStatus status() {
        return BuildingStatus.WORKING;
    }

    /**
     * How full this tile's network is, as a fill bar in the fluid's own colour ({@link FluidFill}).
     * A fraction of the WHOLE network, not of this tile — that is what "one bucket" means, and every
     * tile of a network therefore shows the same bar. This used to be a bare percentage badge
     * because {@link Appearance} had no field to carry the fluid's colour; now it does, so the badge
     * is gone and the bar replaces it (a badge alongside would say the same thing twice).
     */
    @Override
    public Appearance appearance() {
        FluidNetwork current = network;
        if (current == null || current.capacity() == 0 || current.amount() == 0) {
            return Appearance.of(prototype.texture());
        }
        FluidType carried = current.fluid();
        if (carried == null) {
            return Appearance.of(prototype.texture()); // amount > 0 implies a fluid, but NullAway can't see that
        }
        int percent = (int) (current.amount() * 100 / current.capacity());
        return Appearance.of(prototype.texture(), new FluidFill(carried, percent));
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
    public PipeState state() {
        FluidNetwork current = network;
        if (current == null) {
            FluidType carried = detachedFluid;
            return new PipeState(carried == null ? null : carried.id(), detachedAmount);
        }
        long share = current.shareOf(this);
        FluidType carried = current.fluid();
        return share > 0 && carried != null
                ? new PipeState(carried.id(), share)
                : new PipeState(null, 0);
    }
}

package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Item;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Sits on an ore cell and periodically mines a batch, holding it until a neighbor takes it — the
 * same "hold until delivered" discipline as {@link Belt} and {@link Furnace}: mining never
 * outpaces delivery, so nothing is produced and silently discarded.
 *
 * <p>Depends on an injected {@link OreLayout} rather than a static ore table — which ore sits
 * under a given cell doesn't change, so nothing needs to be cached; asking the strategy again
 * every batch keeps the miner decoupled from any one concrete map generator.
 *
 * <p>A miner placed on a cell with no ore simply idles forever, retrying every {@link
 * #effectiveTime} ticks — a soft degradation rather than a crash, since placement not lining up
 * with the ore map is a recoverable state, not a programming error.
 */
public final class Miner implements Building {

    private static final int MINE_TIME = 3;

    private final OreLayout oreLayout;

    private int cooldown = MINE_TIME;
    private @Nullable Item held;

    public Miner(OreLayout oreLayout) {
        this.oreLayout = oreLayout;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Miner(OreLayout oreLayout, int cooldown, @Nullable Item held) {
        this.oreLayout = oreLayout;
        this.cooldown = cooldown;
        this.held = held;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (held == null) {
            if (--cooldown > 0) {
                return;
            }
            cooldown = effectiveTime(world);
            Optional<Item> ore = oreLayout.oreAt(x, y);
            if (ore.isEmpty()) {
                return; // no ore under this tile — idle rather than crash; the timer above retries later
            }
            held = ore.get();
            world.notifyProduced(held);
        }
        if (world.tryDeliverToNeighbor(x, y, held)) {
            held = null;
        }
    }

    private static int effectiveTime(World world) {
        return world.research().isUnlocked(Tech.FAST_MINING) ? Math.max(1, MINE_TIME / 2) : MINE_TIME;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.MINER);
    }

    @Override
    public Optional<Item> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public BuildingType type() {
        return BuildingType.MINER;
    }

    @Override
    public BuildingMemento memento() {
        return new BuildingMemento.MinerState(cooldown, held);
    }
}

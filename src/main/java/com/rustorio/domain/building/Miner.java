package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.Tech;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Sits on an ore cell and periodically mines a batch, holding it until its forward neighbor takes
 * it — the same "hold until delivered" discipline as {@link Belt} and {@link Furnace}: mining
 * never outpaces delivery, so nothing is produced and silently discarded.
 *
 * <p>Depends on an injected {@link OreLayout} rather than a static ore table — which ore sits
 * under a given cell doesn't change, so nothing needs to be cached; asking the strategy again
 * every batch keeps the miner decoupled from any one concrete map generator.
 *
 * <p>A miner placed on a cell with no ore simply idles forever, retrying every {@link
 * #effectiveTime} ticks — a soft degradation rather than a crash, since placement not lining up
 * with the ore map is a recoverable state, not a programming error.
 *
 * <p><b>Owner decision (D-01, DEV_TASKS.md):</b> delivery is addressed to the single cell ahead of
 * {@link #direction}, via {@link TickContext#offerForward}, not broadcast to all four neighbors.
 * Before this, a miner built next to ANY building that would accept ore worked regardless of which
 * way it faced, making belts and layout optional — see §2.1 of the design audit. This is also why
 * a miner now needs {@link #rotatedClockwise}: once delivery is addressed, a miner built facing the
 * wrong way is unrecoverable without demolition unless it can be turned in place.
 */
public final class Miner implements Building {

    private static final int MINE_TIME = 3;

    private final OreLayout oreLayout;
    private final Direction direction;

    private int cooldown = MINE_TIME;
    private @Nullable ItemType held;
    /** Recomputed once per {@link #tick}, not once per render frame — see {@link BuildingStatus}'s own javadoc for why (F-01, DEV_TASKS.md). */
    private BuildingStatus status = BuildingStatus.WORKING;
    /** {@code UpgradeSpeedAction}'s upgrade count — see {@link #tick}'s own note on how it's applied. */
    private int speedLevel;

    public Miner(OreLayout oreLayout, Direction direction) {
        this.oreLayout = oreLayout;
        this.direction = direction;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Miner(OreLayout oreLayout, Direction direction, int cooldown, @Nullable ItemType held, int speedLevel) {
        this.oreLayout = oreLayout;
        this.direction = direction;
        this.cooldown = cooldown;
        this.held = held;
        this.speedLevel = speedLevel;
    }

    /**
     * Runs {@link #tickOnce} {@code 1 << speedLevel} times — the same multiplier {@code
     * SpeedModule} used to produce by nesting {@code speedLevel} independent wrapper layers, each
     * doubling whatever it wrapped (owner decision: preserve the exact ×2^N stacking, not switch to
     * a linear ×(1+N) just because the mechanism moved from a decorator to a field).
     */
    @Override
    public void tick(TickContext world, int x, int y) {
        for (int i = 0, repeats = 1 << speedLevel; i < repeats; i++) {
            tickOnce(world, x, y);
        }
    }

    private void tickOnce(TickContext world, int x, int y) {
        if (held == null) {
            if (--cooldown > 0) {
                return;
            }
            cooldown = effectiveTime(world);
            Optional<ItemType> ore = oreLayout.extract(x, y);
            if (ore.isEmpty()) {
                // Two different situations, and (N14, NEW_BUGS_PROGRESS.md — owner decision) they no
                // longer look the same on screen. No ore under this tile at all is NO_ORE, a
                // standing problem the player has to move the miner to fix. A cell whose reserve
                // just didn't yield on this particular call is NOT: a depleted cell still yields on
                // 1 call in OreDepletion.TAIL_INTERVAL (D-04, DEV_TASKS.md), so reporting NO_ORE
                // here made a slow-but-working miner flicker between "broken" and "fine" every few
                // ticks. It IS working, just slowly — which is exactly what WORKING says, so no
                // third status is needed. hasOre is a pure report and consumes nothing.
                status = oreLayout.hasOre(x, y) ? BuildingStatus.WORKING : BuildingStatus.NO_ORE;
                return;
            }
            held = ore.get();
            world.notifyProduced(held);
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
            status = BuildingStatus.WORKING;
        } else {
            status = BuildingStatus.OUTPUT_FULL;
        }
    }

    private static int effectiveTime(TickContext world) {
        return world.research().isUnlocked(Tech.FAST_MINING) ? Math.max(1, MINE_TIME / 2) : MINE_TIME;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(VanillaSprites.MINER, status);
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new Miner(oreLayout, direction.rotate(), cooldown, held, speedLevel));
    }

    @Override
    public int speedLevel() {
        return speedLevel;
    }

    @Override
    public Building withSpeedLevel(int newSpeedLevel) {
        return new Miner(oreLayout, direction, cooldown, held, newSpeedLevel);
    }

    @Override
    public BuildingType type() {
        return BuildingType.MINER;
    }

    @Override
    public MinerState state() {
        return new MinerState(direction, cooldown, held, speedLevel);
    }
}

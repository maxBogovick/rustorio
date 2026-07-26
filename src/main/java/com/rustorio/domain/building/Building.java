package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import java.util.Optional;

/**
 * Anything the world can hold in a cell and step forward one tick at a time.
 *
 * <p><b>Why {@code sealed}.</b> The set of building kinds is closed and known here, in one place
 * — {@link Miner}, {@link Chest}, {@link Furnace}, {@link Belt}, {@link Splitter}, {@link
 * SpeedModule}, {@link Lab} and {@link UndergroundBelt}. A stray class can't quietly become a
 * building outside this file, and the compiler can enforce exhaustiveness anywhere code switches
 * over a building's concrete type (see {@link BuildingFactory#restore}).
 *
 * <p><b>The tax {@link SpeedModule} pays for that.</b> Decorator wants to wrap any object behind
 * an interface without asking the interface's author for permission; {@code sealed} is the
 * opposite idea — the set of wrappable types is closed and must know about the decorator ahead of
 * time. {@code SpeedModule} has to be listed in {@code permits} rather than simply implementing
 * {@code Building} from anywhere; that's real friction between the two patterns, not an oversight.
 */
public sealed interface Building
        permits Miner, Chest, Furnace, Belt, Splitter, SpeedModule, Lab, UndergroundBelt {

    /**
     * Live one tick. The world calls this once per building per step, passing the building's own
     * coordinates (the building itself doesn't store them — only the world's cell map does) so it
     * can address its neighbors.
     *
     * <p>Default is a no-op: most buildings (a chest) simply sit there; whoever is active (a
     * miner) overrides it.
     */
    default void tick(TickContext world, int x, int y) {
    }

    /**
     * Accept an item offered by a neighbor.
     *
     * <p>Default is "no" — a miner only gives, never takes. Buildings that do accept something
     * (a chest, a furnace) override this instead of the world running an {@code instanceof} chain
     * to guess who's willing.
     *
     * @return {@code true} if the item was taken; {@code false} if this building doesn't want it
     */
    default boolean accept(TickContext world, Item item) {
        return false;
    }

    /** The item this building is physically holding "in transit" right now, if any. */
    default Optional<Item> heldItem() {
        return Optional.empty();
    }

    /** How many {@link SpeedModule} layers wrap this building — {@code 0} if none. */
    default int speedLevel() {
        return 0;
    }

    /** How this building looks right now — sprite plus an optional numeric badge. */
    Appearance appearance();

    /** This building's kind, as shown in the hotbar and stored by the save system. */
    BuildingType type();

    /** Capture this building's entire internal state for persistence (Memento pattern). */
    BuildingMemento memento();

    /**
     * Whether this building doesn't care what order it's ticked in relative to its neighbors this
     * step. True for almost everything; a belt pushing left or up is the one exception — see
     * {@code World.tick()} for why the traversal order matters to it.
     */
    default boolean prefersDescendingTick() {
        return true;
    }

    /**
     * The direction this building's main (or only) output faces, if it has one at all — {@code
     * empty} if it accepts/holds the same from every side (a chest) or decides dynamically (a
     * miner tries all four neighbors). Purely cosmetic: only the direction-arrow overlay reads it.
     */
    default Optional<Direction> outputDirection() {
        return Optional.empty();
    }

    /**
     * A second output direction, for buildings that have two — currently only {@link Splitter}.
     */
    default Optional<Direction> secondaryOutputDirection() {
        return Optional.empty();
    }

    /** Strip away any {@link SpeedModule} layers and return the real building underneath. */
    static Building unwrap(Building building) {
        Building current = building;
        while (current instanceof SpeedModule module) {
            current = module.inner();
        }
        return current;
    }
}

package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import java.util.Optional;

/**
 * Anything the world can hold in a cell and step forward one tick at a time.
 *
 * <p><b>Open, not closed.</b> Used to be {@code sealed} with a fixed {@code permits} list — the
 * ten vanilla kinds ({@link Miner}, {@link Chest}, {@link Furnace}, {@link Belt}, {@link Splitter},
 * {@link Filter}, {@link Inserter}, {@link Lab}, {@link UndergroundBelt}) and nothing else. That
 * closed the door on a mod ever adding its own building behavior: a third-party class can't be
 * listed in someone else's {@code permits} clause. This interface has no implementation of its
 * own to protect and nothing here relies on exhaustiveness over "every kind of building" anymore —
 * capability interfaces ({@link SettlesEachTick}, {@link TransportNode}) are how code asks "can
 * you do X", not a closed type switch. Any class, anywhere, can {@code implement Building} now.
 * {@code BuildingFactory.create}/{@code restore} dispatch through registered data ({@link
 * BuildingPrototype}) rather than a closed {@code switch}, so a genuinely new prototype — with no
 * {@link BuildingType} of its own at all — is buildable/restorable too; what remains closed is the
 * hotbar/UI (still a {@link BuildingType#values()} loop) and {@link BuildingType} itself as an
 * enum, neither a consequence of this interface no longer being {@code sealed}.
 *
 * <p><b>{@code speedLevel} used to be a decorator ({@code SpeedModule}), not anymore.</b> A
 * decorator that wraps an unknown number of times conflicts with capability interfaces: every
 * {@code instanceof SomeCapability} check would have to unwrap first, and that cost grows with
 * every capability the engine adds. {@code speedLevel} is now plain data — a field on whichever
 * archetypes ({@link Miner}, {@link Chest}, {@link Furnace}, {@link Lab}) actually accept the
 * effect (see {@code BuildingPrototype#acceptsSpeedEffects}) — read directly by {@link
 * #speedLevel()}, no unwrapping required anywhere.
 */
public interface Building {

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
    default boolean accept(TickContext world, ItemType item) {
        return false;
    }

    /** The item this building is physically holding "in transit" right now, if any. */
    default Optional<ItemType> heldItem() {
        return Optional.empty();
    }

    /** How many times {@code UpgradeSpeedAction} has upgraded this building — {@code 0} if never. */
    default int speedLevel() {
        return 0;
    }

    /**
     * A copy of this building with {@link #speedLevel()} set to {@code newSpeedLevel}, preserving
     * every other bit of state — how {@code UpgradeSpeedAction} increments the level (and {@code
     * undo} restores the previous instance wholesale, so this method's return value is only ever
     * needed going forward, never backward). Default: return {@code this} unchanged — the buildings
     * that don't track a {@code speedLevel} at all (see {@code BuildingPrototype#acceptsSpeedEffects})
     * have nothing to change and {@code UpgradeSpeedAction} never calls this on them anyway (the
     * prototype check refuses them first).
     */
    default Building withSpeedLevel(int newSpeedLevel) {
        return this;
    }

    /**
     * How many cells wide this building physically occupies, anchored at the cell {@code
     * com.rustorio.domain.world.World} stores it under (its top-left corner) — {@code 1} for
     * every building except {@link Furnace}'s {@code ASSEMBLER} kind (X-03, DEV_TASKS.md). {@code
     * World} reserves every cell in {@code [x, x + footprintWidth)} × {@code [y, y +
     * footprintHeight)} on placement and frees the same rectangle on demolition — see {@code
     * World.place}/{@code World.removeBuilding}.
     */
    default int footprintWidth() {
        return 1;
    }

    /** The height counterpart to {@link #footprintWidth} — see its javadoc. */
    default int footprintHeight() {
        return 1;
    }

    /** How this building looks right now — sprite plus an optional numeric badge. */
    Appearance appearance();

    /** This building's kind, as shown in the hotbar. */
    BuildingType type();

    /**
     * Capture this building's entire internal state for persistence (Memento pattern) — this
     * building's own state record (e.g. a {@code MinerState}), erased to {@code Object} here the
     * same way {@link BuildingPrototype#behavior()} already erases its own return type. Paired
     * with a {@link Codec} registered on this building's governing {@link BuildingPrototype} (see
     * {@code VanillaBuildings}), which knows how to turn the returned record into a plain
     * JSON-shaped value and back.
     */
    Object state();

    /**
     * This building's own governing {@link BuildingPrototype}'s id — what a save writes as
     * {@code proto}, what {@code BuildingFactory.restore} resolves against on load, and what a
     * {@code BuildingPlacedEvent} names.
     *
     * <p>Deliberately abstract, with no default. It used to default to the vanilla id for this
     * building's own {@link #type()}, which is wrong the moment a building is built from a
     * prototype other than its kind's vanilla one — exactly what a JSON-defined mod building
     * borrowing an archetype is. Every archetype except {@link Furnace} inherited that default, so
     * a modded chest, belt, miner, splitter, filter, inserter, tunnel or lab was saved under the
     * VANILLA id and came back as the vanilla building, losing its own cost, texture and tuning.
     * A building that cannot name the prototype it was built from has no identity worth writing to
     * a save, so the interface asks for one rather than guessing.
     */
    ContentId prototypeId();

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

    /**
     * A copy of this building, rotated one clockwise step, preserving everything else about its
     * state (held item, buffers, timers) — or {@code Optional.empty()} if this building has no
     * direction to rotate at all (a {@link Chest}, a {@link Lab}). Returns a NEW instance rather
     * than mutating this one: every directional building's {@code direction} field is set once at
     * construction and read by other code that assumes it never changes mid-lifetime (e.g. {@link
     * Belt}'s segment membership) — {@code com.rustorio.domain.action.RotateAction} is the one
     * caller, and it always goes through {@code World.removeBuilding}/{@code restoreBuilding}
     * anyway, exactly like {@code UpgradeSpeedAction} does (D-01, DEV_TASKS.md).
     */
    default Optional<Building> rotatedClockwise() {
        return Optional.empty();
    }
}

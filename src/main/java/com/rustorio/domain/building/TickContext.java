package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.ResearchView;
import java.util.Optional;

/**
 * The narrow port between a building's {@link Building#tick}/{@link Building#accept} and the
 * world it lives in — exactly the operations a building ever actually calls, and no more. Replaces a
 * direct dependency on the {@code World} class (declared in the sibling package one level up):
 * every building used to import it directly, while {@code World} imports back from this package
 * ({@link Belt}, {@link Building}, {@link BuildingFactory}) — an import cycle between the two
 * packages that made them impossible to compile separately, impossible to check for acyclicity,
 * and forced {@link Belt#attachToNeighbors}/{@code leaveSegment} to be {@code public} just so
 * {@code World} could reach across the package boundary. See P3-01, BUG_FIX_PROGRESS.md.
 *
 * <p>{@code World} is the only implementation and remains the concrete type everywhere outside
 * {@code domain.building} — this interface exists purely so buildings can depend on "the few
 * things I need from the world" instead of the whole aggregate root. Deliberately not "the
 * six things" or any other count: this javadoc named a number for a long time, and the number
 * was wrong every time the interface changed.
 */
public interface TickContext {

    /** Hand an item to ONE specific neighbor, addressed by direction — a belt's forward push. */
    boolean offerForward(int x, int y, ItemType item);

    /** Look at a cell without offering or consuming anything. */
    Optional<Building> peek(int x, int y);

    /** Read-only view of research progress and unlocked technologies. */
    ResearchView research();

    /** Tell every production listener an item was produced. */
    void notifyProduced(ItemType item);

    /** Add research points — the one door a {@link Lab} batch mutates research through. */
    void addResearchPoints(int amount);

    /**
     * A handle onto the fluid network touching this building on one side — how a pump, a boiler or
     * any modded fluid machine moves fluid without knowing anything about the world's cell map.
     *
     * <p>Addressed by side rather than by absolute coordinates so that a machine with two different
     * connections (a boiler: water in on one side, steam out on another) can name them apart. Empty
     * when that neighbor is not a fluid tile at all, which is the ordinary case for three sides of
     * most machines — not an error.
     */
    Optional<FluidPort> fluidPort(int x, int y, Direction side);

    /**
     * Take {@code amount} of power out of whatever grid covers {@code (x, y)} this tick — how a
     * machine that declared a demand pays for its own tick.
     *
     * <p>All or nothing, and {@code false} when no pole covers this cell at all: a machine either
     * runs a whole tick or reports {@code NO_POWER}. Only a building whose prototype declares a
     * demand ever calls this, which is what keeps electricity opt-in — everything built before there
     * was any never asks, and so never notices.
     */
    boolean drawPower(int x, int y, long amount);

    /**
     * The capability registered under {@code key} in this world, or empty if nothing registered
     * one — how a building reaches anything that is neither content nor a cell of the map (an
     * outbound HTTP client, a clock) without this interface having to name it.
     *
     * <p>This method replaced three ({@code requestFetch}/{@code pollFetch}/{@code
     * lastResponseBody}) that one mod's web-fetching archetype needed and that nothing else in the
     * engine ever called: a single mod's vocabulary had reached the narrowest public contract the
     * engine has, and the next such archetype would have had to widen it again. Now it cannot —
     * see {@link ServiceKey}.
     *
     * <p>Empty is ORDINARY, not an error: a world built without a given service (a headless test,
     * a dev tool, a game whose provider mod isn't installed) must let the building degrade the way
     * {@link Miner} does on a cell with no ore, not throw.
     *
     * <p><b>Resolve once, not per tick.</b> This is a map lookup; a building that needs a service
     * every tick asks on its FIRST tick and keeps the answer in a field, the same way an archetype
     * reads its {@link Traits} at construction rather than re-reading them per tick. Calling this
     * every tick would be a map lookup on the hot path, which this project's own tick budget
     * forbids.
     */
    <T> Optional<T> service(ServiceKey<T> key);
}

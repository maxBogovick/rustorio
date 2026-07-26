package com.rustorio.domain.building;

import com.rustorio.domain.Item;
import com.rustorio.domain.ResearchView;
import java.util.Optional;

/**
 * The narrow port between a building's {@link Building#tick}/{@link Building#accept} and the
 * world it lives in — exactly the six operations a building ever actually calls. Replaces a
 * direct dependency on the {@code World} class (declared in the sibling package one level up):
 * every building used to import it directly, while {@code World} imports back from this package
 * ({@link Belt}, {@link Building}, {@link BuildingFactory}) — an import cycle between the two
 * packages that made them impossible to compile separately, impossible to check for acyclicity,
 * and forced {@link Belt#attachToNeighbors}/{@code leaveSegment} to be {@code public} just so
 * {@code World} could reach across the package boundary. See P3-01, BUG_FIX_PROGRESS.md.
 *
 * <p>{@code World} is the only implementation and remains the concrete type everywhere outside
 * {@code domain.building} — this interface exists purely so buildings can depend on "the six
 * things I need from the world" instead of the whole aggregate root.
 */
public interface TickContext {

    /** Hand an item to ONE specific neighbor, addressed by direction — a belt's forward push. */
    boolean offerForward(int x, int y, Item item);

    /** Try to hand an item to any of the four neighbors — a miner's "wherever fits." */
    boolean tryDeliverToNeighbor(int x, int y, Item item);

    /** Look at a cell without offering or consuming anything. */
    Optional<Building> peek(int x, int y);

    /** Read-only view of research progress and unlocked technologies. */
    ResearchView research();

    /** Tell every production listener an item was produced. */
    void notifyProduced(Item item);

    /** Add research points — the one door a {@link Lab} batch mutates research through. */
    void addResearchPoints(int amount);
}

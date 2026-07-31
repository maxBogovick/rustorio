package com.rustorio.domain.building;

/**
 * How a {@link BuildingPrototype} turns a decoded state (whatever its own {@link Codec#decode}
 * produced from a save) back into a live {@link Building} — the restoring counterpart to {@link
 * BehaviorFactory}.
 *
 * <p>{@code BuildingFactory.restore} resolves the governing prototype directly from the save's own
 * explicit {@code prototypeId} envelope, decodes the raw state through that prototype's {@link
 * Codec}, then calls this method — so casting {@code decodedState} to the exact expected record
 * type (e.g. a {@code MinerState}) inside an implementation of this method is safe by
 * construction, not a defensive check: nothing else could have produced that decoded value in the
 * first place. {@code speedLevel}, for the archetypes that track it, lives as a plain field of the
 * decoded state record itself — not a separate parameter here anymore.
 */
@FunctionalInterface
public interface RestoreFactory {

    /** Rebuild {@code self}'s archetype from {@code decodedState} — the exact record type {@code self}'s own {@link Codec} decodes. */
    Building restore(BuildingPrototype self, Object decodedState, BuildingFactory factory);
}

package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import org.jspecify.annotations.Nullable;

/**
 * {@link Pipe}'s own captured state: this ONE tile's share of its network's volume, and which fluid
 * that is. The network itself is never written to a save — it is rebuilt from the tiles' geometry
 * on load, exactly the way belt segments already are — so the tile is the unit of persistence, the
 * same role {@link BeltState}'s {@code held} plays for a belt.
 *
 * <p>The fluid is a bare {@link ContentId}, not a resolved {@code FluidType}, unlike every other
 * archetype's state, which holds resolved {@code ItemType}s. A {@link Codec} is handed the ITEM
 * registry and nothing else, and widening that signature would change an interface every archetype
 * and every code mod already implements — for one field. Resolving the id instead happens one step
 * later, in this prototype's own {@code RestoreFactory}, which does have the whole {@code
 * BuildingFactory} (and therefore its fluid registry) in hand.
 *
 * <p>{@code null} fluid means an empty tile, and then {@code amount} is {@code 0} — the two are
 * always written together.
 */
public record PipeState(@Nullable ContentId fluid, long amount) {
}

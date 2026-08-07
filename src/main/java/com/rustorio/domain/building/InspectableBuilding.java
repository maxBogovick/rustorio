package com.rustorio.domain.building;

import java.util.List;

/**
 * A building with something to say about itself in the inspection panel beyond the facts every
 * building shares (its label, status, held item). The read-only counterpart to {@link
 * EditableBuilding}, and the same bargain: implementing this interface is ALL a new archetype has
 * to do to appear correctly in the panel — the renderer gains no branch, and a mod that ships its
 * own archetype needs no change in {@code com.graphics} at all.
 *
 * <p>Before this, {@code com.graphics.render.InspectionPanelLayout} decided what to show with a
 * chain of {@code instanceof} over concrete building classes. That chain is why a mod's building
 * could only ever be described by editing the engine's own renderer, and why one mod's archetype
 * briefly put a JSON parser and a response-body line-wrapper into the rendering layer.
 *
 * <p>{@link TickContext} rather than {@code World}: this is the same narrow door a building
 * already talks to its world through during {@link Building#tick}, so an implementation gains no
 * reach it did not already have — and the domain keeps its rule that nothing here accepts a
 * rendering type in a signature.
 *
 * <p><b>Called on every frame the panel is open</b>, not once when it opens: a chest's contents
 * and a furnace's buffers change while the player watches, and a panel that froze at open time
 * would be lying within a tick. Implementations must therefore stay cheap — read fields, format
 * them, return. Anything genuinely expensive (parsing, scanning, allocating per call) belongs
 * wherever the underlying data CHANGES, not here; one archetype learned this by parsing an 8 KB
 * JSON body sixty times a second for as long as its panel stayed open.
 *
 * <p>Returned lines are semantic, not laid out: a line longer than the panel is wrapped by the
 * panel, which is the only party that knows how wide it is.
 */
public interface InspectableBuilding {

    /** Extra lines describing this building at {@code (x, y)}, in display order — appended after the shared facts. Empty is legal and means "nothing to add right now". */
    List<String> inspectionDetails(TickContext world, int x, int y);
}

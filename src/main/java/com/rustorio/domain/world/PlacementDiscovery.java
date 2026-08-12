package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Which building prototypes have ever been placed in this world — survives demolition, keyed the
 * same way as {@link com.rustorio.domain.building.VisibilityRule.Placed} checks. {@link
 * LinkedHashSet} keeps snapshot iteration stable between runs.
 */
public final class PlacementDiscovery {

    private final LinkedHashSet<ContentId> everPlaced = new LinkedHashSet<>();

    public void record(ContentId prototypeId) {
        everPlaced.add(prototypeId);
    }

    public Set<ContentId> asSet() {
        return Set.copyOf(everPlaced);
    }

    public void clear() {
        everPlaced.clear();
    }

    public void restore(List<ContentId> ids) {
        everPlaced.clear();
        everPlaced.addAll(ids);
    }

    public Snapshot snapshot() {
        return new Snapshot(List.copyOf(everPlaced));
    }

    public record Snapshot(List<ContentId> everPlaced) {
    }
}

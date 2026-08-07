package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.BuildingPrototype;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What to CALL each building on screen when two of them are called the same thing.
 *
 * <p>{@code rustorio:boiler} and {@code waterworks:boiler} both label themselves "Boiler", and
 * {@code rustorio:pipe} and {@code waterworks:pipe} both "Pipe". On screen they were two identical
 * entries with different behaviour, and no amount of squinting told them apart — a mod cannot know
 * what another mod named its buildings, so this collides by construction the moment two mods cover
 * the same ground.
 *
 * <p>The rule is minimal on purpose: a label that is unique stays exactly as its author wrote it,
 * and only a colliding one gains its namespace — "Boiler" and "Boiler (waterworks)". Namespacing
 * everything unconditionally would make a stock game read like a debug dump.
 *
 * <p>Computed ONCE per screen, from a registry that is frozen for that screen's lifetime, never per
 * frame: this walks every prototype twice and allocates, and the frame budget in this package's own
 * rules forbids exactly that.
 */
public final class DisplayLabels {

    private final Map<ContentId, String> byId;

    private DisplayLabels(Map<ContentId, String> byId) {
        this.byId = byId;
    }

    /**
     * Builds the table for {@code all}. Iteration order follows {@code all}, so the result is as
     * deterministic as the registry that produced it — a {@link LinkedHashMap} rather than a
     * {@code HashMap} for the reason this project keeps a rule about.
     */
    public static DisplayLabels of(List<BuildingPrototype> all) {
        Map<String, Integer> timesUsed = new HashMap<>();
        for (BuildingPrototype prototype : all) {
            timesUsed.merge(prototype.label(), 1, Integer::sum);
        }
        Map<ContentId, String> byId = new LinkedHashMap<>();
        for (BuildingPrototype prototype : all) {
            String label = prototype.label();
            boolean collides = timesUsed.getOrDefault(label, 0) > 1;
            byId.put(prototype.id(), collides ? label + " (" + prototype.id().namespace() + ")" : label);
        }
        return new DisplayLabels(byId);
    }

    /** What to draw for {@code prototypeId} — its own label, disambiguated only if it had to be. Falls back to the id for anything not in the table. */
    public String of(ContentId prototypeId) {
        return byId.getOrDefault(prototypeId, prototypeId.toString());
    }
}

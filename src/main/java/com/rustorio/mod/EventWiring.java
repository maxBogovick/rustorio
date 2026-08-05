package com.rustorio.mod;

import com.rustorio.api.mod.BuildingPlaceEvent;
import com.rustorio.api.mod.BuildingPlacedEvent;
import com.rustorio.api.mod.EventBus;
import com.rustorio.api.mod.ItemProducedEvent;
import com.rustorio.api.mod.ResearchCompleteEvent;
import com.rustorio.api.mod.TickEvent;
import com.rustorio.api.mod.WorldInitEvent;
import com.rustorio.domain.world.World;

/**
 * Bridges {@link World}'s own domain-level Observer listeners ({@code ProductionListener}, {@code
 * BuildingPlacedListener}, {@code TickListener}, {@code ResearchCompleteListener}) to a mod-facing
 * {@link EventBus} — the domain itself never imports {@code com.rustorio.api.mod} (see those
 * listener interfaces' own javadoc for why), so this is the one place that translates between the
 * two vocabularies.
 */
public final class EventWiring {

    private EventWiring() {
    }

    /**
     * Publishes {@link WorldInitEvent} once, then wires {@code world}'s other notification points —
     * and its one veto point — to publish their own mod-facing event as they fire. Call once, right after
     * constructing {@code world} — a {@link WorldInitEvent} published later would be a lie about
     * when the world actually started existing.
     */
    public static void attach(World world, EventBus events) {
        events.publish(new WorldInitEvent(world.width(), world.height()));
        world.addPlacementVeto((prototypeId, x, y) -> {
            BuildingPlaceEvent attempt = new BuildingPlaceEvent(prototypeId, x, y);
            events.publish(attempt);
            return !attempt.isCancelled();
        });
        world.addBuildingPlacedListener((prototypeId, x, y) -> events.publish(new BuildingPlacedEvent(prototypeId, x, y)));
        world.addTickListener(tick -> events.publish(new TickEvent(tick)));
        world.addResearchCompleteListener(tech -> events.publish(new ResearchCompleteEvent(tech)));
        world.addProductionListener((tick, item) -> events.publish(new ItemProducedEvent(item)));
    }
}

package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.mod.BuildingPlacedEvent;
import com.rustorio.api.mod.ItemProducedEvent;
import com.rustorio.api.mod.ResearchCompleteEvent;
import com.rustorio.api.mod.TickEvent;
import com.rustorio.api.mod.WorldInitEvent;
import com.rustorio.domain.BuildingType;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Proves the five mod-facing events actually fire off REAL {@link World} actions — not a mocked
 * listener, the genuine {@code place}/{@code tick}/{@code notifyProduced}/{@code tryUnlockTech}
 * paths every player action and every building already goes through.
 */
class EventWiringTest {

    @Test
    void worldInitEventPublishesImmediatelyWithTheWorldsRealDimensions() {
        World world = new World(4, 5);
        SimpleEventBus events = new SimpleEventBus();
        List<WorldInitEvent> received = new ArrayList<>();
        events.subscribe(WorldInitEvent.class, received::add);

        EventWiring.attach(world, events);

        assertEquals(List.of(new WorldInitEvent(4, 5)), received);
    }

    @Test
    void buildingPlacedEventFiresOnARealSuccessfulPlacement() {
        World world = new World(4, 4);
        SimpleEventBus events = new SimpleEventBus();
        EventWiring.attach(world, events);
        List<BuildingPlacedEvent> received = new ArrayList<>();
        events.subscribe(BuildingPlacedEvent.class, received::add);

        boolean placed = world.place(BuildingType.CHEST, 1, 0);

        assertTrue(placed);
        assertEquals(1, received.size());
        assertEquals(1, received.get(0).x());
        assertEquals(0, received.get(0).y());
        assertEquals("rustorio:chest", received.get(0).prototypeId().toString());
    }

    @Test
    void buildingPlacedEventDoesNotFireOnRestore() {
        World world = new World(4, 4);
        SimpleEventBus events = new SimpleEventBus();
        EventWiring.attach(world, events);
        List<BuildingPlacedEvent> received = new ArrayList<>();
        events.subscribe(BuildingPlacedEvent.class, received::add);

        world.restoreBuilding(2, 2, new Chest());

        assertTrue(received.isEmpty(), "restoring a building is not the same event as a player placing one");
    }

    @Test
    void tickEventFiresOnceAtTheEndOfEachRealTick() {
        World world = new World(4, 4);
        SimpleEventBus events = new SimpleEventBus();
        EventWiring.attach(world, events);
        List<TickEvent> received = new ArrayList<>();
        events.subscribe(TickEvent.class, received::add);

        world.tick();
        world.tick();

        assertEquals(List.of(new TickEvent(1), new TickEvent(2)), received);
    }

    @Test
    void itemProducedEventFiresOnTheRealNotifyProducedPath() {
        World world = new World(4, 4);
        SimpleEventBus events = new SimpleEventBus();
        EventWiring.attach(world, events);
        List<ItemProducedEvent> received = new ArrayList<>();
        events.subscribe(ItemProducedEvent.class, received::add);

        world.notifyProduced(VanillaItems.IRON_PLATE);

        assertEquals(List.of(new ItemProducedEvent(VanillaItems.IRON_PLATE)), received);
    }

    @Test
    void researchCompleteEventFiresOnlyOnAGenuineUnlock() {
        World world = new World(4, 4);
        SimpleEventBus events = new SimpleEventBus();
        EventWiring.attach(world, events);
        List<ResearchCompleteEvent> received = new ArrayList<>();
        events.subscribe(ResearchCompleteEvent.class, received::add);

        boolean refused = world.tryUnlockTech(VanillaTechs.FAST_MINING);
        assertFalse(refused, "no research points banked yet — this attempt must be refused");
        assertTrue(received.isEmpty(), "a refused unlock attempt must not publish an event");

        world.addResearchPoints(costOf(VanillaTechs.FAST_MINING));
        boolean unlocked = world.tryUnlockTech(VanillaTechs.FAST_MINING);

        assertTrue(unlocked);
        assertEquals(List.of(new ResearchCompleteEvent(VanillaTechs.FAST_MINING)), received);
    }

    /** A vanilla technology's price, read from the registry the game itself researches through. */
    private static int costOf(com.rustorio.api.content.ContentId tech) {
        return VanillaTechs.frozen().get(tech).cost();
    }
}

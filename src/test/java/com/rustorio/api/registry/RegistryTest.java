package com.rustorio.api.registry;

import com.rustorio.api.content.ContentId;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Registry lifecycle: register/update before freeze, read-only after, deterministic rawId. */
class RegistryTest {

    private static final ContentId IRON_ORE = ContentId.of("rustorio:iron_ore");
    private static final ContentId COAL = ContentId.of("rustorio:coal");
    private static final ContentId COPPER_ORE = ContentId.of("rustorio:copper_ore");

    @Test
    void registeredContentIsReadableAfterFreeze() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");

        registry.freeze();

        assertEquals("Iron Ore", registry.get(IRON_ORE));
    }

    @Test
    void registerAfterFreezeFails() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.freeze();

        assertThrows(IllegalStateException.class, () -> registry.register(COAL, "Coal"));
    }

    @Test
    void updateAfterFreezeFails() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.freeze();

        assertThrows(IllegalStateException.class, () -> registry.update(IRON_ORE, v -> v + " (buffed)"));
    }

    @Test
    void updatingAnUnregisteredIdFailsWithAClearError() {
        Registry<String> registry = new Registry<>();

        NoSuchElementException thrown = assertThrows(NoSuchElementException.class,
                () -> registry.update(IRON_ORE, v -> v + "!"));
        assertTrue(thrown.getMessage().contains(IRON_ORE.toString()),
                "error message should name the id that was never registered: " + thrown.getMessage());
    }

    @Test
    void updateReplacesTheRegisteredValueAndLogsTheId() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");

        registry.update(IRON_ORE, v -> v + " (buffed)");
        registry.freeze();

        assertEquals("Iron Ore (buffed)", registry.get(IRON_ORE));
        assertEquals(List.of(IRON_ORE), registry.updateLog());
    }

    @Test
    void rawIdsAreZeroBasedAndContiguousInSortedOrder() {
        Registry<String> registry = new Registry<>();
        // Registered out of alphabetical order on purpose - freeze must sort regardless.
        registry.register(IRON_ORE, "Iron Ore");
        registry.register(COAL, "Coal");
        registry.register(COPPER_ORE, "Copper Ore");

        registry.freeze();

        assertEquals(0, registry.rawId(COAL), "rustorio:coal sorts first");
        assertEquals(1, registry.rawId(COPPER_ORE), "rustorio:copper_ore sorts second");
        assertEquals(2, registry.rawId(IRON_ORE), "rustorio:iron_ore sorts third");
        assertEquals(3, registry.size());
    }

    @Test
    void rawIdAssignmentDoesNotDependOnRegistrationOrder() {
        Registry<String> inOrder = new Registry<>();
        inOrder.register(COAL, "Coal");
        inOrder.register(COPPER_ORE, "Copper Ore");
        inOrder.register(IRON_ORE, "Iron Ore");
        inOrder.freeze();

        Registry<String> reversed = new Registry<>();
        reversed.register(IRON_ORE, "Iron Ore");
        reversed.register(COPPER_ORE, "Copper Ore");
        reversed.register(COAL, "Coal");
        reversed.freeze();

        assertEquals(inOrder.rawId(IRON_ORE), reversed.rawId(IRON_ORE));
        assertEquals(inOrder.rawId(COAL), reversed.rawId(COAL));
        assertEquals(inOrder.rawId(COPPER_ORE), reversed.rawId(COPPER_ORE));
    }

    @Test
    void getByRawIdMatchesGetById() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.register(COAL, "Coal");
        registry.freeze();

        int rawId = registry.rawId(IRON_ORE);

        assertEquals(registry.get(IRON_ORE), registry.get(rawId));
    }

    @Test
    void iterateReturnsValuesInRawIdOrder() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.register(COAL, "Coal");
        registry.register(COPPER_ORE, "Copper Ore");
        registry.freeze();

        assertEquals(List.of("Coal", "Copper Ore", "Iron Ore"), registry.iterate(),
                "iterate() must follow rawId order, i.e. sorted ContentId order");
    }

    @Test
    void readingBeforeFreezeIsForbidden() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");

        assertThrows(IllegalStateException.class, () -> registry.get(IRON_ORE));
        assertThrows(IllegalStateException.class, () -> registry.get(0));
        assertThrows(IllegalStateException.class, () -> registry.rawId(IRON_ORE));
        assertThrows(IllegalStateException.class, registry::size);
        assertThrows(IllegalStateException.class, registry::iterate);
        assertThrows(IllegalStateException.class, () -> registry.getOrUnknown(IRON_ORE));
    }

    @Test
    void freezingTwiceFails() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.freeze();

        assertThrows(IllegalStateException.class, registry::freeze);
    }

    @Test
    void getOrUnknownReturnsEmptyForContentThatWasNeverRegistered() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.freeze();

        assertTrue(registry.getOrUnknown(COAL).isEmpty(),
                "unregistered id must come back empty, not throw - a save can reference a removed mod's content");
        assertEquals("Iron Ore", registry.getOrUnknown(IRON_ORE).orElseThrow());
    }

    @Test
    void updateLogIsEmptyWhenNoUpdateWasCalled() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");

        assertFalse(registry.updateLog().contains(IRON_ORE));
        assertTrue(registry.updateLog().isEmpty());
    }

    @Test
    void peekWorksBeforeFreezeUnlikeGetOrUnknown() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");

        assertEquals("Iron Ore", registry.peek(IRON_ORE).orElseThrow(),
                "peek must answer while registration is still open, for a mod resolving another mod's content mid-round");
        assertTrue(registry.peek(COAL).isEmpty());
    }

    @Test
    void peekStillWorksAfterFreeze() {
        Registry<String> registry = new Registry<>();
        registry.register(IRON_ORE, "Iron Ore");
        registry.freeze();

        assertEquals("Iron Ore", registry.peek(IRON_ORE).orElseThrow());
        assertTrue(registry.peek(COAL).isEmpty());
    }
}

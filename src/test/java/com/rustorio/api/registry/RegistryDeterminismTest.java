package com.rustorio.api.registry;

import com.rustorio.api.content.ContentId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** rawId assignment and iteration order don't depend on registration order; adding a new id shifts neighbors' rawId, which is expected. */
class RegistryDeterminismTest {

    private static final List<ContentId> IDS = List.of(
            ContentId.of("rustorio:coal"),
            ContentId.of("rustorio:copper_ore"),
            ContentId.of("rustorio:engine"),
            ContentId.of("rustorio:gear"),
            ContentId.of("rustorio:iron_ore"),
            ContentId.of("rustorio:iron_plate"));

    @Test
    void rawIdAndIterationOrderAreIdenticalRegardlessOfRegistrationOrder() {
        Registry<ContentId> forwardOrder = registryOf(IDS);

        List<ContentId> reversed = new ArrayList<>(IDS);
        Collections.reverse(reversed);
        Registry<ContentId> reverseOrder = registryOf(reversed);

        List<ContentId> shuffled = new ArrayList<>(IDS);
        Collections.shuffle(shuffled, new Random(20260729L));
        Registry<ContentId> shuffledOrder = registryOf(shuffled);

        for (ContentId id : IDS) {
            assertEquals(forwardOrder.rawId(id), reverseOrder.rawId(id),
                    "reverse-order registration must assign the same rawId to " + id);
            assertEquals(forwardOrder.rawId(id), shuffledOrder.rawId(id),
                    "shuffled-order registration must assign the same rawId to " + id);
        }
        assertEquals(forwardOrder.iterate(), reverseOrder.iterate());
        assertEquals(forwardOrder.iterate(), shuffledOrder.iterate());
    }

    /**
     * Adding a new id shifts the rawId of every id that sorts after it — this is expected, not a
     * bug: rawId is an in-memory index reassigned from scratch on every freeze(), never written to
     * a save (that's what ContentId itself is for). If some future change makes rawId stable
     * across different registry contents, something is probably persisting it where it shouldn't.
     */
    @Test
    void addingANewIdShiftsSubsequentRawIds() {
        Registry<ContentId> before = registryOf(List.of(
                ContentId.of("rustorio:coal"),
                ContentId.of("rustorio:engine"),
                ContentId.of("rustorio:iron_ore")));

        Registry<ContentId> after = registryOf(List.of(
                ContentId.of("rustorio:coal"),
                ContentId.of("rustorio:copper_ore"),
                ContentId.of("rustorio:engine"),
                ContentId.of("rustorio:iron_ore")));

        assertEquals(0, before.rawId(ContentId.of("rustorio:coal")));
        assertEquals(1, before.rawId(ContentId.of("rustorio:engine")));
        assertEquals(2, before.rawId(ContentId.of("rustorio:iron_ore")));

        assertEquals(0, after.rawId(ContentId.of("rustorio:coal")), "coal still sorts first, unaffected");
        assertEquals(1, after.rawId(ContentId.of("rustorio:copper_ore")), "new id takes the slot right after coal");
        assertEquals(2, after.rawId(ContentId.of("rustorio:engine")), "engine shifted from 1 to 2");
        assertEquals(3, after.rawId(ContentId.of("rustorio:iron_ore")), "iron_ore shifted from 2 to 3");
    }

    private static Registry<ContentId> registryOf(List<ContentId> ids) {
        Registry<ContentId> registry = new Registry<>();
        for (ContentId id : ids) {
            registry.register(id, id);
        }
        registry.freeze();
        return registry;
    }
}

package com.rustorio.api.content;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Namespaced id: valid ids, rejected malformed ids, round-trip parsing, and sort order. */
class ContentIdTest {

    @Test
    void validIdSplitsIntoNamespaceAndPath() {
        ContentId id = ContentId.of("rustorio:iron_ore");

        assertEquals("rustorio", id.namespace());
        assertEquals("iron_ore", id.path());
    }

    @Test
    void toStringRejoinsNamespaceAndPath() {
        ContentId id = new ContentId("rustorio", "iron_ore");

        assertEquals("rustorio:iron_ore", id.toString());
    }

    @Test
    void ofToStringRoundTripsToAnEqualId() {
        ContentId original = ContentId.of("some_mod:complex_gearbox_v2");

        ContentId roundTripped = ContentId.of(original.toString());

        assertEquals(original, roundTripped);
    }

    @Test
    void emptyNamespaceIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContentId("", "iron_ore"));
    }

    @Test
    void emptyPathIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContentId("rustorio", ""));
    }

    @Test
    void uppercaseIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContentId("Rustorio", "iron_ore"));
        assertThrows(IllegalArgumentException.class, () -> new ContentId("rustorio", "Iron_Ore"));
    }

    @Test
    void nonAsciiIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ContentId("rustorio", "минерал"));
    }

    @Test
    void stringWithoutAColonIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentId.of("rustorio_iron_ore"));
    }

    @Test
    void stringWithTwoColonsIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> ContentId.of("rustorio:iron:ore"));
    }

    @Test
    void sortOrderMatchesPlainStringOrderOfNamespaceColonPath() {
        ContentId zebra = ContentId.of("rustorio:zebra_ore");
        ContentId apple = ContentId.of("modpack:apple_ore");
        ContentId iron = ContentId.of("rustorio:iron_ore");

        List<ContentId> sorted = new ArrayList<>(List.of(zebra, apple, iron));
        Collections.sort(sorted);

        assertEquals(List.of(apple, iron, zebra), sorted,
                "sort order must be plain String order of \"namespace:path\" — modpack:apple_ore < "
                        + "rustorio:iron_ore < rustorio:zebra_ore — because Registry.freeze() will "
                        + "rely on exactly this order for deterministic rawId assignment");
    }
}

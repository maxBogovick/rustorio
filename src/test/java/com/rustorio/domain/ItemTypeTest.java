package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link ItemType}: identity is {@link ContentId} alone, not the record's default every-field
 * equality — two prototypes sharing an id (e.g. before/after a {@code Registry.update()} call)
 * must count as the same key everywhere, in a {@code HashMap} exactly as much as in a {@code
 * TreeMap}, or one of the two silently drops/splits a stored count.
 */
class ItemTypeTest {

    private static final ContentId IRON_ORE = ContentId.of("rustorio:iron_ore");

    private static ItemType ironOre(String label, int colorRgb) {
        return new ItemType(IRON_ORE, label, false, colorRgb, ItemShape.CIRCLE);
    }

    @Test
    void twoInstancesWithTheSameIdAreEqualEvenWithDifferentMetadata() {
        ItemType before = ironOre("Iron Ore", 0x111111);
        ItemType after = ironOre("Rusty Ore", 0x222222); // same id, everything else differs

        assertEquals(before, after, "same ContentId must mean the same item, regardless of label/color");
        assertEquals(before.hashCode(), after.hashCode(), "equal objects must share a hash code");
        assertEquals(0, before.compareTo(after), "compareTo must agree with equals (both id-based)");
    }

    @Test
    void twoInstancesWithDifferentIdsAreNeverEqual() {
        ItemType ironOre = ironOre("Iron Ore", 0x111111);
        ItemType coal = new ItemType(ContentId.of("rustorio:coal"), "Coal", false, 0x000000, ItemShape.SQUARE);

        assertNotEquals(ironOre, coal);
    }

    @Test
    void aHashMapTreatsPreAndPostUpdateInstancesAsTheSameKey() {
        Map<ItemType, Integer> contents = new HashMap<>();
        contents.put(ironOre("Iron Ore", 0x111111), 5);

        // Simulates a mod's Registry.update() swapping metadata for the same ContentId, then a
        // caller looking the item back up and crediting more of it — must land on the SAME slot,
        // not silently open a second one next to it (the actual failure mode this test guards).
        contents.merge(ironOre("Rusty Ore", 0x222222), 3, Integer::sum);

        assertEquals(1, contents.size(), "must still be one slot, not two");
        assertEquals(8, contents.get(ironOre("whatever label", 0)));
    }

    @Test
    void aTreeMapTreatsPreAndPostUpdateInstancesAsTheSameKey() {
        Map<ItemType, Integer> contents = new TreeMap<>();
        contents.put(ironOre("Iron Ore", 0x111111), 5);

        contents.merge(ironOre("Rusty Ore", 0x222222), 3, Integer::sum);

        assertEquals(1, contents.size());
        assertEquals(8, contents.get(ironOre("whatever label", 0)));
    }

    @Test
    void constructionRejectsAnEmptyLabel() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> new ItemType(IRON_ORE, "", false, 0, ItemShape.CIRCLE));

        assertTrue(e.getMessage().contains(IRON_ORE.toString()),
                "the failure must name which item's registration was rejected");
    }

    /**
     * Regression test for code review finding S3: an accidental {@code "" + item} concatenation
     * used to print the record's default every-field dump instead of a readable name.
     */
    @Test
    void toStringReturnsJustTheLabelNotTheFullRecordDump() {
        ItemType gear = ironOre("Gear", 0xE6C33C);

        assertEquals("Gear", gear.toString());
    }
}

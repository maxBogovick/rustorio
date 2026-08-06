package com.graphics.render;

import com.rustorio.domain.Cell;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The overlay's colour choice is a pure function of a network's anchor: the same anchor always
 * yields the same slot (so a network never changes colour between frames or runs), the slot is
 * always in range, and distinct anchors do not all collapse onto one slot. Distinctness is only a
 * spot check, not a guarantee — see {@link NetworkTint#paletteIndex} on why determinism, not
 * uniqueness, is the property that matters.
 */
class NetworkTintTest {

    @Test
    void sameAnchorAlwaysLandsOnTheSameSlot() {
        assertEquals(NetworkTint.paletteIndex(new Cell(5, 7), 6),
                NetworkTint.paletteIndex(new Cell(5, 7), 6),
                "a network must not change colour when nothing about it changed");
    }

    @Test
    void theSlotIsAlwaysInRangeEvenForNegativeCoordinates() {
        for (int x = -50; x <= 50; x += 7) {
            for (int y = -50; y <= 50; y += 7) {
                int slot = NetworkTint.paletteIndex(new Cell(x, y), 6);
                assertTrue(slot >= 0 && slot < 6, "slot out of range at (" + x + ", " + y + "): " + slot);
            }
        }
    }

    @Test
    void differentAnchorsDoNotAllCollapseOntoOneSlot() {
        Set<Integer> slots = new HashSet<>();
        slots.add(NetworkTint.paletteIndex(new Cell(0, 0), 6));
        slots.add(NetworkTint.paletteIndex(new Cell(1, 0), 6));
        slots.add(NetworkTint.paletteIndex(new Cell(0, 1), 6));
        slots.add(NetworkTint.paletteIndex(new Cell(9, 4), 6));
        slots.add(NetworkTint.paletteIndex(new Cell(3, 12), 6));
        assertTrue(slots.size() >= 2,
                "a palette that maps every network to one colour would show no membership at all");
    }
}

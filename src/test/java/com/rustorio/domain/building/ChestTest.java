package com.rustorio.domain.building;

import com.rustorio.domain.Item;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@link Chest}: accumulates whatever it's handed, badge tracks the count — see P2-04. */
class ChestTest {

    @Test
    void emptyChestHasNoBadge() {
        Chest chest = new Chest();

        assertEquals(Sprite.CHEST, chest.appearance().sprite());
        assertFalse(chest.appearance().hasBadge(), "an empty chest must not draw a \"0\" badge");
    }

    @Test
    void filledChestShowsItsCount() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        chest.accept(world, Item.IRON_ORE);
        chest.accept(world, Item.IRON_ORE);

        assertTrue(chest.appearance().hasBadge());
        assertEquals(2, chest.appearance().badge());
    }

    @Test
    void chestAcceptsAnyItem() {
        World world = new World(4, 4);
        Chest chest = new Chest();

        for (Item item : Item.values()) {
            assertTrue(chest.accept(world, item), "a chest must accept every item kind, unsorted");
        }
        assertEquals(Item.values().length, chest.count());
    }
}

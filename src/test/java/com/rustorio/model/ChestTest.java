package com.rustorio.model;

import com.rustorio.core.Item;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Протокол передачи у ящика: принимает всё, считает, наружу не отдаёт. */
class ChestTest {

    @Test
    void acceptCountsItems() {
        Chest chest = new Chest();                   // дано: пустой ящик
        assertEquals(0, chest.items());
        chest.accept(Item.IRON_ORE);                 // когда: принял два предмета
        chest.accept(Item.IRON_PLATE);
        assertEquals(2, chest.items());              // тогда: счётчик равен двум
    }

    @Test
    void chestAcceptsAnythingAndNeverOutputs() {
        Chest chest = new Chest();
        assertTrue(chest.canAccept(Item.IRON_ORE));
        chest.accept(Item.IRON_ORE);
        assertTrue(chest.canAccept(Item.IRON_PLATE), "ящик бездонный: принимает всегда");
        assertTrue(chest.output().isEmpty(), "ящик — конечная точка: наружу не отдаёт");
    }
}

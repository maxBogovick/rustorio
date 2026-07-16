package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Tool;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Правила постановки и сноса зданий. Чистая модель — без окна и движка. */
class WorldPlaceRemoveTest {

    private final World world = World.generate(10, 8);

    @Test
    void placePutsBuildingOnTile() {
        world.place(3, 4, new Chest());
        assertNotNull(world.tile(3, 4).building());
    }

    @Test
    void placeAndRemoveOutsideWorldAreSafe() {
        world.place(-1, 0, new Chest());
        world.place(10, 99, new Chest());
        world.remove(-5, -5);
    }

    @Test
    void placingSameKindKeepsExistingBuilding() {
        Chest first = new Chest();
        world.place(3, 4, first);
        world.place(3, 4, new Chest());
        assertSame(first, world.tile(3, 4).building());
    }

    @Test
    void removeClearsTileAndIsIdempotent() {
        world.place(3, 4, new Chest());
        world.remove(3, 4);
        assertNull(world.tile(3, 4).building());
        world.remove(3, 4);
    }

    // ── L3: появился второй тип здания (бур) ──────────────────────────

    @Test
    void minerLearnsAboutOreWhenPlaced() {
        World w = World.generate(20, 20);
        // (6,5) — центр залежи в OreMap; (0,0) — пусто.
        w.place(6, 5, Building.create(Tool.MINER, Direction.EAST));
        w.place(0, 0, Building.create(Tool.MINER, Direction.EAST));
        assertTrue(((Miner) w.tile(6, 5).building()).onOre(), "на залежи — знает про руду");
        assertFalse(((Miner) w.tile(0, 0).building()).onOre(), "вне залежи — знает, что руды нет");
    }

    @Test
    void placeDoesNotOverwriteDifferentType() {
        world.place(3, 4, new Chest());
        world.place(3, 4, Building.create(Tool.MINER, Direction.EAST));
        assertInstanceOf(Chest.class, world.tile(3, 4).building());
    }
}

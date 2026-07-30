package com.rustorio.model;

import com.rustorio.World;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/** Правила постановки и сноса зданий. Чистая модель — без окна и движка. */
class WorldPlaceRemoveTest {

    private final World world = World.generate(10, 8);

    @Test
    void placePutsBuildingOnTile() {
        world.place(3, 4, new Chest());          // когда
        assertNotNull(world.tile(3, 4).building()); // тогда
    }

    @Test
    void placeAndRemoveOutsideWorldAreSafe() {
        world.place(-1, 0, new Chest());
        world.place(10, 99, new Chest());
        world.remove(-5, -5); // ничего из этого не должно упасть
    }

    @Test
    void placingSameKindKeepsExistingBuilding() {
        Chest first = new Chest();               // дано: ящик уже стоит
        world.place(3, 4, first);
        world.place(3, 4, new Chest());          // когда: ставим «такой же»
        // тогда: мир ОСТАВИЛ старый объект, а не заменил новым
        assertSame(first, world.tile(3, 4).building());
    }

    @Test
    void removeClearsTileAndIsIdempotent() {
        world.place(3, 4, new Chest());
        world.remove(3, 4);
        assertNull(world.tile(3, 4).building());
        world.remove(3, 4); // повторный снос пустого тоже безопасен
    }

    // TODO(L3): «place НЕ затирает здание ДРУГОГО типа» — сегодня непроверяемо:
    // в игре ровно один тип здания. Допишем, как только появится второй (бур).
}

package com.rustorio.sim;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты МАШИН: печь плавит руду по рецепту (L8). Сборщик и его составные
 * рецепты добавятся в L9 — у файла останется тот же хозяин: «машины с рецептами».
 */
class MachinesTest {

    private static void stepTimes(World world, int n) {
        Simulation simulation = new Simulation(world);
        TickContext ctx = new TickContext(Config.TICK);
        for (int i = 0; i < n; i++) {
            simulation.step(ctx);
        }
    }

    @Test
    @DisplayName("Печь плавит руду в пластину и отдаёт дальше")
    void furnaceSmeltsAndHandsOff() {
        World world = World.generate(20, 20);
        world.place(0, 0, Building.create(Tool.FURNACE, Direction.EAST));
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST));
        // Кладём руду в печь вручную (обычно это делает лента).
        ((Furnace) world.tile(0, 0).building()).accept(Item.IRON_ORE);

        stepTimes(world, 60);

        Chest chest = assertInstanceOf(Chest.class, world.tile(1, 0).building());
        assertTrue(chest.items() >= 1, "печь должна выдать пластину");
    }

    @Test
    @DisplayName("Печь не принимает сырьё, которого ей уже хватает")
    void furnaceStopsAcceptingWhatItAlreadyHas() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.FURNACE, Direction.EAST));
        Furnace furnace = (Furnace) world.tile(0, 0).building();

        assertTrue(furnace.canAccept(Item.IRON_ORE), "пустая печь принимает руду");
        furnace.accept(Item.IRON_ORE);
        assertFalse(furnace.canAccept(Item.IRON_ORE),
                "рецепту нужна одна руда — вторую печь брать не должна, иначе лента перед "
                        + "ней никогда не забьётся и игрок не увидит затор");
    }
}

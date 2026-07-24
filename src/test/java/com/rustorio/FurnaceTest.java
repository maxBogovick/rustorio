package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Печь + {@link ProcessTimer}: срок готовности порции и его тех-модификация FAST_SMELTING. */
class FurnaceTest {

    @Test
    void smeltsIronOreIntoPlateAfterRecipeTime() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restore(1, 0, chest); // держим ссылку на ящик напрямую, минуя place*

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT);
        assertTrue(furnace.accept(world, Item.IRON_ORE));

        for (int i = 0; i < Recipe.IRON.time() - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(0, chest.count(), "не должно быть готово раньше срока рецепта");
        }
        furnace.tick(world, 0, 0); // последний тик — порция готова и сразу уходит соседу
        assertEquals(1, chest.count());
    }

    @Test
    void fastSmeltingHalvesTimeStartingFromTheFirstBatch() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restore(1, 0, chest);
        // Открываем FAST_SMELTING ДО того, как печь впервые подстроится под рецепт — именно этот
        // момент раньше терял тех-эффект (первая порция варилась по recipe.time(), а не по
        // effectiveTime(world); см. javadoc Furnace.accept про ProcessTimer).
        world.research().addPoints(Tech.FAST_SMELTING.cost());

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT);
        assertTrue(furnace.accept(world, Item.IRON_ORE));

        int halvedTime = Math.max(1, Recipe.IRON.time() / 2);
        for (int i = 0; i < halvedTime - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        furnace.tick(world, 0, 0);
        assertEquals(1, chest.count(), "первая же порция обязана учитывать уже открытую технологию");
    }

    @Test
    void pressBuildsChassisFromEngineAndGear() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restore(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT);
        // ENGINE — первый вход рецепта CHASSIS и НИ для какого другого рецепта пресса входом не
        // служит, поэтому им и стоит кормить первым (см. javadoc Recipe.CHASSIS про однозначность).
        assertTrue(press.accept(world, Item.ENGINE));
        assertTrue(press.accept(world, Item.GEAR));

        for (int i = 0; i < Recipe.CHASSIS.time() - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count(), "не должно быть готово раньше срока рецепта");
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }

    @Test
    void furnaceAlloysIronAndBronzePlatesTogether() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restore(1, 0, chest);

        // ALLOY — первый рецепт ПЕЧИ (не пресса) с двумя входами; в отличие от CHASSIS порядок
        // подачи тут не важен вовсе (см. javadoc Recipe.ALLOY) — кормим пластинами в обратном
        // порядке относительно объявления в Recipe(input, input2, ...), чтобы проверить именно это.
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT);
        assertTrue(furnace.accept(world, Item.BRONZE_PLATE));
        assertTrue(furnace.accept(world, Item.IRON_PLATE));

        for (int i = 0; i < Recipe.ALLOY.time() - 1; i++) {
            furnace.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        furnace.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }

    @Test
    void plainOreSmeltingIsUnaffectedByTheNewTwoInputAlloyRecipe() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restore(1, 0, chest);

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT);
        assertTrue(furnace.accept(world, Item.IRON_ORE)); // тот же тип FURNACE, что и у ALLOY

        for (int i = 0; i < Recipe.IRON.time(); i++) {
            furnace.tick(world, 0, 0);
        }
        assertEquals(1, chest.count(), "IRON_ORE по-прежнему однозначно ведёт к IRON, а не к сплаву");
    }

    @Test
    void pressTurnsAlloyPlateIntoAlloyGear() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restore(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT);
        assertTrue(press.accept(world, Item.ALLOY_PLATE));

        for (int i = 0; i < Recipe.ALLOY_GEAR.time() - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }
}

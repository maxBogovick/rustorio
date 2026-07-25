package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Печь + {@link ProcessTimer}: срок готовности порции и его тех-модификация FAST_SMELTING. */
class FurnaceTest {

    private static final RecipeBook RECIPES = RecipeBook.standard();
    private static final int IRON_TIME = RECIPES.find(BuildingType.FURNACE, Item.IRON_ORE).orElseThrow().time();
    private static final int CHASSIS_TIME = RECIPES.find(BuildingType.PRESS, Item.ENGINE).orElseThrow().time();
    private static final int ALLOY_TIME = RECIPES.find(BuildingType.FURNACE, Item.IRON_PLATE).orElseThrow().time();
    private static final int ALLOY_GEAR_TIME =
            RECIPES.find(BuildingType.PRESS, Item.ALLOY_PLATE).orElseThrow().time();

    @Test
    void smeltsIronOreIntoPlateAfterRecipeTime() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest); // держим ссылку на ящик напрямую, минуя place*

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, Item.IRON_ORE));

        for (int i = 0; i < IRON_TIME - 1; i++) {
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
        world.restoreBuilding(1, 0, chest);
        // Открываем FAST_SMELTING ДО того, как печь впервые подстроится под рецепт — именно этот
        // момент раньше терял тех-эффект (см. javadoc Furnace.accept про ProcessTimer).
        world.addResearchPoints(Tech.FAST_SMELTING.cost());

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, Item.IRON_ORE));

        int halvedTime = Math.max(1, IRON_TIME / 2);
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
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        // ENGINE — первый вход рецепта CHASSIS и ни для какого другого рецепта пресса входом не
        // служит, поэтому им и стоит кормить первым.
        assertTrue(press.accept(world, Item.ENGINE));
        assertTrue(press.accept(world, Item.GEAR));

        for (int i = 0; i < CHASSIS_TIME - 1; i++) {
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
        world.restoreBuilding(1, 0, chest);

        // ALLOY — первый рецепт ПЕЧИ (не пресса) с двумя входами; порядок подачи не важен.
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, Item.BRONZE_PLATE));
        assertTrue(furnace.accept(world, Item.IRON_PLATE));

        for (int i = 0; i < ALLOY_TIME - 1; i++) {
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
        world.restoreBuilding(1, 0, chest);

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);
        assertTrue(furnace.accept(world, Item.IRON_ORE)); // тот же тип FURNACE, что и у ALLOY

        for (int i = 0; i < IRON_TIME; i++) {
            furnace.tick(world, 0, 0);
        }
        assertEquals(1, chest.count(), "IRON_ORE по-прежнему однозначно ведёт к IRON, а не к сплаву");
    }

    @Test
    void pressTurnsAlloyPlateIntoAlloyGear() {
        World world = new World(4, 4);
        Chest chest = new Chest();
        world.restoreBuilding(1, 0, chest);

        Furnace press = new Furnace(BuildingType.PRESS, Direction.RIGHT, RECIPES);
        assertTrue(press.accept(world, Item.ALLOY_PLATE));

        for (int i = 0; i < ALLOY_GEAR_TIME - 1; i++) {
            press.tick(world, 0, 0);
            assertEquals(0, chest.count());
        }
        press.tick(world, 0, 0);
        assertEquals(1, chest.count());
    }
}

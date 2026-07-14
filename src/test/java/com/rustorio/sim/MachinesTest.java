package com.rustorio.sim;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import com.rustorio.model.Assembler;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Handoff;
import com.rustorio.model.Lab;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.rustorio.sim.MovementTest.stepTimes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты МАШИН: печь плавит, сборщик собирает, рецепты работают (трек C).
 *
 * <p>Отделён от {@code MovementTest}, чтобы у файла был ровно один хозяин: рецепты,
 * лаборатория и технологии — работа трека C, передача предметов — трека B.
 */
class MachinesTest {

    @Test
    @DisplayName("Печь плавит руду в пластину и отдаёт дальше")
    void furnaceSmeltsAndHandsOff() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        world.place(0, 0, Building.create(Tool.FURNACE, Direction.EAST));
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST));
        // Кладём руду в печь вручную (обычно это делает лента).
        ((Furnace) world.tile(0, 0).building()).accept(Item.IRON_ORE);
        stepTimes(world, 60);

        Chest chest = assertInstanceOf(Chest.class, world.tile(1, 0).building());
        assertTrue(chest.items() >= 1, "печь должна выдать пластину");
    }

    @Test
    @DisplayName("Сборщик собирает шестерёнку из пластины")
    void assemblerBuildsGear() {
        World world = World.generate(Config.GRID_W, Config.GRID_H);
        world.place(0, 0, Building.create(Tool.ASSEMBLER, Direction.EAST));
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST));

        Building assembler = world.tile(0, 0).building();
        assertTrue(assembler.canAccept(Item.IRON_PLATE), "сборщик принимает пластину");
        assertFalse(assembler.canAccept(Item.IRON_ORE), "сборщик НЕ принимает руду");

        ((Assembler) assembler).accept(Item.IRON_PLATE);
        stepTimes(world, 60);

        Chest chest = assertInstanceOf(Chest.class, world.tile(1, 0).building());
        assertTrue(chest.items() >= 1, "сборщик должен выдать шестерёнку");
    }

    // ── Составные рецепты (задача C2) ─────────────────────────────────

    @Test
    @DisplayName("Составной рецепт не начинается, пока не приехали ВСЕ ингредиенты")
    void compositeRecipeWaitsForAllIngredients() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.ASSEMBLER, Direction.EAST));
        Assembler assembler = (Assembler) world.tile(0, 0).building();

        // Механизму нужны пластина + ДВЕ шестерёнки. Даём только одну шестерёнку:
        // ни один рецепт сборщика не выполним (шестерёнка не сырьё для шестерёнки).
        assembler.accept(Item.GEAR);
        stepTimes(world, 40);

        assertTrue(assembler.output().isEmpty(),
                "с одной шестерёнкой собрать нечего — машина обязана ЖДАТЬ, а не халтурить");
    }

    @Test
    @DisplayName("Пластина + две шестерёнки → механизм")
    void assemblerBuildsMechanism() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.ASSEMBLER, Direction.EAST));
        Assembler assembler = (Assembler) world.tile(0, 0).building();

        assembler.accept(Item.GEAR);
        assembler.accept(Item.GEAR);
        assembler.accept(Item.IRON_PLATE);
        stepTimes(world, 40);

        assertEquals(Item.MECHANISM, assembler.output().map(Handoff::item).orElse(null),
                "полный набор ингредиентов — должен получиться механизм");
    }

    @Test
    @DisplayName("Из двух подходящих рецептов машина выбирает более требовательный")
    void machinePrefersTheMoreDemandingRecipe() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.ASSEMBLER, Direction.EAST));
        Assembler assembler = (Assembler) world.tile(0, 0).building();

        // Здесь выполнимы ОБА рецепта: «пластина → шестерёнка» и
        // «пластина + 2 шестерёнки → механизм». Правило: побеждает тот, у кого
        // больше разных ингредиентов, — иначе сборщик тупо молол бы пластины в
        // шестерёнки и до механизма дело не дошло бы никогда.
        assembler.accept(Item.IRON_PLATE);
        assembler.accept(Item.GEAR);
        assembler.accept(Item.GEAR);
        stepTimes(world, 40);

        assertEquals(Item.MECHANISM, assembler.output().map(Handoff::item).orElse(null),
                "должен выиграть механизм, а не шестерёнка");
    }

    // ── Лаборатория (задача C3) ───────────────────────────────────────

    @Test
    @DisplayName("Лаборатория превращает шестерёнки в очки исследований")
    void labTurnsGearsIntoPoints() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();

        lab.accept(Item.GEAR);
        stepTimes(world, 40);

        assertEquals(1, lab.points(), "один цикл на шестерёнке даёт одно очко");
    }

    @Test
    @DisplayName("Механизм ценнее шестерёнки: за него дают втрое больше очков")
    void mechanismIsWorthMorePoints() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();

        lab.accept(Item.MECHANISM);
        stepTimes(world, 40);

        assertEquals(3, lab.points(), "рецепт на механизме даёт 3 очка — это ДАННЫЕ, не код");
    }

    @Test
    @DisplayName("Лаборатория — конечная точка: наружу не отдаёт ничего")
    void labGivesNothingBack() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();

        assertFalse(lab.canAccept(Item.IRON_ORE), "руду лаборатория не изучает");
        lab.accept(Item.GEAR);
        stepTimes(world, 40);

        assertTrue(lab.output().isEmpty(), "предметов наружу лаборатория не выдаёт");
    }

    @Test
    @DisplayName("Очки забираются РОВНО один раз")
    void pointsAreDrainedExactlyOnce() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();

        lab.accept(Item.GEAR);
        stepTimes(world, 40);

        assertEquals(1, lab.drainPoints(), "первый раз — забрали накопленное");
        assertEquals(0, lab.drainPoints(), "второй раз — уже пусто: начислить дважды нельзя");
    }

    @Test
    @DisplayName("Машина не принимает сырьё, которого ей уже хватает")
    void machineStopsAcceptingWhatItAlreadyHas() {
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

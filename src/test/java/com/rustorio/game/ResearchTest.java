package com.rustorio.game;

import com.rustorio.core.Balance;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tech;
import com.rustorio.core.Tool;
import com.rustorio.model.Building;
import com.rustorio.model.Furnace;
import com.rustorio.model.Lab;
import com.rustorio.model.World;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Тесты прогрессии: очки, дерево технологий, правила открытия (трек C).
 */
class ResearchTest {

    /** Игра с лабораторией на (0,0), которой скормили {@code gears} шестерёнок. */
    private static GameState gameWithLab(int gears) {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();
        for (int i = 0; i < gears; i++) {
            lab.accept(Item.GEAR);
        }
        return new GameState(world);
    }

    /** Прогнать столько кадров, чтобы лаборатория успела всё переработать. */
    private static void play(GameState game, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.update(Config.TICK);
        }
    }

    @Test
    @DisplayName("Очки из лабораторий попадают в общий счёт")
    void pointsFlowFromLabsIntoResearch() {
        GameState game = gameWithLab(1);
        play(game, 40);

        assertEquals(1, game.research().points(), "одна шестерёнка = одно очко в общем счёте");
    }

    @Test
    @DisplayName("Очки начисляются РОВНО один раз, сколько бы тиков ни прошло")
    void pointsAreNotCountedTwice() {
        GameState game = gameWithLab(1);
        play(game, 40);
        int afterFirst = game.research().points();
        play(game, 40); // ещё сорок тиков, но сырья больше нет

        assertEquals(afterFirst, game.research().points(),
                "лаборатория пуста — очкам взяться неоткуда");
    }

    @Test
    @DisplayName("Технологию с невыполненными предпосылками открыть нельзя")
    void cannotResearchWithoutPrerequisites() {
        // Один цикл лаборатории — 2 секунды, то есть ~12 тиков. Чтобы очков хватило и на
        // печь (15), и на бур (20), даём ей время переработать все шестерёнки.
        GameState game = gameWithLab(60);
        play(game, 800);

        // «Быстрый бур» требует открытой «Быстрой печи».
        assertFalse(game.research().canResearch(Tech.FAST_MINER),
                "предпосылка (быстрая печь) не открыта — нельзя");
        assertFalse(game.research().research(Tech.FAST_MINER), "и попытка обязана провалиться");

        assertTrue(game.research().research(Tech.FAST_FURNACE), "а вот печь открыть можно");
        assertTrue(game.research().canResearch(Tech.FAST_MINER),
                "теперь предпосылка выполнена — бур доступен");
    }

    @Test
    @DisplayName("Без очков технологию не открыть")
    void cannotResearchWithoutPoints() {
        GameState game = gameWithLab(1); // одно очко, а «быстрая лента» стоит десять
        play(game, 40);

        assertFalse(game.research().canResearch(Tech.FAST_BELT), "очков не хватает");
        assertFalse(game.research().research(Tech.FAST_BELT));
        assertFalse(game.research().isUnlocked(Tech.FAST_BELT));
    }

    @Test
    @DisplayName("Очки списываются РОВНО один раз, а технология не открывается дважды")
    void researchSpendsPointsExactlyOnce() {
        GameState game = gameWithLab(30);
        play(game, 300);
        int before = game.research().points();

        assertTrue(game.research().research(Tech.FAST_BELT), "первый раз — открыли");
        assertEquals(before - 10, game.research().points(), "списалась ровно стоимость");

        assertFalse(game.research().research(Tech.FAST_BELT),
                "второй раз — уже открыта, платить снова нельзя");
        assertEquals(before - 10, game.research().points(), "и очки не тронуты");
    }

    @Test
    @DisplayName("Открытая технология МЕНЯЕТ баланс игры")
    void researchAppliesItsEffect() {
        GameState game = gameWithLab(30);
        play(game, 300);

        Balance balance = game.balance();
        assertEquals(Config.BELT_SLOTS_PER_TICK, balance.beltSlotsPerTick(), "до апгрейда — база");

        assertTrue(game.research().research(Tech.FAST_BELT));

        assertEquals(3, balance.beltSlotsPerTick(),
                "«быстрая лента» обязана РЕАЛЬНО разогнать ленты, а не просто зажечь галочку");
    }

    // ── C5: апгрейд ощущается в игре, а не только в галочке ───────────

    /** Сколько тиков печь плавит одну руду при данном балансе. */
    private static int ticksToSmelt(GameState game, World world, int x, int y) {
        Furnace furnace = (Furnace) world.tile(x, y).building();
        furnace.accept(Item.IRON_ORE);
        for (int tick = 1; tick <= 200; tick++) {
            game.update(Config.TICK);
            if (furnace.output().isPresent()) {
                furnace.removeOutput(); // освобождаем выход под следующий замер
                return tick;
            }
        }
        throw new AssertionError("печь так и не выдала пластину за 200 тиков");
    }

    @Test
    @DisplayName("«Быстрая печь» РЕАЛЬНО ускоряет плавку, а не просто зажигает галочку")
    void fastFurnaceActuallySpeedsUpSmelting() {
        World world = World.generate(6, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        world.place(2, 0, Building.create(Tool.FURNACE, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();
        for (int i = 0; i < 20; i++) {
            lab.accept(Item.GEAR);
        }
        GameState game = new GameState(world);

        int before = ticksToSmelt(game, world, 2, 0);

        play(game, 300); // лаборатория копит очки
        assertTrue(game.research().research(Tech.FAST_FURNACE), "очков должно хватить");

        int after = ticksToSmelt(game, world, 2, 0);

        assertTrue(after < before,
                "после апгрейда плавка обязана занимать МЕНЬШЕ тиков: было " + before
                        + ", стало " + after + ". Если числа равны — эффект никуда не применился");
    }

    @Test
    @DisplayName("В зданиях ради апгрейда не меняется НИ СТРОЧКИ: они уже читают Balance")
    void upgradeReachesMachinesThroughTickContext() {
        GameState game = gameWithLab(30);
        play(game, 300);

        float speedBefore = game.balance().speed(Tool.FURNACE);
        game.research().research(Tech.FAST_FURNACE);
        float speedAfter = game.balance().speed(Tool.FURNACE);

        assertEquals(1.0f, speedBefore, 1e-6f, "до апгрейда — базовая скорость");
        assertEquals(1.5f, speedAfter, 1e-6f,
                "печь получает Balance через TickContext и ускоряется САМА");
    }

    @Test
    @DisplayName("Список доступного показывает только то, что открыть можно ПРЯМО СЕЙЧАС")
    void availableListsOnlyWhatIsReachableNow() {
        GameState game = gameWithLab(12); // хватит на «быструю ленту» (10), но не на печь (15)
        play(game, 120);

        var available = game.research().available().stream().map(t -> t.id()).toList();
        assertTrue(available.contains(Tech.FAST_BELT));
        assertFalse(available.contains(Tech.FAST_FURNACE), "очков не хватает");
        assertFalse(available.contains(Tech.FAST_MINER), "предпосылка не открыта");
    }
}

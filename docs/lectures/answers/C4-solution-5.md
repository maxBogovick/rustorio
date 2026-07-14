# 📄 Решение — Р5 — Тесты

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

```java
    /** Игра с лабораторией на (0,0), которой скормили gears шестерёнок. */
    private static GameState gameWithLab(int gears) {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();
        for (int i = 0; i < gears; i++) {
            lab.accept(Item.GEAR);
        }
        return new GameState(world);
    }

    private static void play(GameState game, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.update(Config.TICK);
        }
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

        assertFalse(game.research().canResearch(Tech.FAST_MINER),
                "предпосылка (быстрая печь) не открыта — нельзя");
        assertFalse(game.research().research(Tech.FAST_MINER), "и попытка обязана провалиться");

        assertTrue(game.research().research(Tech.FAST_FURNACE), "а вот печь открыть можно");
        assertTrue(game.research().canResearch(Tech.FAST_MINER),
                "теперь предпосылка выполнена — бур доступен");
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
```

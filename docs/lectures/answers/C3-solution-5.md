# 📄 Решение — Р5 — Тесты

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C3-workbook.md](../C3-workbook.md)

---

```java
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
```

# 📄 Решение — Р3 — Сквозной тест

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C5-workbook.md](../C5-workbook.md)

---

```java
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
```

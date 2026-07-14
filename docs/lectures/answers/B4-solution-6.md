# 📄 Решение — Р6 — Тесты

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B4-workbook.md](../B4-workbook.md)

---

```java
    @Test
    @DisplayName("Развилка раздаёт предметы по очереди, а не валит в один выход")
    void splitterAlternatesBetweenOutputs() {
        World world = World.generate(5, 5);
        world.place(1, 1, Building.create(Tool.SPLITTER, Direction.EAST));
        world.place(2, 1, Building.create(Tool.CHEST, Direction.EAST)); // прямо
        world.place(1, 2, Building.create(Tool.CHEST, Direction.EAST)); // направо (юг)
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST)); // налево (север)

        Splitter splitter = (Splitter) world.tile(1, 1).building();
        for (int i = 0; i < 6; i++) {
            splitter.accept(Item.IRON_ORE);
            stepTimes(world, 1);
        }

        int east = ((Chest) world.tile(2, 1).building()).items();
        int south = ((Chest) world.tile(1, 2).building()).items();
        int north = ((Chest) world.tile(1, 0).building()).items();

        assertEquals(6, east + south + north, "ни один предмет не потерялся");
        assertEquals(2, east, "поток разделился поровну между тремя выходами");
        assertEquals(2, south);
        assertEquals(2, north);
    }

    @Test
    @DisplayName("Подземка НЕ быстрее обычной ленты — иначе вся игра уехала бы под землю")
    void undergroundIsNotFasterThanBelt() {
        // Земля: 5 клеток ленты (0..4), ящик на 5.
        World ground = World.generate(10, 1);
        for (int x = 0; x <= 4; x++) {
            ground.place(x, 0, Building.create(Tool.BELT, Direction.EAST));
        }
        ground.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));
        ((Belt) ground.tile(0, 0).building()).accept(Item.IRON_ORE);

        // Подземка: вход на 0, выход на 4, ящик на 5 — тот же отрезок.
        World under = World.generate(10, 1);
        under.place(0, 0, Building.create(Tool.UNDERGROUND, Direction.EAST));
        under.place(4, 0, Building.create(Tool.UNDERGROUND, Direction.EAST));
        under.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));
        stepTimes(under, 1);
        ((UndergroundBelt) under.tile(0, 0).building()).accept(Item.IRON_ORE);

        int groundTicks = ticksUntilDelivered(ground, 5, 0);
        int underTicks = ticksUntilDelivered(under, 5, 0);

        assertTrue(underTicks >= groundTicks,
                "подземка (" + underTicks + " тиков) не должна обгонять ленту ("
                        + groundTicks + " тиков): она удобство, а не читерство");
    }
```

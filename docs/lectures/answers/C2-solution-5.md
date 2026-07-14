# 📄 Решение — Р5 — Тесты

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C2-workbook.md](../C2-workbook.md)

---

```java
    @Test
    @DisplayName("Составной рецепт не начинается, пока не приехали ВСЕ ингредиенты")
    void compositeRecipeWaitsForAllIngredients() {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.ASSEMBLER, Direction.EAST));
        Assembler assembler = (Assembler) world.tile(0, 0).building();

        // Механизму нужны пластина + ДВЕ шестерёнки. Даём только одну шестерёнку:
        // ни один рецепт сборщика не выполним.
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

        // Здесь выполнимы ОБА рецепта. Правило: побеждает тот, у кого больше разных
        // ингредиентов, — иначе сборщик молол бы пластины в шестерёнки, и до механизма
        // дело не дошло бы никогда.
        assembler.accept(Item.IRON_PLATE);
        assembler.accept(Item.GEAR);
        assembler.accept(Item.GEAR);
        stepTimes(world, 40);

        assertEquals(Item.MECHANISM, assembler.output().map(Handoff::item).orElse(null),
                "должен выиграть механизм, а не шестерёнка");
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
```

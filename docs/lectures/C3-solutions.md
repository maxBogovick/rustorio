# C3 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [C3-workbook.md](C3-workbook.md). Заходите сюда по
> ссылке из конкретного шага, а не читайте подряд.

---

## П1 — Рецепты лаборатории

- В `record Recipe` добавляется поле `int science` — оно становится частью канонического
  конструктора.
- Чтобы таблица производственных рецептов не заросла нулями, добавьте **короткий
  конструктор** без `science`, который делегирует каноническому с нулём. В `record` это
  обычный дополнительный конструктор: `this(machine, inputs, outputs, time, 0);`.
- Выходы лаборатории — `Map.of()`, пустая карта. Ядро её просто «прокрутит» и ничего не
  положит в очередь готового: **править ядро для этого не нужно**.
- Времена — в `Config` (`RESEARCH_TIME`), как и все базовые числа.

---

## Р1 — Рецепты лаборатории

```java
public record Recipe(Tool machine, Map<Item, Integer> inputs, Map<Item, Integer> outputs,
                     float time, int science) {

    /** Копируем карты в неизменяемые: рецепт — это данные, их никто не должен править. */
    public Recipe {
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    /** Обычный производственный рецепт: очков исследований не даёт. */
    public Recipe(Tool machine, Map<Item, Integer> inputs, Map<Item, Integer> outputs, float time) {
        this(machine, inputs, outputs, time, 0);
    }

    private static final List<Recipe> ALL = List.of(
            new Recipe(Tool.FURNACE,
                    Map.of(Item.IRON_ORE, 1),
                    Map.of(Item.IRON_PLATE, 1),
                    Config.SMELT_TIME),
            new Recipe(Tool.ASSEMBLER,
                    Map.of(Item.IRON_PLATE, 1),
                    Map.of(Item.GEAR, 1),
                    Config.ASSEMBLE_TIME),
            new Recipe(Tool.ASSEMBLER,
                    Map.of(Item.IRON_PLATE, 1, Item.GEAR, 2),
                    Map.of(Item.MECHANISM, 1),
                    Config.MECHANISM_TIME),
            // Рецепты ЛАБОРАТОРИИ: предметы тратятся, а на выходе НЕ предмет, а очки.
            // Пустая карта выходов — это не «забыли заполнить», а осмысленное «ничего
            // материального не производится».
            new Recipe(Tool.LAB,
                    Map.of(Item.GEAR, 1),
                    Map.of(),
                    Config.RESEARCH_TIME, 1),
            new Recipe(Tool.LAB,
                    Map.of(Item.MECHANISM, 1),
                    Map.of(),
                    Config.RESEARCH_TIME, 3)
    );
}
```

Javadoc, объясняющий поле (обязательная часть решения):

```java
 * <p><b>Про поле {@code science}.</b> Лаборатория выдаёт не предмет, а очки исследований —
 * их некуда положить в {@code outputs}, потому что очки не ездят по лентам. Поэтому у
 * рецепта есть отдельное поле «сколько очков даёт цикл»; у производственных рецептов оно
 * равно нулю (для них есть короткий конструктор без него).
```

```java
// core/Config.java
    /** Секунд на один цикл исследования в лаборатории. */
    public static final float RESEARCH_TIME = 2.0f;
```

---

## П2 — Очки в ядре

- Поле `private int science;` в `ProcessKernel`.
- В `complete(recipe)` — одна строка: `science += recipe.science();`. У печи и сборщика там
  ноль, и это никому не мешает.
- `drainScience()` возвращает накопленное **и обнуляет**. Никакого обычного геттера
  `science()` быть не должно: он бы позволил начислить очки дважды.

---

## Р2 — Очки в ядре

```java
    /**
     * Очки исследований, накопленные завершёнными циклами и ещё не забранные.
     *
     * <p>Ядро одно на все машины, и у печи со сборщиком тут всегда ноль — их рецепты очков
     * не дают. Заводить ради лаборатории ОТДЕЛЬНОЕ ядро значило бы дублировать всю логику
     * склада, выбора рецепта и прогресса; поле-счётчик стоит дешевле.
     */
    private int science;

    /** Списать сырьё со склада и выложить продукцию в очередь выхода. */
    private void complete(Recipe recipe) {
        recipe.inputs().forEach((item, needed) -> {
            int left = stock.get(item) - needed;
            if (left == 0) {
                stock.remove(item);
            } else {
                stock.put(item, left);
            }
        });
        recipe.outputs().forEach((item, count) -> {
            for (int i = 0; i < count; i++) {
                ready.add(item);
            }
        });
        science += recipe.science();   // у производственных рецептов — ноль
    }

    /**
     * Забрать накопленные очки исследований (и обнулить счётчик).
     *
     * <p>Именно «забрать», а не «прочитать»: очки должны попасть в игру ровно один раз.
     * Геттер, который не обнуляет, — верный способ начислить их дважды.
     */
    int drainScience() {
        int drained = science;
        science = 0;
        return drained;
    }
```

---

## П3 — `Lab`

- Поле `private final ProcessKernel kernel = new ProcessKernel(Tool.LAB);` — то же ядро, что
  у печи.
- `update`: сперва `kernel.update(ctx)`, потом `points += kernel.drainScience()`.
- `output()` — **всегда** `Optional.empty()`. Не делегируйте ядру: лаборатория конечная
  точка.
- `canAccept` / `accept` — делегируются ядру: оно само знает, какие предметы нужны рецептам
  лаборатории и сколько.
- `drainPoints()` — тот же приём «забрать», что и в ядре: очки уходят в общий счёт игры
  ровно один раз (это понадобится в C4).

---

## Р3 — `Lab`

```java
public final class Lab implements Building {

    private final ProcessKernel kernel = new ProcessKernel(Tool.LAB);

    /** Накопленные очки исследований. Их считывает Research (задача C4). */
    private int points;

    @Override
    public void update(TickContext ctx) {
        kernel.update(ctx);
        // Забираем очки, а не подсматриваем: иначе один и тот же цикл начислился бы дважды.
        points += kernel.drainScience();
    }

    @Override
    public Optional<Handoff> output() {
        return Optional.empty();   // конечная точка: наружу ничего не уходит
    }

    @Override
    public boolean canAccept(Item item) {
        return kernel.canAccept(item);
    }

    @Override
    public void accept(Item item) {
        kernel.accept(item);
    }

    @Override
    public void removeOutput() {
        // Нечего убирать: лаборатория не отдаёт предметы.
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.empty();   // у лаборатории нет направления
    }

    // ── Чтение для отрисовки и прогрессии ─────────────────────────────

    public int points() {
        return points;
    }

    /** Забрать накопленные очки в общий счёт игры (задача C4). */
    public int drainPoints() {
        int drained = points;
        points = 0;
        return drained;
    }

    public Optional<Item> displayItem() {
        return kernel.displayItem();
    }

    public float progressFraction() {
        return kernel.progressFraction();
    }
}
```

Ключевой абзац javadoc — **тоже часть решения**:

```java
 * <p><b>Почему она всё-таки использует общее {@link ProcessKernel}.</b> Со стороны кажется,
 * что у лаборатории «своя» логика. Но приглядитесь: она копит сырьё на складе, выбирает
 * подходящий рецепт, тратит время на цикл, а по завершении списывает ингредиенты — это
 * ровно то, что делают печь и сборщик. Отличие только в том, ЧТО получается в конце: у них
 * предмет, у неё — число. Заводить ради этого отдельное ядро значило бы скопировать склад,
 * выбор рецепта и прогресс.
```

---

## Р4 — Отрисовка

В проходе накладок лаборатория получает полоску прогресса (правит **владелец трека A**):

```java
                    case Lab lab -> drawProgressBar(px, py, lab.progressFraction());
                    case Splitter _, UndergroundBelt _ -> { /* заглушки: накладок нет */ }
```

Число очков уже рисуется в проходе предметов и текста:

```java
                    case Lab lab -> {
                        font.getData().setScale(0.9f);
                        font.setColor(Color.WHITE);
                        font.draw(batch, Integer.toString(lab.points()), px + 6, py + 20);
                    }
```

---

## Р5 — Тесты

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

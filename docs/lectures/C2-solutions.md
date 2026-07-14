# C2 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [C2-workbook.md](C2-workbook.md). Заходите сюда по
> ссылке из конкретного шага, а не читайте подряд: задача учит не тем, что вы увидите
> готовый код, а тем, что напишете свой и он не заработает.

---

## П1 — Новый предмет

- `Item` — обычный enum, добавляется одна константа.
- Время сборки — в `Config` (это **база**; множитель скорости от апгрейдов живёт в
  `Balance`).
- После этого компилятор упадёт в `Textures.itemTexture()` — там исчерпывающий `switch` по
  `Item` **без** `default`, и он требует ветку для нового предмета. Спрайта механизма в
  `resources/` нет — возьмите `bronse_gear.png`, он там лежит с Rust-версии.

---

## Р1 — Новый предмет

```java
// core/Item.java
public enum Item {
    IRON_ORE,
    IRON_PLATE,
    GEAR,
    /** Первый предмет, собираемый из ДВУХ разных ингредиентов (пластина + шестерёнки). */
    MECHANISM
}
```

```java
// core/Config.java
    /** Секунд на сборку механизма (составной рецепт: пластина + 2 шестерёнки). */
    public static final float MECHANISM_TIME = 1.6f;
```

```java
// render/Textures.java — сюда вас привёл компилятор
    /** Спрайт предмета по его типу. Новый предмет — одна ветка здесь. */
    Texture itemTexture(Item item) {
        return switch (item) {
            case IRON_ORE -> ironOre;
            case IRON_PLATE -> ironPlate;
            case GEAR -> gear;
            case MECHANISM -> mechanism;
        };
    }
```

---

## П2 — `Recipe`

- `record Recipe(Tool machine, Map<Item,Integer> inputs, Map<Item,Integer> outputs, float time)`.
- В **компактном конструкторе** скопируйте карты в неизменяемые (`Map.copyOf`): рецепт —
  это данные, их никто не должен править из-под руки.
- Правило выбора удобнее всего **вшить в порядок списка**: пусть `forMachine()` возвращает
  рецепты уже отсортированными по числу разных ингредиентов, по убыванию. Тогда «выбрать
  самый требовательный из подходящих» превращается в «взять первый подходящий» — и никакой
  логики выбора писать не надо.
- `isSatisfiedBy(stock)`: для каждой пары «предмет → сколько надо» проверьте, что на складе
  **не меньше**.

---

## Р2 — `Recipe`

```java
public record Recipe(Tool machine, Map<Item, Integer> inputs, Map<Item, Integer> outputs, float time) {

    /** Копируем карты в неизменяемые: рецепт — это данные, их никто не должен править. */
    public Recipe {
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    /** Таблица рецептов игры. Единственное место, где они перечислены. */
    private static final List<Recipe> ALL = List.of(
            new Recipe(Tool.FURNACE,
                    Map.of(Item.IRON_ORE, 1),
                    Map.of(Item.IRON_PLATE, 1),
                    Config.SMELT_TIME),
            new Recipe(Tool.ASSEMBLER,
                    Map.of(Item.IRON_PLATE, 1),
                    Map.of(Item.GEAR, 1),
                    Config.ASSEMBLE_TIME),
            // Первый СОСТАВНОЙ рецепт — ради него всё и затевалось.
            new Recipe(Tool.ASSEMBLER,
                    Map.of(Item.IRON_PLATE, 1, Item.GEAR, 2),
                    Map.of(Item.MECHANISM, 1),
                    Config.MECHANISM_TIME)
    );

    /** Рецепты машины, отсортированные «сложные сначала». */
    private static final Map<Tool, List<Recipe>> BY_MACHINE = ALL.stream()
            .collect(Collectors.groupingBy(
                    Recipe::machine,
                    Collectors.collectingAndThen(
                            Collectors.toList(),
                            list -> list.stream()
                                    .sorted(Comparator.comparingInt(
                                            (Recipe r) -> r.inputs().size()).reversed())
                                    .toList())));

    /**
     * Все рецепты машины — <b>от самого требовательного к самому простому</b>.
     *
     * <p>У сборщика их два: «пластина → шестерёнка» и «пластина + 2 шестерёнки → механизм».
     * Если внутри лежат и пластина, и шестерёнки, подходят ОБА, и машине надо как-то выбрать.
     * Правило: побеждает рецепт с бо́льшим числом разных ингредиентов (ничья — по порядку в
     * таблице). Так сборщик соберёт механизм, а не будет тупо молоть пластины в шестерёнки.
     *
     * <p>Правило ДЕТЕРМИНИРОВАНО: одинаковый склад — одинаковый выбор, всегда. Иначе
     * поведение машины зависело бы от порядка строк в таблице, то есть от случайности.
     */
    public static List<Recipe> forMachine(Tool machine) {
        return BY_MACHINE.getOrDefault(machine, List.of());
    }

    /** Хватает ли {@code stock} на этот рецепт? */
    boolean isSatisfiedBy(Map<Item, Integer> stock) {
        for (Map.Entry<Item, Integer> need : inputs.entrySet()) {
            if (stock.getOrDefault(need.getKey(), 0) < need.getValue()) {
                return false;
            }
        }
        return true;
    }
}
```

---

## П3 — `ProcessKernel`

- Склад: `Map<Item,Integer> stock = new EnumMap<>(Item.class)`. `EnumMap` — самая быстрая и
  компактная карта, когда ключ — enum.
- Очередь готового: `Deque<Item> ready = new ArrayDeque<>()`.
- Поле `@Nullable Recipe active` — рецепт в работе. Выбирается, когда его нет; обнуляется,
  когда цикл закончен.
- `update`: если очередь готового **не пуста** — ничего не делаем (выход занят, пусть
  заберут). Иначе: нет активного рецепта — выбрать; нет подходящего — обнулить прогресс и
  выйти.
- `complete(recipe)`: списать входы (и **удалить ключ**, если стало ноль), разложить выходы
  в очередь по одному предмету.
- `canAccept(item)`: пройтись по рецептам машины; если предмет нужен рецепту (`needed > 0`)
  **и** на складе его меньше, чем нужно, — берём.

---

## Р3 — `ProcessKernel`

```java
final class ProcessKernel {

    private final Tool machine;

    /** Склад сырья: сколько чего уже приехало. Пустая карта — склад пуст. */
    private final Map<Item, Integer> stock = new EnumMap<>(Item.class);

    /** Готовая продукция, ждущая, когда её заберут (по одному предмету за раз). */
    private final Deque<Item> ready = new ArrayDeque<>();

    /**
     * Рецепт, который машина сейчас выполняет.
     *
     * <p>Выбирается ОДИН раз в начале цикла и не меняется до его конца — иначе приезд нового
     * предмета посреди работы мог бы «переключить» машину на другой рецепт, а потраченное
     * время пропало бы.
     */
    private @Nullable Recipe active;

    private float progress;
    /** Длительность текущего цикла с учётом апгрейдов (для полоски прогресса). */
    private float cycleTime;

    ProcessKernel(Tool machine) {
        this.machine = machine;
    }

    void update(TickContext ctx) {
        if (!ready.isEmpty()) {
            return; // выход занят: сперва пусть заберут готовое
        }
        if (active == null) {
            active = chooseRecipe();
            if (active == null) {
                progress = 0f;
                return; // сырья не хватает ни на один рецепт
            }
        }
        cycleTime = active.time() / ctx.balance().speed(machine);
        progress += ctx.dt();
        if (progress >= cycleTime) {
            complete(active);
            active = null;
            progress = 0f;
        }
    }

    /**
     * Какой рецепт машина может выполнить прямо сейчас.
     *
     * <p>forMachine() уже отдаёт рецепты «сложные сначала», поэтому первый подошедший — самый
     * требовательный из возможных. Никакой логики выбора писать не пришлось.
     */
    private @Nullable Recipe chooseRecipe() {
        for (Recipe recipe : Recipe.forMachine(machine)) {
            if (recipe.isSatisfiedBy(stock)) {
                return recipe;
            }
        }
        return null;
    }

    /** Списать сырьё со склада и выложить продукцию в очередь выхода. */
    private void complete(Recipe recipe) {
        recipe.inputs().forEach((item, needed) -> {
            int left = stock.get(item) - needed;
            if (left == 0) {
                stock.remove(item);   // не держим нули: пустой склад = пустая карта
            } else {
                stock.put(item, left);
            }
        });
        recipe.outputs().forEach((item, count) -> {
            for (int i = 0; i < count; i++) {
                ready.add(item);
            }
        });
    }

    /**
     * Примет ли машина этот предмет.
     *
     * <p>Правило: предмет нужен какому-то моему рецепту И его на складе ещё НЕ ХВАТАЕТ.
     * Вторая половина важна: без неё сборщик набивался бы пластинами до бесконечности, а
     * лента перед ним никогда бы не забилась — то есть игрок не увидел бы, что где-то затор.
     */
    boolean canAccept(Item item) {
        for (Recipe recipe : Recipe.forMachine(machine)) {
            int needed = recipe.inputs().getOrDefault(item, 0);
            if (needed > 0 && stock.getOrDefault(item, 0) < needed) {
                return true;
            }
        }
        return false;
    }

    void accept(Item item) {
        stock.merge(item, 1, Integer::sum);
    }

    Optional<Item> output() {
        return Optional.ofNullable(ready.peek());
    }

    void removeOutput() {
        ready.poll();
    }

    // ── Чтение состояния для отрисовки ───────────────────────────────

    /** Что показать на машине: приоритет у готового продукта, иначе — любое сырьё со склада. */
    Optional<Item> displayItem() {
        Item product = ready.peek();
        if (product != null) {
            return Optional.of(product);
        }
        return stock.keySet().stream().findFirst();
    }

    /** Есть ли на складе хоть что-то (печь по этому решает, гореть ли ей). */
    boolean hasStock() {
        return !stock.isEmpty();
    }

    float progressFraction() {
        if (active == null || cycleTime <= 0f) {
            return 0f;
        }
        return Math.min(progress / cycleTime, 1f);
    }
}
```

---

## П4 — Геттеры для рендера

Рендер (чужой трек!) читал у печи `input()` — «какой предмет во мне лежит». Одного «input»
больше нет. Три метода закрывают все его нужды:

- `displayItem()` — что нарисовать на клетке;
- `isWorking()` — цвет стрелки (зелёная/красная);
- `hasStock()` — печь горит или потухла.

Старые `input()` / `outputItem()` **удаляются**. Обязательно напишите об этом в PR: у них
есть читатель в файле, который вам не принадлежит.

---

## Р4 — Машины и рендер

Печь (сборщик — один в один, только `Tool.ASSEMBLER`):

```java
public final class Furnace implements Building {

    private final Direction dir;
    private final ProcessKernel kernel = new ProcessKernel(Tool.FURNACE);

    @Override
    public void update(TickContext ctx) {
        kernel.update(ctx);
    }

    @Override
    public Optional<Handoff> output() {
        return kernel.output().map(item -> new Handoff(item, dir));
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
        kernel.removeOutput();
    }

    // ── Геттеры для отрисовки ────────────────────────────────────────
    public Direction dir() {
        return dir;
    }

    /** Что показать на машине: готовый продукт, иначе — сырьё со склада. */
    public Optional<Item> displayItem() {
        return kernel.displayItem();
    }

    /** Машина сейчас работает (есть рецепт в работе)? */
    public boolean isWorking() {
        return kernel.progressFraction() > 0f;
    }

    /** Есть ли на складе сырьё (печь по этому решает, гореть ли ей). */
    public boolean hasStock() {
        return kernel.hasStock();
    }

    public float progressFraction() {
        return kernel.progressFraction();
    }
}
```

Рендер (правит **владелец трека A**, вы только предупреждаете):

```java
                    case Furnace f -> batch.draw(
                            f.hasStock() ? textures.furnaceOn : textures.furnaceOff,
                            px, py, TILE, TILE);
...
                    case Furnace f -> {
                        drawArrow(px, py, f.dir(), f.isWorking());
                        drawProgressBar(px, py, f.progressFraction());
                    }
...
                    case Furnace f -> f.displayItem().ifPresent(it -> drawItemIcon(px, py, it));
```

---

## Р5 — Тесты

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

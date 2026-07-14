# 📄 Решение — Р3 — `ProcessKernel`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C2-workbook.md](../C2-workbook.md)

---

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

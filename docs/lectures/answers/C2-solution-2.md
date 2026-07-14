# 📄 Решение — Р2 — `Recipe`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C2-workbook.md](../C2-workbook.md)

---

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

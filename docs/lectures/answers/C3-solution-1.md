# 📄 Решение — Р1 — Рецепты лаборатории

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C3-workbook.md](../C3-workbook.md)

---

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

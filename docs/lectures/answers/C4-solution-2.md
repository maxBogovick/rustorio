# 📄 Решение — Р2 — `Technology`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

```java
public record Technology(Tech id, int cost, List<Tech> requires, List<Effect> effects) {

    public Technology {
        requires = List.copyOf(requires);
        effects = List.copyOf(effects);
    }

    /**
     * Дерево технологий. Единственное место, где оно описано.
     *
     * <pre>
     *   Быстрая лента (10) ──► Длинная подземка (25)
     *   Быстрая печь  (15) ──► Быстрый бур      (20)
     * </pre>
     */
    private static final List<Technology> ALL = List.of(
            new Technology(Tech.FAST_BELT, 10,
                    List.of(),
                    List.of(new Effect.BeltSpeed(3))),
            new Technology(Tech.FAST_FURNACE, 15,
                    List.of(),
                    List.of(new Effect.MachineSpeed(Tool.FURNACE, 1.5f))),
            new Technology(Tech.FAST_MINER, 20,
                    List.of(Tech.FAST_FURNACE),
                    List.of(new Effect.MachineSpeed(Tool.MINER, 1.5f))),
            new Technology(Tech.LONG_UNDERGROUND, 25,
                    List.of(Tech.FAST_BELT),
                    List.of(new Effect.UndergroundReach(6)))
    );

    private static final Map<Tech, Technology> BY_ID = ALL.stream()
            .collect(Collectors.toMap(
                    Technology::id, t -> t, (a, b) -> a, () -> new EnumMap<>(Tech.class)));

    public static List<Technology> all() {
        return ALL;
    }

    public static Technology of(Tech id) {
        return BY_ID.get(id);
    }

    /** Все ли предпосылки уже открыты? */
    public boolean isAvailableWith(Set<Tech> unlocked) {
        return unlocked.containsAll(requires);
    }
}
```

Javadoc про стоимость — **часть решения**:

```java
 * <p><b>Почему стоимость в ОЧКАХ, а не в предметах.</b> В первой редакции плана было написано
 * «cost: сколько каких предметов». Это ошибка: предметы уже потрачены — их съела Lab, превратив
 * в очки. Брать плату ещё и предметами значило бы взять деньги дважды за одно и то же. Очки —
 * единственная валюта прогрессии.
```

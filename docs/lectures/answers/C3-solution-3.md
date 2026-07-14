# 📄 Решение — Р3 — `Lab`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C3-workbook.md](../C3-workbook.md)

---

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

# 📄 Решение — Р4 — Машины и рендер

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C2-workbook.md](../C2-workbook.md)

---

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

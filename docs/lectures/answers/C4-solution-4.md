# 📄 Решение — Р4 — Сбор очков в `GameState`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

```java
    /** Прогресс исследований: очки из лабораторий и открытые технологии. */
    private final Research research = new Research(balance);

    public void update(float deltaTime) {
        if (paused) {
            return;
        }
        accumulator = Math.min(accumulator + deltaTime, Config.MAX_FRAME_TIME);
        TickContext ctx = new TickContext(Config.TICK, balance);
        while (accumulator >= Config.TICK) {
            accumulator -= Config.TICK;
            simulation.step(ctx);
            // Забираем очки из лабораторий сразу после шага мира: они уже начислены.
            research.collect(world);
        }
    }

    /** Прогресс исследований (его читает интерфейс, в нём же открывают технологии). */
    public Research research() {
        return research;
    }
```

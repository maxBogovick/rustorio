# 📄 Решение — Р2 — Замер и отчёт

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C1-workbook.md](../C1-workbook.md)

---

```java
    /** Сколько шагов выбросить перед замером (разогрев JIT). */
    private static final int WARMUP_TICKS = 200;
    /** Сколько шагов замеряем. */
    private static final int MEASURED_TICKS = 1000;

    public static void main(String[] args) {
        World world = buildFactory();
        Simulation simulation = new Simulation(world);
        TickContext ctx = new TickContext(Config.TICK, new Balance());

        // ── Разогрев ──────────────────────────────────────────────────
        // Java сначала исполняет код медленно (интерпретирует), а увидев, что участок
        // выполняется часто, на ходу перекомпилирует его в быстрый машинный код (JIT).
        // Замерите с первого шага — измерите разогрев, а не работу.
        for (int i = 0; i < WARMUP_TICKS; i++) {
            simulation.step(ctx);
        }

        // ── Замер ─────────────────────────────────────────────────────
        long[] nanos = new long[MEASURED_TICKS];
        for (int i = 0; i < MEASURED_TICKS; i++) {
            long start = System.nanoTime();
            simulation.step(ctx);
            nanos[i] = System.nanoTime() - start;
        }

        report(world, nanos);
    }

    private static void report(World world, long[] nanos) {
        int[] buildings = {0};
        world.forEachBuilding((x, y, building) -> buildings[0]++);

        long[] sorted = nanos.clone();
        Arrays.sort(sorted);
        double avgMs = Arrays.stream(nanos).average().orElse(0) / 1_000_000.0;
        double medianMs = sorted[sorted.length / 2] / 1_000_000.0;
        double p99Ms = sorted[(int) (sorted.length * 0.99)] / 1_000_000.0;
        double worstMs = sorted[sorted.length - 1] / 1_000_000.0;

        System.out.printf("""

                ┌─ Бенчмарк симуляции ────────────────────────────────
                │  поле:            %d × %d  (%,d клеток)
                │  зданий:          %,d
                │  линий лент:      %,d
                │  предметов:       %,d
                ├─ Один шаг симуляции ────────────────────────────────
                │  среднее:         %.3f мс
                │  медиана:         %.3f мс
                │  99-й процентиль: %.3f мс
                │  худший шаг:      %.3f мс
                ├─ Бюджет ────────────────────────────────────────────
                │  цель У1:         ≤ 8.000 мс   →  %s
                └─────────────────────────────────────────────────────
                %n""",
                SIZE, SIZE, SIZE * SIZE,
                buildings[0],
                world.belts().segments().size(),
                world.belts().itemCount(),
                avgMs, medianMs, p99Ms, worstMs,
                p99Ms <= 8.0 ? "УКЛАДЫВАЕМСЯ ✓  (сон зданий B5 пока НЕ нужен)"
                             : "НЕ УКЛАДЫВАЕМСЯ ✗  (пора делать B5)");
    }
```

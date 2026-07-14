package com.rustorio.bench;

import com.rustorio.core.Balance;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import com.rustorio.model.Belt;
import com.rustorio.model.Building;
import com.rustorio.model.World;
import com.rustorio.sim.Simulation;

import java.util.Arrays;

/**
 * Бенчмарк симуляции: сколько миллисекунд занимает ОДИН шаг мира.
 *
 * <p><b>Зачем это первая задача проекта.</b> Без замера любое решение об оптимизации —
 * гадание. Можно неделю делать сон зданий (задача B5) и обнаружить, что и без него всё
 * летало. Или наоборот — тянуть с чанками, не зная, что уже задыхаемся. Это число
 * решает судьбу самой сложной задачи проекта.
 *
 * <p><b>Почему без libGDX.</b> Домен (core, model, sim) не зависит от движка — и вот
 * первая практическая выгода: симуляцию можно запустить как обычную консольную
 * программу, без окна, мыши и видеокарты. Ровно это и проверяет
 * {@code ArchitectureTest}.
 *
 * <p>Запуск: {@code ./gradlew benchmark}
 */
public final class SimulationBenchmark {

    /** Размер поля. 250×250 = 62 500 клеток — хватает под 50 000 зданий. */
    private static final int SIZE = 250;
    /** Сколько шагов выбросить перед замером (разогрев JIT — см. ниже). */
    private static final int WARMUP_TICKS = 200;
    /** Сколько шагов замеряем. */
    private static final int MEASURED_TICKS = 1000;

    private SimulationBenchmark() {
    }

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

    /**
     * Большая фабрика: длинные ленты, перемежающиеся печами, и буры на руде.
     *
     * <p>Сцена намеренно «злая»: ленты идут длинными рядами (значит, транспортные
     * линии получаются большими), а печи стоят так, чтобы предметы реально ехали и
     * перерабатывались, а не стояли мёртвым грузом.
     */
    private static World buildFactory() {
        World world = World.generate(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Стоков (ящиков) намеренно НЕТ: ленты упираются в тупик и остаются
                // забитыми. Это худший случай — максимум предметов, которые надо
                // двигать каждый тик. Поставь ящики — они бы всё выпили, и замер
                // показал бы почти пустой мир.
                Tool tool = switch (x % 25) {
                    case 0 -> Tool.MINER;      // источник в начале ряда
                    case 12 -> Tool.FURNACE;   // передел посередине
                    default -> Tool.BELT;
                };
                world.place(x, y, Building.create(tool, Direction.EAST));
            }
        }
        // Набиваем ленты грузом. Без этого замер врёт: стоимость движения зависит от
        // ЧИСЛА ПРЕДМЕТОВ, а если ждать, пока буры сами наполнят фабрику, мы измерим
        // почти пустой мир и обрадуемся зря.
        world.forEachBuilding((x, y, building) -> {
            if (building instanceof Belt belt && belt.canAccept(Item.IRON_ORE)) {
                belt.accept(Item.IRON_ORE);
            }
        });
        return world;
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
}

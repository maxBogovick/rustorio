package com.rustorio;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless-бенчмарк: сколько миллисекунд занимает один {@link World#tick()} на сцене заданного
 * размера — БЕЗ окна libGDX (сравни с У1 из старого архитектурного разбора: «шаг симуляции
 * должен укладываться в бюджет кадра с запасом»). Отвечает на конкретный вопрос: нужен ли
 * реестр активных сущностей (сон/пробуждение зданий) — а не гадание по интуиции. Правило проекта
 * «не оптимизируем без замера» без такого числа не выполнить вообще никак.
 *
 * <p>Сцена — {@code lanes} параллельных лент по {@code laneLength} клеток, каждая упирается в
 * ящик и постоянно дозаправляется с хвоста, чтобы всю дорогу быть ЗАНЯТОЙ (пустая лента дешевле
 * настоящей и соврала бы про нагрузку). Сознательно узкий сценарий — только {@link Belt}, самое
 * многочисленное и недавно переделанное (см. {@link BeltSegment}) здание в реальной фабрике;
 * это число не заменяет замер на смеси всех строений, а отвечает на более узкий вопрос — не
 * оказалась ли сама разбивка на сегменты дорогой.
 *
 * <p>Запуск: {@code ./gradlew benchmark} (числа по умолчанию) или
 * {@code ./gradlew benchmark --args="lanes laneLength measuredTicks"}.
 */
public final class Benchmark {

    private static final int WARMUP_TICKS = 200;

    private Benchmark() {
    }

    public static void main(String[] args) {
        int lanes = args.length > 0 ? Integer.parseInt(args[0]) : 300;
        int laneLength = args.length > 1 ? Integer.parseInt(args[1]) : 100;
        int measuredTicks = args.length > 2 ? Integer.parseInt(args[2]) : 1000;

        Scene scene = buildScene(lanes, laneLength);
        int totalBuildings = lanes * (laneLength + 1); // +1 — ящик на конце каждой линии

        for (int i = 0; i < WARMUP_TICKS; i++) {
            scene.tick(); // прогрев JIT — не входит в замер
        }

        long start = System.nanoTime();
        for (int i = 0; i < measuredTicks; i++) {
            scene.tick();
        }
        long elapsedNanos = System.nanoTime() - start;
        double msPerTick = elapsedNanos / 1_000_000.0 / measuredTicks;

        System.out.printf("Зданий: %d (%d линий по %d + ящик)%n", totalBuildings, lanes, laneLength);
        System.out.printf("Тиков замерено: %d (плюс %d на прогрев)%n", measuredTicks, WARMUP_TICKS);
        System.out.printf("Среднее время шага: %.4f мс/тик%n", msPerTick);
    }

    private static Scene buildScene(int lanes, int laneLength) {
        World world = new World(laneLength + 2, lanes * 2);
        List<Belt> tails = new ArrayList<>(lanes);
        for (int lane = 0; lane < lanes; lane++) {
            int y = lane * 2; // через ряд — ленты соседних линий не смежные, сегменты не сольются
            for (int x = 0; x < laneLength; x++) {
                world.placeBelt(x, y, Direction.RIGHT);
            }
            world.placeChest(laneLength, y);

            for (int x = 0; x < laneLength; x++) {
                ((Belt) world.peek(x, y)).accept(world, Item.IRON_ORE); // сразу набить линию грузом
            }
            tails.add((Belt) world.peek(0, y));
        }
        return new Scene(world, tails);
    }

    /** Мир и хвосты линий — каждый тик дозаправляем хвост, если груз успел уйти вперёд. */
    private record Scene(World world, List<Belt> tails) {
        void tick() {
            for (Belt tail : tails) {
                tail.accept(world, Item.IRON_ORE); // хвост занят — не получится, и не страшно
            }
            world.tick();
        }
    }
}

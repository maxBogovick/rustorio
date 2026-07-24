package com.rustorio;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Predicate;

/**
 * Прямой участок ленты как ОДНА сущность: упорядоченный список клеток (хвост — вход, голова —
 * выход), которые за один вызов {@link #tick} двигаются все разом — вместо N независимых
 * {@code Belt#tick}, честно позвавших {@code world.offerForward} каждая на свою клетку вперёд.
 *
 * <p>Каждый {@link Belt}-тайл при этом остаётся владельцем СВОЕГО предмета ({@code held}) — ровно
 * как раньше; сегмент лишь знает ПОРЯДОК тайлов и двигает груз между ними. Это и упрощает слияние/
 * разрез (§ниже): у тайла не нужно переиндексировать позицию в общем массиве, только переставить
 * ссылку на сегмент.
 *
 * <p><b>Почему не O(1), как в Factorio.</b> Настоящая транспортная линия двигает груз за счёт
 * общего смещения (переставить один указатель вместо N предметов). Здесь — простой проход по
 * списку, O(длины ленты) на тик. Это осознанный выбор: тик и так медленный (см. {@code Config}),
 * длины лент на этой карте малы, а простой код проще проверить тестами на склейку/разрез — самое
 * рискованное место всего рефакторинга. Ускорять до O(1) — тема отдельной вехи, и то не раньше,
 * чем бенчмарк покажет нужду (см. правило тай-брейка: не оптимизируем без замера).
 */
final class BeltSegment {

    private final Direction direction;

    /** Индекс 0 — хвост (вход, откуда сосед кладёт предмет), последний — голова (выход). */
    private final Deque<Belt> tiles = new ArrayDeque<>();

    BeltSegment(Direction direction) {
        this.direction = direction;
    }

    Direction direction() {
        return direction;
    }

    /** Сколько тайлов в сегменте — нужно, чтобы хвост-«водитель» вычислил координаты выхода. */
    int size() {
        return tiles.size();
    }

    /**
     * Тикает только ХВОСТ сегмента (см. {@link Belt#tick}) — иначе один и тот же сдвиг груза
     * применился бы по разу на каждый тайл, и лента ехала бы в N раз быстрее своей длины.
     */
    boolean isTail(Belt belt) {
        return tiles.peekFirst() == belt;
    }

    /** Приставить {@code belt} НОВОЙ головой (клетка построена сразу ЗА текущей головой). */
    void addHead(Belt belt) {
        tiles.addLast(belt);
        belt.joinSegment(this);
    }

    /** Приставить {@code belt} НОВЫМ хвостом (клетка построена сразу ПЕРЕД текущим хвостом). */
    void addTail(Belt belt) {
        tiles.addFirst(belt);
        belt.joinSegment(this);
    }

    /**
     * Влить {@code other} (идущий строго following ЗА головой этого сегмента, того же
     * направления) в этот сегмент — постройка соединила два прямых участка одной клеткой.
     * {@code other} после вызова не используется — все его тайлы уже переехали сюда.
     */
    void mergeHead(BeltSegment other) {
        for (Belt belt : other.tiles) {
            tiles.addLast(belt);
            belt.joinSegment(this);
        }
    }

    /**
     * Убрать {@code belt} из сегмента — снос здания. Сжимает сегмент с края или, если тайл был
     * посередине, режет на два независимых сегмента вокруг дыры. Груз, который держал СНЕСЁННЫЙ
     * тайл, уходит вместе со зданием (как содержимое ящика/печи при сносе) — не теряется и не
     * дублируется у соседей, потому что каждый оставшийся тайл просто переезжает в РОВНО ОДИН из
     * двух кусков, не копируясь.
     */
    void remove(Belt belt) {
        List<Belt> ordered = new ArrayList<>(tiles); // снос — не горячий путь, O(n) тут не страшно
        int index = ordered.indexOf(belt);
        tiles.clear();

        for (int i = 0; i < index; i++) {
            tiles.addLast(ordered.get(i)); // остаются в ЭТОМ сегменте — уже указывают на него
        }
        if (index < ordered.size() - 1) {
            BeltSegment tail = new BeltSegment(direction);
            for (int i = index + 1; i < ordered.size(); i++) {
                tail.addHead(ordered.get(i)); // addHead заодно переставит их segment-ссылку
            }
        }
        belt.joinSegment(null);
    }

    /**
     * Прожить один тик: голова пытается уйти наружу через {@code tryExit}; если ушла — следующий
     * за ней (и так по цепочке к хвосту) продвигается на её место. Один проход головы к хвосту —
     * та же гарантия «не больше одной клетки за тик», которую раньше давал двухпроходный обход
     * {@link World#tick()}; здесь она уже не нужна ВНУТРИ сегмента (только между сегментами).
     */
    void tick(Predicate<Item> tryExit) {
        Belt next = null; // тайл БЛИЖЕ к голове, уже пройденный в этом проходе
        for (var it = tiles.descendingIterator(); it.hasNext(); ) {
            Belt belt = it.next();
            if (next == null) {
                Item head = belt.held();
                if (head != null && tryExit.test(head)) {
                    belt.clearHeld();
                }
            } else if (belt.held() != null && next.held() == null) {
                next.setHeld(belt.held());
                belt.clearHeld();
            }
            next = belt;
        }
    }
}

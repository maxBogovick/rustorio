package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.Optional;

/**
 * Всё, что можно поставить на клетку поля.
 *
 * <p><b>Главная идея всей игры.</b> Sealed-интерфейс: {@code permits} перечисляет
 * ЗАКРЫТЫЙ набор зданий. {@code switch} по зданию не требует {@code default} — все
 * случаи известны компилятору, и новое здание он потребует разобрать везде.
 *
 * <p>Отрисовки здесь СПЕЦИАЛЬНО нет: домен ничего не знает про экран и libGDX.
 * Здание умеет «жить» в симуляции ({@link #update}) и участвовать в передаче
 * предметов ({@link #output}/{@link #canAccept}/{@link #accept}); «как оно
 * выглядит» — забота слоя {@code render}.
 */
public sealed interface Building permits Chest, Miner, Belt, Furnace {

    /**
     * «Внутренняя» работа здания за один шаг симуляции (тик).
     *
     * <p>Единственный параметр — {@link TickContext}: коробка со всем, что нужно
     * зданиям на этом шаге. Так сигнатуру не приходится править во всех зданиях,
     * когда появляется новое общее данное (см. javadoc {@code TickContext}).
     */
    void update(TickContext ctx);

    /**
     * Что здание готово отдать соседу прямо сейчас (и в какую сторону).
     *
     * @return предмет с направлением, либо {@link Optional#empty()}, если отдавать нечего
     */
    Optional<Handoff> output();

    /** Может ли здание принять {@code item} от соседа прямо сейчас? */
    boolean canAccept(Item item);

    /** Принять предмет в себя (после успешной передачи). */
    void accept(Item item);

    /** Убрать отданный предмет из выхода (после успешной передачи). */
    void removeOutput();

    /** Направление здания, если оно у него есть (у ящика — нет). */
    Optional<Direction> direction();

    /**
     * «Такое же» здание — тот же тип и то же направление?
     *
     * <p>Нужно, чтобы рисование мышью с зажатой ЛКМ не пересоздавало здание
     * каждый кадр. Реализация одинакова для всех зданий, поэтому это
     * {@code default}-метод: тип сравниваем по классу, направление — по значению.
     */
    default boolean sameKind(Building other) {
        return this.getClass() == other.getClass()
                && this.direction().equals(other.direction());
    }

    /**
     * Создать НОВОЕ здание по выбранному инструменту и направлению.
     *
     * <p>Единственная «фабрика» зданий. {@code switch} исчерпывающий по
     * {@code enum Tool}: добавишь инструмент — компилятор потребует ветку.
     *
     * <p>До L8 здесь был {@code Optional<Building>}: зданий было меньше, чем
     * инструментов, и одна ветка не могла ничего вернуть. Теперь каждая ветка
     * возвращает настоящее здание — обёртка стала чистой церемонией без единого
     * случая, когда она на самом деле нужна, и мы её убрали.
     */
    static Building create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> new Miner(dir);
            case CHEST -> new Chest();
            case BELT -> new Belt(dir);
            case FURNACE -> new Furnace(dir);
        };
    }
}

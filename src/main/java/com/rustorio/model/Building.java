package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.Optional;

/**
 * Всё, что можно поставить на клетку поля.
 *
 * <p><b>Главная идея всей игры</b> (перенесена из Rust-версии, где это был
 * {@code enum Building}). В Java 21 точный аналог алгебраического типа-суммы —
 * это <b>sealed-интерфейс</b>: {@code permits} перечисляет ЗАКРЫТЫЙ набор
 * зданий, ровно как варианты {@code enum} в Rust. Никто снаружи пакета не
 * добавит новый вид здания в обход этого списка.
 *
 * <p>Это даёт те же две гарантии от компилятора, что так ценились в Rust:
 * <ol>
 *   <li><b>Поведение.</b> Абстрактные методы интерфейса заставляют новый класс
 *       здания реализовать ВСЮ логику (обновление, приём/отдачу предмета…) —
 *       забыть нельзя, не скомпилируется.</li>
 *   <li><b>Исчерпывающий разбор.</b> Отрисовка делает {@code switch} по этому
 *       sealed-типу без ветки {@code default}; добавишь здание — компилятор
 *       потребует новую {@code case}. Это и есть «карта задач» из Rust: идёшь
 *       по ошибкам компилятора сверху вниз.</li>
 * </ol>
 *
 * <p>Отрисовки здесь СПЕЦИАЛЬНО нет: домен ничего не знает про экран и libGDX.
 * Здание умеет только «жить» в симуляции; «как оно выглядит» — забота слоя
 * {@code render} (стрелки зависимостей смотрят вниз, к данным).
 */
public sealed interface Building
        permits Miner, Belt, Furnace, Chest, Assembler, Splitter, UndergroundBelt, Lab {

    /**
     * «Внутренняя» работа здания за один шаг симуляции (тик).
     *
     * <p>Единственный параметр — {@link TickContext}: коробка со всем, что нужно всем
     * зданиям (длина тика, баланс). Раньше здесь был ещё {@code boolean hasOre},
     * нужный ОДНОМУ буру, но навязанный всем; теперь бур узнаёт про руду при
     * постройке, а сигнатура этого метода больше не меняется никогда.
     */
    void update(TickContext ctx);

    /**
     * Что здание готово отдать соседу прямо сейчас (и в какую сторону).
     *
     * @return предмет с направлением, либо {@link Optional#empty()},
     *         если отдавать нечего
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
     * каждый кадр (иначе лента сбрасывала бы предмет). Реализация одинакова
     * для всех зданий, поэтому это {@code default}-метод: тип сравниваем по
     * классу (аналог {@code std::mem::discriminant}), направление — по
     * значению.
     */
    default boolean sameKind(Building other) {
        return this.getClass() == other.getClass()
                && this.direction().equals(other.direction());
    }

    /**
     * Создать НОВОЕ здание по выбранному инструменту и направлению.
     *
     * <p>Единственная «фабрика» зданий — удобно и предсказуемо (аналог
     * {@code Building::new} в Rust). {@code switch} исчерпывающий по {@code enum
     * Tool}: добавишь инструмент — компилятор потребует ветку и здесь.
     */
    static Building create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> new Miner(dir);
            case BELT -> new Belt(dir);
            case FURNACE -> new Furnace(dir);
            case CHEST -> new Chest();
            case ASSEMBLER -> new Assembler(dir);
            case SPLITTER -> new Splitter(dir);
            case UNDERGROUND -> new UndergroundBelt(dir);
            case LAB -> new Lab();
        };
    }
}

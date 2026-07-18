package com.rustorio.persist;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Снимок одного здания: тип, место, направление, накопления и — с версии 2 — предметы внутри.
 *
 * <p><b>Почему поля предметов добавлены, а не переписана вся запись.</b> Схема росла
 * <i>аддитивно</i>: v2 — это НАДМНОЖЕСТВО v1. Старые поля ({@code type/x/y/dir/amount})
 * на месте, новые ({@code machine/splitter/underground/minerOutput}) — {@code @Nullable} и в
 * v1-файлах просто отсутствуют. Поэтому сохранение версии 1 читается кодом версии 2 без
 * всякого преобразования: недостающие поля становятся {@code null}, то есть «предметов нет»
 * — ровно как и было в v1. Это самый дешёвый и самый надёжный вид миграции формата, и
 * стремиться надо именно к нему, а не к ломающему переписыванию структуры.
 *
 * <p>Предметы, едущие по ЛЕНТАМ, тут не хранятся: лента — это клетка транспортной линии, а
 * груз принадлежит линии, не клетке. Он лежит отдельным списком {@link BeltItemDto} в
 * {@link GameSnapshot}, потому что восстанавливается ПОСЛЕ сборки линий.
 *
 * @param amount       счётчик ящика / очки лаборатории (есть с v1)
 * @param machine      буфер печи/сборщика/лаборатории (v2); {@code null} у прочих
 * @param splitter     состояние развилки (v2); {@code null} у прочих
 * @param underground  предметы в трубе подземки (v2); {@code null} у прочих
 * @param minerOutput  готовая руда на выходе бура (v2); {@code null}, если выход пуст
 * @param buffer       содержимое буфера (v3); {@code null} у прочих
 */
public record BuildingDto(
        Tool type,
        int x,
        int y,
        @Nullable Direction dir,
        int amount,
        @Nullable MachineDto machine,
        @Nullable SplitterDto splitter,
        @Nullable List<Item> underground,
        @Nullable Item minerOutput,
        @Nullable List<Item> buffer) {
}

package com.rustorio.model;

import com.rustorio.core.Appearance;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Буфер: копит до {@link #CAPACITY} предметов и отдаёт их по одному соседу в сторону
 * {@code dir}. Порядок — «первым пришёл, первым ушёл» (очередь).
 *
 * <p><b>Это ОБРАЗЕЦ задания: как добавить здание, не открывая графику.</b> Посмотри, чего
 * этот класс НЕ делает: он ничего не знает про спрайты, цвета и libGDX. Он лишь описывает
 * себя в игровых терминах методом {@link #appearance()} — а слой {@code render} сам решит,
 * как это показать. Пока своего спрайта нет, буфер рисуется подписанной плашкой; стрелку,
 * иконку и счётчик рендер возьмёт из {@code appearance()}. <b>Ни одной строки в {@code
 * render} для этого здания писать не пришлось.</b>
 *
 * <p><b>Зачем буфер в игре.</b> Он сглаживает рывки: машина выдаёт продукт неравномерно, а
 * буфер впитывает всплеск и отдаёт ровным ручейком. При этом он НЕ ускоряет поток (отдаёт
 * максимум один предмет за тик, как любая машина) и НЕ создаёт предметы из воздуха — только
 * придерживает уже произведённые. Поэтому он честный житель фабрики, а не чит.
 *
 * <p><b>Как он встроен в общий протокол.</b> Ему не нужна отдельная фаза в симуляции: он
 * играет по обычным правилам {@link Building}. {@link #output()} называет головной предмет и
 * направление — дальше стандартная фаза передачи сама доставит его соседу и позовёт
 * {@link #removeOutput()}. Приём тоже обычный: сосед видит {@link #canAccept(Item)} и кладёт
 * через {@link #accept(Item)}. Никакого нового кода в {@code Simulation} — только данные и
 * договор.
 *
 * <hr>
 * <p><b>ТВОЁ ЗАДАНИЕ (по нарастанию, тестов нет — проверяй, запуская игру):</b>
 * <ol>
 *   <li><b>Логика.</b> Сделай так, чтобы буфер принимал НЕ ЛЮБОЙ предмет, а только тот тип,
 *       что уже лежит внутри (первый принятый задаёт «сорт» буфера; пустой принимает что
 *       угодно). Тронешь только {@link #canAccept(Item)} — снова ни строки в графике.</li>
 *   <li><b>Новое здание.</b> По этому же образцу добавь СВОЁ здание (например, «фильтр»,
 *       пропускающий предмет только если позади стоит бур). Пройди по ошибкам компилятора:
 *       {@code Tool}, {@link Building} (список {@code permits} и фабрика {@code create}),
 *       сохранение ({@code SaveService}/{@code LoadService}). Заметь: {@code render} среди
 *       ошибок НЕ будет.</li>
 *   <li><b>Бэкенд.</b> Сохранение буфера уже написано ниже — разберись, как именно его
 *       содержимое переживает F5/F9, и сделай то же для своего здания.</li>
 *   <li><b>Арт (необязательно).</b> Нарисуй буферу спрайт: положи картинку в {@code
 *       resources/}, заведи регион в {@code Textures} и одну ветку {@code case Buffer} в
 *       {@code BuildingRenderer.renderSprites}. Плашка сменится картинкой.</li>
 * </ol>
 */
public final class Buffer implements Building {

    /** Сколько предметов помещается. Структурная константа, не игровой баланс — живёт здесь. */
    private static final int CAPACITY = 8;

    private final Direction dir;
    /** Придержанные предметы: голова очереди уходит первой. */
    private final Deque<Item> items = new ArrayDeque<>();

    public Buffer(Direction dir) {
        this.dir = dir;
    }

    /** Восстановить буфер с содержимым — для загрузки сохранения (см. {@code LoadService}). */
    public Buffer(Direction dir, List<Item> contents) {
        this.dir = dir;
        this.items.addAll(contents); // порядок очереди сохраняется: голова остаётся головой
    }

    @Override
    public void update(TickContext ctx) {
        // Буфер пассивен: он ничего не «делает» за тик. Копит на приёме, отдаёт через output().
    }

    @Override
    public Optional<Handoff> output() {
        Item head = items.peek();
        return head == null ? Optional.empty() : Optional.of(new Handoff(head, dir));
    }

    @Override
    public boolean canAccept(Item item) {
        return items.size() < CAPACITY; // есть место — примем (ЗАДАНИЕ 1: добавь сюда проверку сорта)
    }

    @Override
    public void accept(Item item) {
        items.add(item);
    }

    @Override
    public void removeOutput() {
        items.poll(); // головной предмет ушёл соседу
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    @Override
    public Tool tool() {
        return Tool.BUFFER;
    }

    @Override
    public Appearance appearance() {
        // Единственная «дверь» в графику — и та без единого пикселя. Стрелка зелёная, пока
        // есть что отдавать; иконка — что уйдёт следующим; счётчик — сколько придержано.
        return Appearance.of("Buffer")
                .arrow(dir, !items.isEmpty())
                .icon(items.peek())
                .counter(items.size());
    }

    // ── Чтение для сохранения и тестов ────────────────────────────────
    public Direction dir() {
        return dir;
    }

    /** Содержимое буфера по порядку очереди — для снимка сохранения. */
    public List<Item> contents() {
        return new ArrayList<>(items);
    }
}

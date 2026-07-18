package com.rustorio.model;

import com.rustorio.core.Appearance;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Лаборатория: тратит предметы и копит очки исследований.
 *
 * <p><b>Зачем она в игре.</b> Это первый ответ на вопрос «а зачем я всё это строю». Пока
 * завод просто складывает шестерёнки в ящик, у бесконечной песочницы нет цели. Лаборатория
 * превращает произведённое в <b>прогресс</b>: очки открывают технологии (задача C4), а те —
 * апгрейды (C5).
 *
 * <p><b>Почему она не влезала в старую модель.</b> До задачи C2 рецепт был «один предмет →
 * один предмет». Лаборатория ест предметы, а выдаёт НЕ предмет, а очки — их некуда положить
 * в выход, потому что очки не ездят по лентам. Именно из-за неё рецепты и пришлось обобщить.
 *
 * <p><b>Почему она всё-таки использует общее {@link ProcessKernel}.</b> Со стороны кажется,
 * что у лаборатории «своя» логика. Но приглядитесь: она копит сырьё на складе, выбирает
 * подходящий рецепт, тратит время на цикл, а по завершении списывает ингредиенты — это
 * ровно то, что делают печь и сборщик. Отличие только в том, ЧТО получается в конце: у них
 * предмет, у неё — число. Заводить ради этого отдельное ядро значило бы скопировать склад,
 * выбор рецепта и прогресс. Композиция дешевле наследования и дешевле копипасты.
 *
 * <p>Наружу лаборатория, как и ящик, не отдаёт ничего — она конечная точка цепочки.
 */
public final class Lab implements Building {

    private final ProcessKernel kernel;

    /** Накопленные очки исследований. Их считывает {@code Research} (задача C4). */
    private int points;

    public Lab() {
        this.kernel = new ProcessKernel(Tool.LAB);
    }

    /**
     * Восстановить лабораторию с накопленными, но ещё не забранными очками — для загрузки
     * сохранения. На практике {@code Research} забирает очки каждый тик, поэтому в норме
     * тут ноль; конструктор существует ради полноты снимка.
     */
    public Lab(int points) {
        this();
        this.points = points;
    }

    /** Полный конструктор загрузки (B2): очки + буфер изучаемого сырья + очередь готового. */
    public Lab(int points, Map<Item, Integer> stock, List<Item> ready) {
        this.kernel = new ProcessKernel(Tool.LAB, stock, ready);
        this.points = points;
    }

    @Override
    public void update(TickContext ctx) {
        kernel.update(ctx);
        // Забираем очки, а не подсматриваем: иначе один и тот же цикл начислился бы дважды.
        points += kernel.drainScience();
    }

    @Override
    public Optional<Handoff> output() {
        return Optional.empty(); // конечная точка: наружу ничего не уходит
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
        // Нечего убирать: лаборатория не отдаёт предметы.
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.empty(); // у лаборатории нет направления
    }

    @Override
    public Tool tool() {
        return Tool.LAB;
    }

    @Override
    public Appearance appearance() {
        // Нет направления — нет стрелки. Показываем прогресс исследования и счётчик очков.
        return Appearance.of("Lab")
                .progress(progressFraction())
                .counter(points());
    }

    // ── Чтение для отрисовки и прогрессии ─────────────────────────────

    /** Сколько очков исследований накоплено. */
    public int points() {
        return points;
    }

    // ── Снимок для сохранения (B2) ────────────────────────────────────
    public Map<Item, Integer> stockSnapshot() {
        return kernel.snapshotStock();
    }

    public List<Item> readySnapshot() {
        return kernel.snapshotReady();
    }

    /** Забрать накопленные очки в общий счёт игры (задача C4). */
    public int drainPoints() {
        int drained = points;
        points = 0;
        return drained;
    }

    /** Что показать на клетке: сырьё, которое лаборатория сейчас изучает. */
    public Optional<Item> displayItem() {
        return kernel.displayItem();
    }

    /** Полоска прогресса текущего цикла исследования. */
    public float progressFraction() {
        return kernel.progressFraction();
    }
}

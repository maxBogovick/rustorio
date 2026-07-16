package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Транспортная линия: непрерывная цепочка лент ОДНОГО направления — как единая
 * сущность, внутри которой едут предметы.
 *
 * <p><b>Главная идея.</b> Раньше сто клеток ленты были ста зданиями, каждое с одним
 * предметом, и предмет раз в тик перепрыгивал в соседнее. Теперь цепочка — один
 * объект со списком предметов, у каждого своя позиция. Отсюда три выигрыша:
 * поток становится плотным ({@link Config#SLOTS_PER_TILE} предметов на клетку),
 * движение линии — одна операция вместо сотни, а у предмета появляется дробная
 * позиция, то есть плавная анимация достаётся бесплатно.
 *
 * <p><b>Порядок в {@link #items}: от ГОЛОВЫ к хвосту (по убыванию слота).</b> Это
 * не украшательство: двигать предметы можно только с головы — передний едет
 * первым, а следующий уже знает, докуда тот уехал. Пойдёшь с хвоста — задний
 * проедет сквозь переднего (см. {@link #step}).
 *
 * <p>Позиции — целые числа слотов, дробей в симуляции нет (см. {@link BeltItem}).
 */
public final class BeltSegment implements BeltView {

    private static final int SLOTS = Config.SLOTS_PER_TILE;

    private final Direction dir;
    /** Клетки по порядку: [0] — хвост, [size-1] — голова (куда лента везёт). */
    private final List<Cell> tiles;
    /** Предметы, отсортированные по УБЫВАНИЮ слота: items[0] — ближайший к голове. */
    private final List<BeltItem> items;

    BeltSegment(Direction dir, List<Cell> tiles, List<BeltItem> items) {
        this.dir = dir;
        this.tiles = List.copyOf(tiles);
        this.items = new ArrayList<>(items);
        this.items.sort(Collections.reverseOrder(java.util.Comparator.comparingInt(i -> i.slot)));
    }

    // ── Чтение ────────────────────────────────────────────────────────

    public Direction dir() {
        return dir;
    }

    public List<Cell> tiles() {
        return tiles;
    }

    public int tileCount() {
        return tiles.size();
    }

    /** Длина линии в слотах: слоты нумеруются 0..lengthSlots()-1 от хвоста. */
    public int lengthSlots() {
        return tiles.size() * SLOTS;
    }

    public int itemCount() {
        return items.size();
    }

    /** Сколько предметов стоит на клетке с номером {@code tileIndex} внутри линии. */
    public int countItemsOnTile(int tileIndex) {
        int count = 0;
        for (BeltItem it : items) {
            if (it.slot / SLOTS == tileIndex) {
                count++;
            }
        }
        return count;
    }

    /** Передний предмет на клетке (для отрисовки спрайта и старых тестов). */
    Optional<Item> itemOnTile(int tileIndex) {
        for (BeltItem it : items) { // items идут от головы — первый найденный и есть передний
            if (it.slot / SLOTS == tileIndex) {
                return Optional.of(it.item);
            }
        }
        return Optional.empty();
    }

    // ── Движение (сердце линии) ───────────────────────────────────────

    /**
     * Сдвинуть все предметы на один тик.
     *
     * <p>Идём С ГОЛОВЫ: передний предмет едет первым, и его новая позиция
     * становится потолком для следующего. Так предметы физически не могут
     * обогнать друг друга и слиться — без единой проверки «а не наехал ли я».
     */
    void step(int slotsPerTick) {
        int limit = lengthSlots() - 1; // дальше головы не уехать: там линия кончается
        for (BeltItem it : items) {
            it.prevSlot = it.slot;
            it.slot = Math.min(it.slot + slotsPerTick, limit);
            limit = it.slot - 1; // следующий встанет в лучшем случае впритык
        }
    }

    // ── Приём и отдача предметов (стык с машинами) ────────────────────

    boolean canInsertAt(int slot) {
        if (slot < 0 || slot >= lengthSlots()) {
            return false;
        }
        for (BeltItem it : items) {
            if (it.slot == slot) {
                return false;
            }
        }
        return true;
    }

    void insert(Item item, int slot) {
        if (!canInsertAt(slot)) {
            throw new IllegalStateException("слот " + slot + " занят или вне линии");
        }
        BeltItem fresh = new BeltItem(item, slot);
        int pos = 0;
        while (pos < items.size() && items.get(pos).slot > slot) {
            pos++;
        }
        items.add(pos, fresh); // сохраняем порядок «от головы»
    }

    /** Предмет, доехавший до последнего слота: его и заберёт машина за головой. */
    Optional<Item> headItem() {
        if (items.isEmpty()) {
            return Optional.empty();
        }
        BeltItem front = items.getFirst();
        return front.slot == lengthSlots() - 1 ? Optional.of(front.item) : Optional.empty();
    }

    void removeHeadItem() {
        if (headItem().isPresent()) {
            items.removeFirst();
        }
    }

    // ── Отрисовка (единственное место, где появляется дробь) ───────────

    @Override
    public List<BeltItemPos> itemPositions(float alpha) {
        List<BeltItemPos> out = new ArrayList<>(items.size());
        Cell tail = tiles.getFirst();
        for (BeltItem it : items) {
            // Между тиками показываем предмет между прошлой и текущей позицией.
            float slot = it.prevSlot + (it.slot - it.prevSlot) * alpha;
            // Центр слота в клетках от хвостового КРАЯ линии.
            float dist = (slot + 0.5f) / SLOTS;
            // Клетка-хвост занимает dist ∈ [0,1], её центр — dist = 0.5.
            float x = tail.x() + dir.dx() * (dist - 0.5f);
            float y = tail.y() + dir.dy() * (dist - 0.5f);
            out.add(new BeltItemPos(it.item, x, y));
        }
        return out;
    }

    // ── Инварианты и отладка ──────────────────────────────────────────

    /**
     * «Линия не сошла с ума». Проверяется в тестах после КАЖДОЙ операции —
     * именно так ловятся потеря и удвоение предметов при перестройке линии.
     */
    void assertInvariants() {
        if (tiles.isEmpty()) {
            throw new IllegalStateException("сегмент без клеток");
        }
        // Клетки идут подряд в направлении dir.
        for (int i = 1; i < tiles.size(); i++) {
            Cell prev = tiles.get(i - 1);
            Cell cur = tiles.get(i);
            if (cur.x() != prev.x() + dir.dx() || cur.y() != prev.y() + dir.dy()) {
                throw new IllegalStateException("клетки сегмента не подряд: " + tiles);
            }
        }
        int last = Integer.MAX_VALUE;
        for (BeltItem it : items) {
            if (it.slot < 0 || it.slot >= lengthSlots()) {
                throw new IllegalStateException("слот вне линии: " + it.slot + " из " + lengthSlots());
            }
            if (it.slot >= last) {
                throw new IllegalStateException("порядок нарушен или два предмета в одном слоте: " + this);
            }
            last = it.slot;
        }
    }

    /** Печать вида {@code [.o.O] E} — «картинка» линии, незаменима при отладке. */
    @Override
    public String toString() {
        char[] slots = new char[lengthSlots()];
        java.util.Arrays.fill(slots, '.');
        for (BeltItem it : items) {
            slots[it.slot] = it.slot == lengthSlots() - 1 ? 'O' : 'o';
        }
        return "[" + new String(slots) + "] " + dir.shortName() + " " + tiles.getFirst();
    }

    // ── Внутреннее: доступ к предметам для перестройки сети ────────────
    List<BeltItem> rawItems() {
        return items;
    }
}

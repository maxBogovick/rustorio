package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Все транспортные линии мира и правила их сборки и разборки.
 *
 * <p><b>Зачем отдельный класс.</b> Постройка или снос ОДНОЙ клетки ленты может
 * разрезать линию надвое или склеить две в одну. Это самая тонкая логика в игре:
 * именно здесь предметы теряются и удваиваются. Она собрана в одном месте, а не
 * размазана по {@link World} и {@link Belt}, — чтобы её можно было целиком
 * прочитать и целиком покрыть тестами.
 *
 * <p><b>Почему линия пересобирается целиком, а не «дописывается с краю».</b>
 * Склейка, удлинение и разрез — это по сути одна операция: «взять такие-то клетки
 * и такие-то предметы и сделать из них линию». Один путь исполнения = одно место,
 * где может быть ошибка. Сегменты короткие, а постройка происходит по клику мыши,
 * а не каждый тик, — цена пересборки исчезающе мала.
 *
 * <p>Порядок сегментов — {@link LinkedHashSet}, то есть детерминированный:
 * симуляция обязана вести себя одинаково при одинаковых действиях игрока.
 */
public final class BeltNetwork {

    private static final int SLOTS = Config.SLOTS_PER_TILE;

    private final World world;
    private final Set<BeltSegment> segments = new LinkedHashSet<>();

    BeltNetwork(World world) {
        this.world = world;
    }

    public Collection<BeltSegment> segments() {
        return Collections.unmodifiableCollection(segments);
    }

    /** Сколько всего предметов едет по лентам мира (инвариант в тестах). */
    public int itemCount() {
        int total = 0;
        for (BeltSegment segment : segments) {
            total += segment.itemCount();
        }
        return total;
    }

    /** Один шаг: каждая линия двигает свои предметы. */
    public void step(int slotsPerTick) {
        for (BeltSegment segment : segments) {
            segment.step(slotsPerTick);
        }
    }

    // ── Постройка ─────────────────────────────────────────────────────

    /**
     * На клетку поставили ленту. Четыре случая:
     * <ol>
     *   <li>соседей нет — новая линия из одной клетки;</li>
     *   <li>есть линия СЗАДИ (везёт в нас) — клетка становится её новой головой;</li>
     *   <li>есть линия СПЕРЕДИ (мы повезём в неё) — клетка становится её хвостом;</li>
     *   <li>есть и сзади, и спереди — <b>СКЛЕЙКА</b>: две линии и наша клетка → одна.</li>
     * </ol>
     * Случай 4 — тот самый, где предметы удваиваются или пропадают: позиции
     * предметов передней линии обязаны сдвинуться на длину задней плюс нашу клетку.
     */
    void onBeltPlaced(Cell cell, Belt belt) {
        Direction dir = belt.dir();
        BeltSegment back = segmentOfBeltAt(cell.x() - dir.dx(), cell.y() - dir.dy(), dir);
        BeltSegment front = segmentOfBeltAt(cell.x() + dir.dx(), cell.y() + dir.dy(), dir);

        List<Cell> tiles = new ArrayList<>();
        List<BeltItem> items = new ArrayList<>();

        if (back != null) {
            tiles.addAll(back.tiles());
            items.addAll(back.rawItems()); // позиции задней линии НЕ меняются: её хвост остаётся хвостом
            segments.remove(back);
        }

        int newTileIndex = tiles.size();
        tiles.add(cell);

        if (front != null) {
            // Передняя линия уезжает вперёд на (длина задней + наша клетка) клеток.
            int shift = (newTileIndex + 1) * SLOTS;
            for (BeltItem it : front.rawItems()) {
                it.slot += shift;
                it.prevSlot += shift; // иначе рендер дёрнет предмет назад на один кадр
                items.add(it);
            }
            tiles.addAll(front.tiles());
            segments.remove(front);
        }

        createSegment(dir, tiles, items);
    }

    // ── Снос ──────────────────────────────────────────────────────────

    /**
     * С клетки убрали ленту: линия разрезается надвое.
     *
     * <p>Предметы делятся по слоту. Границы {@code <} и {@code >=} здесь —
     * то самое место, где рождается «ошибка на единицу»: предмет, попавший в обе
     * половины, — это удвоение, не попавший ни в одну — потеря. Предмет, стоявший
     * НА снесённой клетке, уничтожается вместе с ней — это законно: игрок снёс
     * ленту с грузом.
     */
    void onBeltRemoved(Cell cell, Belt belt) {
        BeltSegment segment = belt.segment();
        if (segment == null) {
            return;
        }
        int k = belt.indexInSegment();
        List<Cell> tiles = segment.tiles();
        segments.remove(segment);
        belt.detach();

        int cutFrom = k * SLOTS;          // первый слот снесённой клетки
        int cutTo = (k + 1) * SLOTS;      // первый слот ЗА снесённой клеткой

        List<BeltItem> leftItems = new ArrayList<>();
        List<BeltItem> rightItems = new ArrayList<>();
        for (BeltItem it : segment.rawItems()) {
            if (it.slot < cutFrom) {
                leftItems.add(it); // позиции не меняются: хвост остался на месте
            } else if (it.slot >= cutTo) {
                it.slot -= cutTo;
                it.prevSlot = Math.max(0, it.prevSlot - cutTo);
                rightItems.add(it);
            }
            // иначе предмет стоял на снесённой клетке — уничтожен
        }

        List<Cell> leftTiles = new ArrayList<>(tiles.subList(0, k));
        List<Cell> rightTiles = new ArrayList<>(tiles.subList(k + 1, tiles.size()));

        if (!leftTiles.isEmpty()) {
            createSegment(segment.dir(), leftTiles, leftItems);
        }
        if (!rightTiles.isEmpty()) {
            createSegment(segment.dir(), rightTiles, rightItems);
        }
    }

    // ── Общая сборка линии ────────────────────────────────────────────

    /**
     * Собрать линию и — САМОЕ ВАЖНОЕ — перепривязать к ней ВСЕ её клетки.
     *
     * <p>Клетка, оставшаяся смотреть на старый сегмент, — худший баг этой задачи:
     * предмет читается из двух мест (нарисуется и поедет дважды) или из мёртвого
     * объекта (пропадёт), причём тесты на движение этого не увидят. Поэтому
     * привязка живёт здесь, в единственном месте, через которое проходят все пути.
     */
    private void createSegment(Direction dir, List<Cell> tiles, List<BeltItem> items) {
        BeltSegment segment = new BeltSegment(dir, tiles, items);
        segments.add(segment);
        for (int i = 0; i < tiles.size(); i++) {
            beltAt(tiles.get(i)).attach(segment, i);
        }
    }

    private Belt beltAt(Cell cell) {
        Building building = world.tile(cell.x(), cell.y()).building();
        if (!(building instanceof Belt belt)) {
            throw new IllegalStateException("на клетке сегмента нет ленты: " + cell);
        }
        return belt;
    }

    /** Сегмент ленты на соседней клетке, если это лента ТОГО ЖЕ направления. */
    private @Nullable BeltSegment segmentOfBeltAt(int x, int y, Direction dir) {
        if (!world.inBounds(x, y)) {
            return null;
        }
        return world.tile(x, y).building() instanceof Belt belt && belt.dir() == dir
                ? belt.segment()
                : null;
    }
}

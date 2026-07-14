# 📄 Решение — Р7 — `BeltNetwork`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

```java
public final class BeltNetwork {

    private static final int SLOTS = Config.SLOTS_PER_TILE;

    private final World world;
    // LinkedHashSet, а не HashSet: порядок обхода линий должен быть предсказуемым,
    // иначе симуляция перестанет быть воспроизводимой.
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

    // ── Постройка: четыре случая, один код ────────────────────────────
    void onBeltPlaced(Cell cell, Belt belt) {
        Direction dir = belt.dir();
        BeltSegment back  = segmentOfBeltAt(cell.x() - dir.dx(), cell.y() - dir.dy(), dir);
        BeltSegment front = segmentOfBeltAt(cell.x() + dir.dx(), cell.y() + dir.dy(), dir);

        List<Cell> tiles = new ArrayList<>();
        List<BeltItem> items = new ArrayList<>();

        if (back != null) {
            tiles.addAll(back.tiles());
            items.addAll(back.rawItems());  // позиции задней линии НЕ меняются: её хвост остался хвостом
            segments.remove(back);
        }

        int newTileIndex = tiles.size();
        tiles.add(cell);

        if (front != null) {
            // Передняя линия уезжает вперёд на (длина задней + наша клетка) клеток.
            int shift = (newTileIndex + 1) * SLOTS;
            for (BeltItem it : front.rawItems()) {
                it.slot += shift;
                it.prevSlot += shift;   // ЛОВУШКА 1: иначе рендер дёрнет предмет назад на один кадр
                items.add(it);
            }
            tiles.addAll(front.tiles());
            segments.remove(front);
        }

        createSegment(dir, tiles, items);
    }

    // ── Снос: разрез ──────────────────────────────────────────────────
    void onBeltRemoved(Cell cell, Belt belt) {
        BeltSegment segment = belt.segment();
        if (segment == null) {
            return;
        }
        int k = belt.indexInSegment();
        List<Cell> tiles = segment.tiles();
        segments.remove(segment);
        belt.detach();

        int cutFrom = k * SLOTS;        // первый слот снесённой клетки
        int cutTo = (k + 1) * SLOTS;    // первый слот ЗА снесённой клеткой

        List<BeltItem> leftItems = new ArrayList<>();
        List<BeltItem> rightItems = new ArrayList<>();
        for (BeltItem it : segment.rawItems()) {
            if (it.slot < cutFrom) {
                leftItems.add(it);      // позиции не меняются: хвост остался на месте
            } else if (it.slot >= cutTo) {
                it.slot -= cutTo;
                it.prevSlot = Math.max(0, it.prevSlot - cutTo);
                rightItems.add(it);
            }
            // иначе предмет стоял на снесённой клетке — уничтожен
        }

        List<Cell> leftTiles  = new ArrayList<>(tiles.subList(0, k));
        List<Cell> rightTiles = new ArrayList<>(tiles.subList(k + 1, tiles.size()));

        if (!leftTiles.isEmpty()) {
            createSegment(segment.dir(), leftTiles, leftItems);
        }
        if (!rightTiles.isEmpty()) {
            createSegment(segment.dir(), rightTiles, rightItems);
        }
    }

    /**
     * Собрать линию и — САМОЕ ВАЖНОЕ — перепривязать к ней ВСЕ её клетки (ЛОВУШКА 2).
     * Единственный путь, через который проходят все перестройки, — значит, забыть
     * привязку физически негде.
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
```

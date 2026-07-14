# 📄 Решение — Р6 — `Belt`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

```java
public final class Belt implements Building {

    private final Direction dir;
    /** Линия, в которую входит эта клетка (ставится при постройке). */
    private @Nullable BeltSegment segment;
    /** Какая я по счёту клетка в своей линии (0 — хвост). */
    private int indexInSegment = -1;

    public Belt(Direction dir) {
        this.dir = dir;
    }

    // ── Привязка к линии (этим управляет только BeltNetwork) ──────────
    void attach(BeltSegment segment, int indexInSegment) {
        this.segment = segment;
        this.indexInSegment = indexInSegment;
    }

    void detach() {
        this.segment = null;
        this.indexInSegment = -1;
    }

    public @Nullable BeltSegment segment() { return segment; }
    public int indexInSegment()            { return indexInSegment; }

    @Override
    public void update(float dt, boolean hasOre) {
        // Лента сама не работает: предметы двигает фаза лент в симуляции.
    }

    /**
     * Отдаёт наружу только ГОЛОВНАЯ клетка линии и только когда предмет доехал до
     * последнего слота. Иначе предмет «вылезал» бы из середины ленты.
     */
    @Override
    public Optional<Handoff> output() {
        if (segment == null || indexInSegment != segment.tileCount() - 1) {
            return Optional.empty();
        }
        return segment.headItem().map(item -> new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item incoming) {
        return segment != null && segment.canInsertAt(entrySlot());
    }

    @Override
    public void accept(Item incoming) {
        if (segment == null) {
            throw new IllegalStateException("лента не привязана к линии");
        }
        segment.insert(incoming, entrySlot());
    }

    @Override
    public void removeOutput() {
        if (segment != null) {
            segment.removeHeadItem();
        }
    }

    @Override
    public Optional<Direction> direction() { return Optional.of(dir); }

    /** Предмет попадает на ленту в ПЕРВЫЙ слот своей клетки — «с заднего края». */
    private int entrySlot() {
        return indexInSegment * Config.SLOTS_PER_TILE;
    }

    public Direction dir() { return dir; }

    /** Передний предмет на ЭТОЙ клетке, если есть. */
    public Optional<Item> item() {
        return segment == null ? Optional.empty() : segment.itemOnTile(indexInSegment);
    }
}
```

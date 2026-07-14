# 📄 Решение — Р3 — `BeltSegment`, часть 1

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

```java
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
        this.items.sort(Collections.reverseOrder(Comparator.comparingInt(i -> i.slot)));
    }

    public Direction dir()    { return dir; }
    public List<Cell> tiles() { return tiles; }
    public int tileCount()    { return tiles.size(); }
    public int itemCount()    { return items.size(); }

    /** Длина линии в слотах: слоты нумеруются 0..lengthSlots()-1 от хвоста. */
    public int lengthSlots()  { return tiles.size() * SLOTS; }

    /** Сколько предметов стоит на клетке с номером tileIndex внутри линии. */
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
        for (BeltItem it : items) {   // items идут от головы — первый найденный и есть передний
            if (it.slot / SLOTS == tileIndex) {
                return Optional.of(it.item);
            }
        }
        return Optional.empty();
    }

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
        items.add(pos, fresh);   // сохраняем порядок «от головы»
    }

    /** Внутреннее: доступ к предметам нужен BeltNetwork при склейке и разрезе. */
    List<BeltItem> rawItems() { return items; }
}
```

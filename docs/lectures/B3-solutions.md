# B3 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник рабочей тетради [B3-workbook.md](B3-workbook.md).
>
> Сюда заходят **по ссылке из конкретного шага**, а не читают подряд. Прочитаете подряд — потеряете
> всю задачу: она учит не тем, что вы увидите готовый код, а тем, что вы напишете свой и он не
> заработает.
>
> На каждый шаг две ступени: **П** — подсказка (направление, не ответ), **Р** — реализация целиком.
> Всегда пробуйте П прежде, чем Р. И самое полезное — открыть Р **после** того, как ваше решение уже
> работает, и сравнить: почти всегда найдётся деталь, до которой вы не додумались, или, наоборот,
> ваша окажется лучше.

---

## Р1 — Две константы

```java
    // ── Ленты (транспортные линии) ────────────────────────────────────
    /**
     * На сколько «слотов» делится клетка ленты. Слот — минимальная позиция
     * предмета; позиция хранится ЦЕЛЫМ числом слотов, а не дробью, чтобы не
     * копить ошибку float и не терять воспроизводимость симуляции.
     *
     * <p>2 слота = две порции груза на клетку (аналог одной полосы в Factorio).
     */
    public static final int SLOTS_PER_TILE = 2;
    /**
     * Сколько слотов предмет проезжает за тик.
     *
     * <p>Ровно SLOTS_PER_TILE: раньше предмет проезжал клетку за тик, и скорость
     * игры после перехода на линии НЕ должна измениться — иначе непонятно, что
     * сломал рефакторинг, а что «так и задумано».
     */
    public static final int BELT_SLOTS_PER_TICK = 2;
```

---

## П2 — `BeltItem`

Класс делайте **пакетно-приватным** (без `public`): это внутренняя кухня модели лент, наружу её знать
не нужно. Поля тоже пакетно-приватные — внутри пакета к ним обращаются `BeltSegment` и `BeltNetwork`,
и геттеры тут только мешали бы.

В конструкторе `prevSlot = slot`: предмет, который только что положили, никуда не «ехал».

---

## Р2 — `BeltItem`

```java
package com.rustorio.model;

import com.rustorio.core.Item;

/**
 * Предмет, едущий по транспортной линии: что едет и на каком слоте стоит.
 *
 * <p>Позиция — ЦЕЛОЕ число слотов от хвоста сегмента, а не дробь. Дробные числа
 * копят ошибку (прибавьте 0.1 сто раз — получите 9.99999998), и через час игры
 * предметы начали бы то слипаться, то расходиться; кроме того, дроби ломают
 * воспроизводимость симуляции, без которой не будет ни сохранений, ни надёжных
 * тестов. Дробь появляется ТОЛЬКО в отрисовке — см. BeltSegment#itemPositions.
 *
 * <p>prevSlot — позиция на прошлом тике. Она нужна исключительно рендеру: между
 * тиками он показывает предмет ГДЕ-ТО МЕЖДУ prevSlot и slot, и получается плавное
 * движение. Симуляция им не пользуется.
 */
final class BeltItem {

    final Item item;
    /** Текущая позиция: 0 — хвост сегмента. */
    int slot;
    /** Позиция на прошлом тике (только для плавной отрисовки). */
    int prevSlot;

    BeltItem(Item item, int slot) {
        this.item = item;
        this.slot = slot;
        this.prevSlot = slot;
    }
}
```

---

## П3 — `BeltSegment`, часть 1

- Слоты нумеруются `0 .. lengthSlots()-1` от хвоста. Клетка с номером `i` владеет слотами
  `i*SLOTS .. i*SLOTS + SLOTS - 1`. Обратно: слот `s` лежит на клетке `s / SLOTS` — целочисленное
  деление здесь ваш друг.
- `canInsertAt`: проверьте, что слот в диапазоне **и** что ни у одного предмета нет такого слота.
- `insert`: найдите позицию в списке, где слоты перестают быть больше вашего, и вставьте туда
  (`items.add(pos, fresh)`).
- В конструкторе на всякий случай **отсортируйте** входной список по убыванию слота: не полагайтесь
  на то, что вызывающий ничего не перепутал.

---

## Р3 — `BeltSegment`, часть 1

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

---

## П4 — Движение

Скелет:

```java
void step(int slotsPerTick) {
    int limit = /* самый дальний доступный слот */;
    for (BeltItem it : items) {          // items идут ОТ ГОЛОВЫ
        it.prevSlot = it.slot;           // запомнили, откуда едем (это нужно рендеру!)
        it.slot = /* не дальше limit */;
        limit = /* следующий не ближе, чем впритык за этим */;
    }
}
```

Три вопроса — ответите на них, и метод написан:

1. Чему равен `limit` для **самого первого** (переднего) предмета?
2. Как ограничить бросок предмета этим `limit`? (`Math.min` — и всё.)
3. Каким становится `limit` для следующего, если этот встал на слот `s`?

И одна деталь: `prevSlot` обновляйте **у всех** предметов, включая те, что не сдвинулись, — иначе
рендер будет думать, что стоящий предмет едет.

---

## Р4 — Движение, инварианты, печать

```java
    /**
     * Сдвинуть все предметы на один тик.
     *
     * <p>Идём С ГОЛОВЫ: передний едет первым, и его новая позиция становится потолком
     * для следующего. Так предметы физически не могут обогнать друг друга — без единой
     * проверки «а не наехал ли я».
     */
    void step(int slotsPerTick) {
        int limit = lengthSlots() - 1;         // дальше головы не уехать: там линия кончается
        for (BeltItem it : items) {
            it.prevSlot = it.slot;
            it.slot = Math.min(it.slot + slotsPerTick, limit);
            limit = it.slot - 1;               // следующий встанет в лучшем случае впритык
        }
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

    /** «Линия не сошла с ума». Вызывать в тестах после КАЖДОЙ операции. */
    void assertInvariants() {
        if (tiles.isEmpty()) {
            throw new IllegalStateException("сегмент без клеток");
        }
        for (int i = 1; i < tiles.size(); i++) {          // клетки идут подряд в направлении dir
            Cell prev = tiles.get(i - 1);
            Cell cur = tiles.get(i);
            if (cur.x() != prev.x() + dir.dx() || cur.y() != prev.y() + dir.dy()) {
                throw new IllegalStateException("клетки сегмента не подряд: " + tiles);
            }
        }
        int last = Integer.MAX_VALUE;
        for (BeltItem it : items) {
            if (it.slot < 0 || it.slot >= lengthSlots()) {
                throw new IllegalStateException("слот вне линии: " + it.slot);
            }
            if (it.slot >= last) {
                throw new IllegalStateException(
                        "порядок нарушен или два предмета в одном слоте: " + this);
            }
            last = it.slot;
        }
    }

    /** Печать вида [.o.O] E — «картинка» линии, незаменима при отладке. */
    @Override
    public String toString() {
        char[] slots = new char[lengthSlots()];
        Arrays.fill(slots, '.');
        for (BeltItem it : items) {
            slots[it.slot] = it.slot == lengthSlots() - 1 ? 'O' : 'o';
        }
        return "[" + new String(slots) + "] " + dir.shortName() + " " + tiles.getFirst();
    }
```

---

## П5 — `itemPositions`

Считайте в три хода:

1. **Дробный слот:** `slot = prevSlot + (slot - prevSlot) * alpha`. При `alpha = 0` получится прошлая
   позиция, при `alpha = 1` — текущая. Это линейная интерполяция.
2. **Из слотов в клетки:** центр слота `s` находится на расстоянии `(s + 0.5) / SLOTS` клеток от
   **хвостового края** линии. Почему `+0.5`: предмет стоит в **середине** своего слота, а не на его
   границе.
3. **Из «расстояния от края» в координаты поля:** клетка-хвост занимает расстояние от 0 до 1, и её
   **центр** — это 0.5. Значит `x = хвост.x + dx * (dist - 0.5)`. Проверьте сами: при `dist = 0.5`
   получается ровно центр хвостовой клетки — то, что надо.

---

## Р5 — `BeltView`, `BeltItemPos`, `itemPositions`

```java
public interface BeltView {

    /**
     * Позиции предметов для кадра.
     *
     * @param alpha доля прожитого тика (0..1). Симуляция шагает раз в Config.TICK, а
     *              кадров между тиками много: alpha говорит, насколько предмет уже уехал
     *              от прошлой позиции к текущей. Это и даёт плавность.
     */
    List<BeltItemPos> itemPositions(float alpha);
}
```

```java
/**
 * Где нарисовать предмет, едущий по ленте: непрерывные координаты поля.
 *
 * <p>Целое значение x/y — это ЦЕНТР клетки, дробное — точка между клетками. Это
 * по-прежнему координаты ДОМЕНА (клетки), а не пиксели: перевод в пиксели — забота
 * слоя отрисовки, домен про экран ничего не знает.
 */
public record BeltItemPos(Item item, float x, float y) { }
```

```java
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
```

---

## П6 — `Belt`

- `output()`: сначала проверьте `segment != null`, потом «я ли голова»
  (`indexInSegment == segment.tileCount() - 1`), и только потом спросите у линии `headItem()`.
- `canAccept()` / `accept()`: работают со «слотом въезда» — заведите приватный `entrySlot()`, чтобы
  формула `indexInSegment * SLOTS_PER_TILE` жила в одном месте.
- `item()`: делегируйте линии — `segment.itemOnTile(indexInSegment)`. Метод нужен рендеру и старым
  тестам, поэтому **не удаляйте его**, даже когда предметы уедут в линию.

---

## Р6 — `Belt`

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

---

## П7 — `BeltNetwork`

Каркас постройки:

```java
void onBeltPlaced(Cell cell, Belt belt) {
    Direction dir = belt.dir();
    BeltSegment back  = /* линия ленты того же направления сзади, или null */;
    BeltSegment front = /* ... спереди, или null */;

    List<Cell> tiles = new ArrayList<>();
    List<BeltItem> items = new ArrayList<>();

    if (back != null)  { /* клетки back + его предметы БЕЗ изменений; back убрать из набора */ }

    int newTileIndex = tiles.size();     // ← это номер НАШЕЙ клетки в новой линии
    tiles.add(cell);

    if (front != null) { /* предметы front сдвинуть на (newTileIndex + 1) * SLOTS; клетки добавить */ }

    createSegment(dir, tiles, items);    // одна функция на все случаи
}
```

Заметьте: **отдельных веток «случай 2» и «случай 3» не понадобилось** — они получаются сами, если
`back` или `front` окажется `null`. Четыре случая схлопнулись в один код. Это и есть признак того,
что формулировка найдена верная.

Для сноса: посчитайте `cutFrom = k * SLOTS` и `cutTo = (k+1) * SLOTS`, пройдите по предметам и
разложите их по трём корзинам (левая / правая / уничтожен), потом соберите из непустых половинок линии
той же функцией `createSegment`.

---

## Р7 — `BeltNetwork`

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

---

## Р8 — Подключение к игре

### `World`

```java
    /** Транспортные линии мира: их сборку и разборку ведёт только она. */
    private final BeltNetwork belts = new BeltNetwork(this);

    public BeltNetwork belts() { return belts; }

    public void place(int x, int y, Building building) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tiles[idx(x, y)];
        Building existing = tile.building();
        if (existing != null) {
            if (existing.getClass() != building.getClass()) {
                return;                                  // другой тип — не трогаем
            }
            if (existing.sameKind(building)) {
                return;                                  // ровно такое же — не пересоздаём
            }
            // Тот же тип, другое направление: старую ленту сперва честно вынимаем из её
            // линии (иначе линия осталась бы ссылаться на снесённую клетку).
            if (existing instanceof Belt oldBelt) {
                belts.onBeltRemoved(new Cell(x, y), oldBelt);
            }
        }
        tile.setBuilding(building);
        if (building instanceof Belt newBelt) {
            belts.onBeltPlaced(new Cell(x, y), newBelt);
        }
    }

    public void remove(int x, int y) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tiles[idx(x, y)];
        // Снимаем ленту с линии ДО того, как убрать её с клетки: разрезу нужна и сама
        // лента (её место в линии), и её соседи на своих местах.
        if (tile.building() instanceof Belt belt) {
            belts.onBeltRemoved(new Cell(x, y), belt);
        }
        tile.setBuilding(null);
    }
```

### `Systems`

```java
    public static void step(World world, float dt) {
        runMachines(world, dt);
        moveBelts(world);      // ← новая фаза
        moveItems(world);
    }

    /**
     * Система №1.5: предметы едут ВНУТРИ транспортных линий.
     *
     * <p>Отдельная фаза, потому что лента больше не «здание с одним предметом»:
     * непрерывная цепочка — одна линия, и двигать её надо целиком, с головы. Стык
     * «машина ↔ лента» при этом остался прежним и обслуживается moveItems — поэтому
     * бур, печь и сборщик не знают, что ленты переписаны.
     */
    private static void moveBelts(World world) {
        world.belts().step(Config.BELT_SLOTS_PER_TICK);
    }
```

### `GameState`

```java
    /**
     * Доля прожитого тика (0..1) — нужна ТОЛЬКО отрисовке.
     *
     * <p>Симуляция шагает раз в Config.TICK (пять с половиной раз в секунду), а кадров
     * рисуется шестьдесят. Без этого числа предмет на ленте дёргался бы скачками; с ним
     * рендер показывает его между прошлой и текущей позицией — и движение становится
     * плавным, хотя мир по-прежнему думает целыми тиками.
     */
    public float tickAlpha() {
        return Math.min(accumulator / Config.TICK, 1f);
    }
```

### `Renderer`

В `switch` по зданиям ветка ленты становится пустой:

```java
                    // Предметы на лентах рисуются НЕ здесь: они больше не принадлежат
                    // клетке, а едут внутри линии — см. drawBeltItems().
                    case Belt _ -> { }
```

И появляется отдельный проход:

```java
    /**
     * Предметы, едущие по транспортным линиям.
     *
     * <p><b>Здесь и появляется плавность.</b> Модель считает целыми слотами и шагает пять
     * раз в секунду; alpha — доля прожитого тика — говорит, насколько предмет уже уехал от
     * прошлой позиции к текущей. Дробные координаты существуют только тут, в отрисовке:
     * симуляция остаётся целочисленной и воспроизводимой.
     */
    private void drawBeltItems(World world, float alpha) {
        float size = TILE * 0.42f;
        for (BeltSegment segment : world.belts().segments()) {
            for (BeltItemPos pos : segment.itemPositions(alpha)) {
                // Целая координата — это ЦЕНТР клетки, поэтому +0.5 клетки.
                float cx = Config.OFFSET_X + (pos.x() + 0.5f) * TILE;
                float cy = worldHeight - Config.OFFSET_Y - (pos.y() + 0.5f) * TILE;
                batch.draw(textures.itemTexture(pos.item()),
                        cx - size / 2f, cy - size / 2f, size, size);
            }
        }
    }
```

Вызывается из `drawItemsAndText`, внутри уже открытого `batch.begin()`:

```java
        drawBeltItems(world, game.tickAlpha());
        drawHud(game);
        batch.end();
```

# L6 — Решения

> ⚠️ Ответы к [L06-workbook.md](L06-workbook.md). Заходите по ссылке из своего шага:
> сначала 💡 подсказка (П), для шага 1 — ещё и 🧩 Parsons, потом 📄 решение (Р).
> Javadoc и комментарии — часть ответа.
>
> Код совпадает с эталоном (git-коммит `f9e8aa4` для `BeltNetwork`, `a30272c` для
> `BeltSegment`/`BeltItem`/`BeltItemPos`/`BeltView`), с двумя явными упрощениями:
> `BeltNetwork.onBeltPlaced` сегодня без склейки (случай 4 эталона) и
> `onBeltRemoved` — без честного разреза. Оба — работа L7. Метод `itemSlots()`
> (сохранения, L15–L16) сегодня не нужен и опущен.

---

<a id="разогрев"></a>
## Р+ — Ответы разминки (одной строкой, не подглядывая до попытки)

1. В L5 предмет двигался МЕЖДУ зданиями — тем же протоколом, что бур→ящик,
   `Simulation` не знала слова «лента». Сегодня появляется движение ВНУТРИ одной
   ленты (несколько предметов на линии сразу) — это новый вид движения, для него
   нужна новая система `moveBelts`, а не расширение старой.
2. Фаза 1 только ЧИТАЕТ поле и собирает список запланированных передач (штампуя
   клетки-приёмники через `claim`, чтобы двое не пропихнули предмет в одну);
   фаза 2 ПРИМЕНЯЕТ собранное. Раздельно — чтобы результат не зависел от порядка
   обхода зданий: изменения не видны, пока не собран весь список.
3. Update Method (Nystrom) — каждый объект мира на каждом тике получает свой шанс
   «пожить» через единообразный вызов `update(ctx)`; в `Simulation.step` это
   `runMachines`, вызывающий `building.update(ctx)` для каждого здания по очереди.
4. Инвариант за фасадом — правило, которое ВСЕГДА истинно для объекта, и держит
   его именно объект (а не тот, кто им пользуется); пример из уже написанного:
   `Building.create` гарантирует, что фабрика — единственный, кто создаёт здания
   консистентно (сегодня похожий пример — `BeltSegment.assertInvariants`).

---

<a id="п1"></a>
## П1 — Что сломается при обходе с хвоста

Подсказка: у `step` в голове контур — «граница для следующего предмета берётся из
УЖЕ ПОСЧИТАННОЙ новой позиции предыдущего». Если предыдущий ещё не посчитан (мы
идём с хвоста), эта граница ещё старая.

Ответ на «Предскажи-1»: возьмём линию с предметом A на слоте 0 и предметом B на
слоте 1 (B — ближе к голове). При обходе С ГОЛОВЫ: сначала двигается B (нет
препятствий впереди), потом A — его граница уже учитывает НОВУЮ позицию B. При
обходе С ХВОСТА: сначала двигается A на `slot + slotsPerTick`, ничего не зная о
том, где на самом деле окажется B (используется его СТАРАЯ позиция как граница,
которую ещё никто не проверил) — A может продвинуться ДО или ЗА старую позицию B,
и как только оба окажутся в одном слоте, `assertInvariants` поймает нарушение
(«порядок нарушен или два предмета в одном слоте»). Само по себе прежде-обгона не
случится физически иначе, кроме как через эту ошибку порядка.

<a id="parsons-1"></a>
## 🧩 Parsons-1 — соберите `step()` из настоящих строк Р1

Вот шесть строк метода `step(int slotsPerTick)` из `BeltSegment`, перемешанные и
помеченные буквами. Выпишите порядок (например, `A, B, C, D, E, F`), который
воспроизводит настоящий метод:

```
A. limit = it.slot - 1;
B. for (BeltItem it : items) {
C. it.slot = Math.min(it.slot + slotsPerTick, limit);
D. }
E. int limit = lengthSlots() - 1;
F. it.prevSlot = it.slot;
```

Внимание: строки `F`/`C`/`A` внутри тела цикла можно скомпилировать в ЛЮБОМ
порядке — Java не проверяет за вас порядок присваиваний. Но только ОДИН порядок
даёт правильное поведение: сохранить старую позицию НУЖНО раньше, чем она будет
перезаписана (иначе `prevSlot` станет равен новому `slot`, и рендер перестанет
интерполировать — предмет будет прыгать, а не скользить), а новую границу можно
посчитать только ПОСЛЕ того, как новая позиция уже известна. Соберите и проверьте
себя, прежде чем смотреть [Р1](L06-solutions.md#р1): `E, B, F, C, A, D`.

⬇ ниже — готовое решение

<a id="р1"></a>
## Р1 — `BeltItem`, `BeltItemPos`, `BeltView`, `BeltSegment` целиком

```java
// model/BeltItem.java
package com.rustorio.model;

import com.rustorio.core.Item;

/**
 * Предмет, едущий по транспортной линии: что едет и на каком слоте стоит.
 *
 * <p>Позиция — ЦЕЛОЕ число слотов от хвоста сегмента, а не дробь. Дробные числа
 * копят ошибку (прибавьте 0.1 сто раз — получите 9.99999998), и через час игры
 * предметы начали бы то слипаться, то расходиться; кроме того, дроби ломают
 * воспроизводимость симуляции, без которой не будет ни сохранений, ни надёжных
 * тестов. Дробь появляется ТОЛЬКО в отрисовке — см. {@link BeltSegment#itemPositions}.
 *
 * <p>{@code prevSlot} — позиция на прошлом тике. Она нужна исключительно рендеру:
 * между тиками он показывает предмет ГДЕ-ТО МЕЖДУ prevSlot и slot, и получается
 * плавное движение. Симуляция им не пользуется.
 */
final class BeltItem {
    final Item item;
    int slot;
    int prevSlot;

    BeltItem(Item item, int slot) {
        this.item = item;
        this.slot = slot;
        this.prevSlot = slot;
    }
}
```

```java
// model/BeltItemPos.java
package com.rustorio.model;

import com.rustorio.core.Item;

/** Где нарисовать предмет, едущий по ленте: непрерывные координаты поля. */
public record BeltItemPos(Item item, float x, float y) {
}
```

```java
// model/BeltView.java
package com.rustorio.model;

import java.util.List;

/** Что отрисовке нужно знать о предметах на ленте — и ничего больше. */
public interface BeltView {
    List<BeltItemPos> itemPositions(float alpha);
}
```

```java
// model/BeltSegment.java
package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class BeltSegment implements BeltView {

    private static final int SLOTS = Config.SLOTS_PER_TILE;

    private final Direction dir;
    private final List<Cell> tiles;
    private final List<BeltItem> items;

    BeltSegment(Direction dir, List<Cell> tiles, List<BeltItem> items) {
        this.dir = dir;
        this.tiles = List.copyOf(tiles);
        this.items = new ArrayList<>(items);
        this.items.sort(Collections.reverseOrder(java.util.Comparator.comparingInt(i -> i.slot)));
    }

    public Direction dir() { return dir; }
    public List<Cell> tiles() { return tiles; }
    public int tileCount() { return tiles.size(); }
    public int lengthSlots() { return tiles.size() * SLOTS; }
    public int itemCount() { return items.size(); }

    public int countItemsOnTile(int tileIndex) {
        int count = 0;
        for (BeltItem it : items) {
            if (it.slot / SLOTS == tileIndex) count++;
        }
        return count;
    }

    Optional<Item> itemOnTile(int tileIndex) {
        for (BeltItem it : items) {
            if (it.slot / SLOTS == tileIndex) return Optional.of(it.item);
        }
        return Optional.empty();
    }

    void step(int slotsPerTick) {
        int limit = lengthSlots() - 1;
        for (BeltItem it : items) {
            it.prevSlot = it.slot;
            it.slot = Math.min(it.slot + slotsPerTick, limit);
            limit = it.slot - 1;
        }
    }

    boolean canInsertAt(int slot) {
        if (slot < 0 || slot >= lengthSlots()) return false;
        for (BeltItem it : items) {
            if (it.slot == slot) return false;
        }
        return true;
    }

    void insert(Item item, int slot) {
        if (!canInsertAt(slot)) {
            throw new IllegalStateException("слот " + slot + " занят или вне линии");
        }
        BeltItem fresh = new BeltItem(item, slot);
        int pos = 0;
        while (pos < items.size() && items.get(pos).slot > slot) pos++;
        items.add(pos, fresh);
    }

    Optional<Item> headItem() {
        if (items.isEmpty()) return Optional.empty();
        BeltItem front = items.getFirst();
        return front.slot == lengthSlots() - 1 ? Optional.of(front.item) : Optional.empty();
    }

    void removeHeadItem() {
        if (headItem().isPresent()) items.removeFirst();
    }

    @Override
    public List<BeltItemPos> itemPositions(float alpha) {
        List<BeltItemPos> out = new ArrayList<>(items.size());
        Cell tail = tiles.getFirst();
        for (BeltItem it : items) {
            float slot = it.prevSlot + (it.slot - it.prevSlot) * alpha;
            float dist = (slot + 0.5f) / SLOTS;
            float x = tail.x() + dir.dx() * (dist - 0.5f);
            float y = tail.y() + dir.dy() * (dist - 0.5f);
            out.add(new BeltItemPos(it.item, x, y));
        }
        return out;
    }

    void assertInvariants() {
        if (tiles.isEmpty()) throw new IllegalStateException("сегмент без клеток");
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

    @Override
    public String toString() {
        char[] slots = new char[lengthSlots()];
        java.util.Arrays.fill(slots, '.');
        for (BeltItem it : items) {
            slots[it.slot] = it.slot == lengthSlots() - 1 ? 'O' : 'o';
        }
        return "[" + new String(slots) + "] " + dir.shortName() + " " + tiles.getFirst();
    }

    List<BeltItem> rawItems() { return items; }
}
```

### ❌ Частые неправильные варианты (шаг 1)

```java
void step(int slotsPerTick) {
    for (BeltItem it : items) {
        it.slot = Math.min(it.slot + slotsPerTick, lengthSlots() - 1); // ← ВСЕГДА один и тот же предел
    }
}
// Предел не сужается от предмета к предмету — задний может доехать ДО или ЗА
// переднего: два предмета в одном слоте, assertInvariants поймает при первой
// же проверке.
```

```java
// Забыли сохранить prevSlot ДО обновления slot:
it.slot = Math.min(it.slot + slotsPerTick, limit);
it.prevSlot = it.slot; // ← уже равен НОВОМУ slot, интерполяции не будет
// itemPositions(alpha) для любого alpha < 1 даст ту же точку, что для alpha = 1:
// предмет прыгает, а не скользит. Тесты BeltSegmentTest это поймают
// (renderInterpolatesBetweenTicks), но JUnit не подскажет причину так же
// наглядно, как чтение своего же кода.
```

---

<a id="п2"></a>
## П2 — Какая клетка имеет право отдать

Подсказка: предмет «вылезает» из линии только с ОДНОГО конца — с головы. Если бы
любая клетка линии могла отдать наружу, груз мог бы телепортироваться из середины.

Ответ на «Предскажи-2»: право `output()` — только у клетки с
`indexInSegment == segment.tileCount() - 1` (последняя, головная клетка линии), и
только когда `segment.headItem()` не пусто (предмет реально доехал до последнего
слота). Любая другая клетка линии на `output()` всегда отвечает `Optional.empty()`
— даже если формально хранит какой-то груз внутри сегмента: наружу отдаёт только
голова.

<a id="р2"></a>
## Р2 — `Belt` целиком

```java
// model/Belt.java
package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Клетка ленты. Сама почти ничего не хранит: предметы живут в
 * {@link BeltSegment} — транспортной линии, куда эта клетка входит.
 *
 * <p><b>Почему привязка к линии происходит не в конструкторе.</b>
 * {@link Building#create} делает {@code new Belt(dir)}, ещё НЕ ЗНАЯ, куда ленту
 * поставят: координаты появляются только внутри {@link World#place}. Поэтому мир,
 * поставив ленту, сообщает об этом {@link BeltNetwork}, а та решает — начать новую
 * линию или удлинить соседнюю — и вызывает {@link #attach}.
 *
 * <p>Наружу клетка ленты остаётся обычным зданием: договор
 * {@code output/canAccept/accept} не изменился, поэтому бур, печь и сборщик
 * ничего не знают о том, что ленты переписаны.
 */
public final class Belt implements Building {

    private final Direction dir;
    private @Nullable BeltSegment segment;
    private int indexInSegment = -1;

    public Belt(Direction dir) {
        this.dir = dir;
    }

    void attach(BeltSegment segment, int indexInSegment) {
        this.segment = segment;
        this.indexInSegment = indexInSegment;
    }

    void detach() {
        this.segment = null;
        this.indexInSegment = -1;
    }

    public @Nullable BeltSegment segment() { return segment; }
    public int indexInSegment() { return indexInSegment; }

    @Override
    public void update(TickContext ctx) {
        // Лента сама не работает: предметы двигает система moveBelts в Simulation.
    }

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
        if (segment != null) segment.removeHeadItem();
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    private int entrySlot() {
        return indexInSegment * Config.SLOTS_PER_TILE;
    }

    public Direction dir() { return dir; }

    public Optional<Item> item() {
        return segment == null ? Optional.empty() : segment.itemOnTile(indexInSegment);
    }
}
```

### ❌ Частые неправильные варианты (шаг 2)

```java
@Override
public Optional<Handoff> output() {
    return segment == null ? Optional.empty() : segment.headItem().map(item -> new Handoff(item, dir));
    // ← забыли проверить indexInSegment == tileCount()-1: КАЖДАЯ клетка линии
    // теперь пытается отдать головной предмет наружу своему соседу — предмет
    // продублируется на нескольких стыках одновременно.
}
```

---

<a id="п3"></a>
## П3 — Сколько сегментов получится

Подсказка: `onBeltPlaced` каждый раз ищет соседа СЗАДИ (по направлению) и, если
находит, присоединяется к НЕМУ, пересобирая линию целиком.

Ответ на «Предскажи-3»: пять лент, поставленных подряд одна за другой (в
направлении их движения), — это ОДИН `BeltSegment` из пяти клеток: каждая
следующая лента находит предыдущую сзади и удлиняет её линию. Снос ленты из
середины сегодня — это НЕ разрез на два сегмента (это работа L7), а полное
разрушение всей линии: `onBeltRemoved` в этой лекции убирает сегмент целиком и
отвязывает (`detach`) все его клетки, включая оставшиеся половины. Проверено
эмпирически: 4 подряд построенные ленты → 1 сегмент; снос одной ИЗ СЕРЕДИНЫ → 0
сегментов, обе оставшиеся половины без линии.

<a id="р3"></a>
## Р3 — `BeltNetwork` целиком + правки `World`/`Simulation`

```java
// model/BeltNetwork.java
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

    public int itemCount() {
        int total = 0;
        for (BeltSegment segment : segments) total += segment.itemCount();
        return total;
    }

    public void step(int slotsPerTick) {
        for (BeltSegment segment : segments) segment.step(slotsPerTick);
    }

    void onBeltPlaced(Cell cell, Belt belt) {
        Direction dir = belt.dir();
        BeltSegment back = segmentOfBeltAt(cell.x() - dir.dx(), cell.y() - dir.dy(), dir);
        BeltSegment front = back == null
                ? segmentOfBeltAt(cell.x() + dir.dx(), cell.y() + dir.dy(), dir)
                : null; // и сзади, и спереди сразу — сегодня не склеиваем (L7)

        List<Cell> tiles = new ArrayList<>();
        List<BeltItem> items = new ArrayList<>();

        if (back != null) {
            tiles.addAll(back.tiles());
            items.addAll(back.rawItems());
            segments.remove(back);
        }

        int newTileIndex = tiles.size();
        tiles.add(cell);

        if (front != null) {
            int shift = (newTileIndex + 1) * SLOTS;
            for (BeltItem it : front.rawItems()) {
                it.slot += shift;
                it.prevSlot += shift;
                items.add(it);
            }
            tiles.addAll(front.tiles());
            segments.remove(front);
        }

        createSegment(dir, tiles, items);
    }

    void onBeltRemoved(Cell cell, Belt belt) {
        BeltSegment segment = belt.segment();
        if (segment == null) return;
        segments.remove(segment);
        for (Cell tile : segment.tiles()) {
            beltAt(tile).detach();
        }
    }

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

    private @Nullable BeltSegment segmentOfBeltAt(int x, int y, Direction dir) {
        if (!world.inBounds(x, y)) return null;
        return world.tile(x, y).building() instanceof Belt belt && belt.dir() == dir
                ? belt.segment()
                : null;
    }
}
```

`World` — три точки правки:

```java
    private final BeltNetwork belts = new BeltNetwork(this);
    // ...
    public BeltNetwork belts() {
        return belts;
    }
```

```java
    // в place(), внутри if (existing != null) { ... }, после sameKind-проверки:
    if (existing instanceof Belt oldBelt) {
        belts.onBeltRemoved(new Cell(x, y), oldBelt);
    }
    // ...после tile.setBuilding(building) и проверки Miner:
    if (building instanceof Belt newBelt) {
        belts.onBeltPlaced(new Cell(x, y), newBelt);
    }
```

```java
    // remove() целиком:
    public void remove(int x, int y) {
        if (!inBounds(x, y)) return;
        Tile tile = tile(x, y);
        if (tile.building() instanceof Belt belt) {
            belts.onBeltRemoved(new Cell(x, y), belt);
        }
        tile.setBuilding(null);
    }
```

`Simulation` — новая система между `runMachines` и `moveItems`:

```java
    public void step(TickContext ctx) {
        world.beginTick();
        runMachines(ctx);
        moveBelts();
        moveItems();
    }

    private void moveBelts() {
        world.belts().step(Config.BELT_SLOTS_PER_TICK);
    }
```

### ❌ Частые неправильные варианты (шаг 3)

```java
// «Разрежем список тайлов, но забудем позвать attach на новых кусках»:
segments.remove(oldSegment);
segments.add(new BeltSegment(dir, leftTiles, leftItems));
segments.add(new BeltSegment(dir, rightTiles, rightItems));
// Клетки левой и правой половины по-прежнему держат ссылку на СТАРЫЙ,
// уже удалённый из segments сегмент — предмет продолжит «существовать»,
// но `moveBelts` его больше не увидит и не сдвинет. Именно поэтому вся
// сборка идёт через ЕДИНЫЙ createSegment, который сам вызывает attach.
```

---

<a id="п4"></a>
## П4 — Откуда берётся плавность на экране

Подсказка: линия хранит позиции ЦЕЛЫМИ слотами (симуляция воспроизводима), а
дробная точка появляется РОВНО в одном месте — внутри `itemPositions(alpha)`,
между `prevSlot` и `slot`.

Ответ на «Предскажи-4» (в шаге не было явного «Предскажи», но вопрос напрашивается):
рендер вызывает `itemPositions(tickAlpha)` КАЖДЫЙ кадр (60 раз в секунду), а не
только когда тикнула симуляция (5,5 раза в секунду) — поэтому даже без нового тика
`tickAlpha` успевает подрасти от 0 к 1, и предмет на экране едет плавно между двумя
целочисленными позициями, которые сама симуляция считает лишь раз в TICK секунд.

<a id="р4"></a>
## Р4 — `ItemRenderer` и `Renderer`

```java
    void render(World world, TileRange range, float tickAlpha) {
        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) continue;
                float px = grid.x(x);
                float py = grid.yBottom(y);
                switch (b) {
                    case Miner m -> m.outputItem().ifPresent(it -> drawItemIcon(px, py, it));
                    // Предметы на лентах рисуются НЕ здесь — см. drawBeltItems().
                    case Belt _ -> { }
                    case Chest c -> drawCounter(px, py, c.items());
                }
            }
        }
        drawBeltItems(world, tickAlpha);
        batch.end();
    }

    /**
     * Предметы, едущие по транспортным линиям.
     *
     * <p><b>Здесь и появляется плавность.</b> Модель считает целыми слотами и
     * шагает пять раз в секунду; {@code alpha} — доля прожитого тика — говорит,
     * насколько предмет уже уехал от прошлой позиции к текущей.
     */
    private void drawBeltItems(World world, float alpha) {
        for (BeltSegment segment : world.belts().segments()) {
            for (BeltItemPos pos : segment.itemPositions(alpha)) {
                float cx = grid.centerX(pos.x());
                float cy = grid.centerY(pos.y());
                drawItemCentered(cx - TILE / 2f, cy - TILE / 2f, pos.item());
            }
        }
    }
```

`Renderer` — один изменённый вызов:

```java
        itemRenderer.render(world, visible, game.tickAlpha());           // 5. предметы/счётчики
```

### ❌ Частые неправильные варианты (шаг 4)

```java
buildingRenderer.renderSprites(world, visible, elapsed);   // без изменений — это ОК,
itemRenderer.render(world, visible, elapsed);              // ← а вот тут не ОК: подставили
// НАСТЕННОЕ время вместо tickAlpha. elapsed ничем не ограничен (растёт всегда),
// а itemPositions ждёт РОВНО [0,1] — предмет либо «застынет» не в фазе с миром,
// либо будет улетать за пределы отрезка prevSlot..slot. Разные часы — для разных
// анимаций: elapsed для декоративной дорожки ленты, tickAlpha — для позиции груза.
```

---

<a id="билет"></a>
## Ответы на выходной билет

1. Потому что вместимость линии считается в СЛОТАХ (`SLOTS_PER_TILE` на клетку), а
   не в независимых зданиях: клетка линии — не отдельный «карман на один предмет»,
   а точка внутри общей очереди.
2. Потому что граница для каждого следующего предмета берётся из УЖЕ посчитанной
   новой позиции переднего; с хвоста эта граница ещё старая, и задний может
   обогнать или слиться с передним.
3. `elapsed` — настенное время, растёт всегда, для декоративной анимации (бегущая
   дорожка); `tickAlpha` — доля ТЕКУЩЕГО тика (0..1), синхронна с моделью, для
   позиции реального груза между двумя целочисленными состояниями симуляции.

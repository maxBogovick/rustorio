# A8 — Решения

> **Это ответы.** Заходите сюда **по ссылке из шага** рабочей тетради
> [A8-workbook.md](A8-workbook.md), а не читайте подряд. Подсказка (`Пn`) даёт направление;
> решение (`Рn`) — код целиком. Лучше всего сравнить с ним **после** того, как написали своё.

---

## П1 — каркас Appearance

Начните со списка полей из тетради и сделайте их компонентами `record`. Все «пустые»
состояния — это sentinel: стрелки нет → `null`, полоски нет → `Float.NaN`, счётчика нет →
`-1`. Фабрики — это методы, возвращающие **новую** запись с одним изменённым полем:
`of(label)` задаёт основу с пустыми sentinel-значениями, а `arrow/progress/…` копируют
запись, меняя своё поле. Проверьте импорты: только `Direction` и `Item`.

---

## Р1 — Appearance целиком

`core/Appearance.java`:

```java
package com.rustorio.core;

import org.jspecify.annotations.Nullable;

/**
 * Как показать здание — в игровых терминах, без единого пикселя.
 *
 * <p>Здание описывает себя этим record, а слой render его исполняет. Незнакомое зданию
 * рисуется подписанной плашкой — поэтому новое здание не требует ни строчки в графике.
 */
public record Appearance(
        String label,
        @Nullable Direction arrow,
        boolean working,
        float progress,
        boolean alert,
        @Nullable Item icon,
        int counter) {

    /** Основа: одна подпись, без стрелок, полосок, иконок и счётчиков. */
    public static Appearance of(String label) {
        return new Appearance(label, null, false, Float.NaN, false, null, -1);
    }

    public Appearance arrow(Direction direction, boolean working) {
        return new Appearance(label, direction, working, progress, alert, icon, counter);
    }

    public Appearance progress(float fraction) {
        return new Appearance(label, arrow, working, fraction, alert, icon, counter);
    }

    public Appearance alert(boolean on) {
        return new Appearance(label, arrow, working, progress, on, icon, counter);
    }

    public Appearance icon(@Nullable Item item) {
        return new Appearance(label, arrow, working, progress, alert, item, counter);
    }

    public Appearance counter(int value) {
        return new Appearance(label, arrow, working, progress, alert, icon, value);
    }

    public boolean hasProgress() {
        return !Float.isNaN(progress);
    }

    public boolean hasCounter() {
        return counter >= 0;
    }
}
```

---

## П2 — что говорит о себе здание

Метод в `Building` — абстрактный, одна строка. Дальше идите по таблице из шага 2 и
переводите её строки в цепочки фабрик. «Стрелка зелёная, пока копает» — это
`.arrow(dir, <условие работы>)`. «Иконка — руда на выходе» — `.icon(<поле выхода>)` (годится
`null`, если пусто). Здание без стрелки/полоски — просто `Appearance.of("...")` без
хвоста. Не выдумывайте новых состояний: показывайте ровно то, что графика рисовала раньше.

---

## Р2 — appearance во всех зданиях

Метод в интерфейсе `model/Building.java`:

```java
    /** Как меня показать — в игровых терминах. Единственная дверь здания в графику. */
    Appearance appearance();
```

Реализации (по одной на здание):

```java
// Miner — output это @Nullable Item (готовая руда), onOre — стоит ли бур на руде
@Override public Appearance appearance() {
    return Appearance.of("Miner")
            .arrow(dir, output == null && onOre)
            .alert(!onOre)
            .icon(output);
}

// Belt — никаких накладок; груз рисуется отдельно, вдоль линии
@Override public Appearance appearance() {
    return Appearance.of("Belt");
}

// Furnace
@Override public Appearance appearance() {
    return Appearance.of("Furnace")
            .arrow(dir, isWorking())
            .progress(progressFraction())
            .icon(displayItem().orElse(null));
}

// Assembler — как печь
@Override public Appearance appearance() {
    return Appearance.of("Assembler")
            .arrow(dir, isWorking())
            .progress(progressFraction())
            .icon(displayItem().orElse(null));
}

// Chest
@Override public Appearance appearance() {
    return Appearance.of("Chest").counter(items);
}

// Lab — направления нет, значит и стрелки нет
@Override public Appearance appearance() {
    return Appearance.of("Lab")
            .progress(progressFraction())
            .counter(points());
}

// Splitter — направление читается по повороту спрайта, стрелки нет
@Override public Appearance appearance() {
    return Appearance.of("Splitter");
}

// UndergroundBelt — то же
@Override public Appearance appearance() {
    return Appearance.of("Underground");
}
```

Не забудьте `import com.rustorio.core.Appearance;` в каждом здании.

---

## П3 — как render читает описание

Три прохода перестают смотреть на тип и начинают смотреть на `b.appearance()`. Схема
одинаковая: взять описание, спросить у него `arrow`/`hasProgress`/`alert`/`icon`/
`hasCounter` и нарисовать. В `renderSprites` `switch` по спрайтам оставьте как есть, но
допишите `default -> drawPlate(...)`. Для плашки заведите белый регион 1×1 в `Textures` и
протащите `BitmapFont` в конструктор `BuildingRenderer` из `Renderer`. Красите квадрат —
верните цвет в белый следующей строкой.

---

## Р3 — generic render

**`render/BuildingRenderer.java`** — конструктор получает шрифт, проходы читают описание:

```java
    private final BitmapFont font;

    BuildingRenderer(SpriteBatch batch, ShapeRenderer shapes, Textures textures,
            BitmapFont font, Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.textures = textures;
        this.font = font;
        this.grid = grid;
    }
```

Проход спрайтов — прежний `switch` плюс запасной выход:

```java
                switch (b) {
                    case Miner m -> { /* ... как было ... */ }
                    case Belt belt -> { /* ... */ }
                    case Furnace f -> { /* ... */ }
                    case Assembler _ -> batch.draw(textures.assembler, px, py, TILE, TILE);
                    case Chest _ -> batch.draw(textures.chest, px, py, TILE, TILE);
                    case Splitter s -> { /* ... */ }
                    case UndergroundBelt u -> { /* ... */ }
                    case Lab _ -> batch.draw(textures.lab, px, py, TILE, TILE);
                    default -> drawPlate(b.appearance().label(), px, py);
                }
```

Накладки — без `switch`:

```java
    void renderOverlays(World world, GameState game, TileRange range) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                Appearance look = b.appearance();
                if (look.arrow() != null) {
                    drawArrow(px, py, look.arrow(), look.working());
                }
                if (look.hasProgress()) {
                    drawProgressBar(px, py, look.progress());
                }
            }
        }
        game.hover().ifPresent(cell -> drawArrow(
                grid.x(cell.x()), grid.yBottom(cell.y()), game.direction(), Palette.GHOST));
        shapes.end();
    }
```

Рамки — по флагу `alert`:

```java
                Building b = world.tile(x, y).building();
                if (b != null && b.appearance().alert()) {
                    shapes.setColor(Palette.IDLE);
                    shapes.rect(grid.x(x) + 2, grid.yBottom(y) + 2, TILE - 4, TILE - 4);
                }
```

Плашка:

```java
    private void drawPlate(String label, float px, float py) {
        int h = label.hashCode();
        float r = 0.30f + 0.55f * ((h & 0xFF) / 255f);
        float g = 0.30f + 0.55f * (((h >> 8) & 0xFF) / 255f);
        float b = 0.30f + 0.55f * (((h >> 16) & 0xFF) / 255f);
        batch.setColor(r, g, b, 1f);
        batch.draw(textures.white, px + 2, py + 2, TILE - 4, TILE - 4);
        batch.setColor(Color.WHITE);          // вернуть цвет — иначе покрасится следующий спрайт
        font.getData().setScale(0.7f);
        font.draw(batch, label, px + 4, py + TILE - 5);
        font.getData().setScale(1f);
    }
```

**`render/Textures.java`** — белый регион 1×1:

```java
    final TextureRegion white;
    // ... в конструкторе, рядом с остальной упаковкой:
    packWhite(packer);
    // ... после generateTextureAtlas:
    white = region("white");

    private static void packWhite(PixmapPacker packer) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        packer.pack("white", pixmap);
        pixmap.dispose();
    }
```

**`render/Renderer.java`** — передать шрифт:

```java
    this.buildingRenderer = new BuildingRenderer(batch, shapes, textures, font, grid);
```

**`render/ItemRenderer.java`** — иконка и счётчик из описания:

```java
                Appearance look = b.appearance();
                if (look.icon() != null) {
                    drawItemIcon(px, py, look.icon());
                }
                if (look.hasCounter()) {
                    drawCounter(px, py, look.counter());
                }
```

Груз лент (`drawBeltItems`) остаётся как был: он принадлежит линии, а не клетке.

---

## П4 — как провести Buffer по цепочке

Класс пишется по договору `Building`: внутри — `Deque<Item>`, `accept` кладёт, `output`
называет голову с направлением, `removeOutput` снимает голову, `canAccept` пускает, пока
есть место. `appearance()` — стрелка зелёная, пока не пусто; иконка — голова очереди;
счётчик — размер. Дальше не пишите обвязку наугад — **добавьте `Tool.BUFFER` и идите за
ошибками компилятора**: он сам приведёт вас в `create`, `permits`, `SaveService`,
`LoadService`. В `render` он вас не пошлёт — в этом и смысл.

---

## Р4 — Buffer и сохранение

**`model/Buffer.java`:**

```java
package com.rustorio.model;

import com.rustorio.core.Appearance;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/** Буфер: копит до CAPACITY предметов и отдаёт по одному соседу в сторону dir (FIFO). */
public final class Buffer implements Building {

    private static final int CAPACITY = 8;

    private final Direction dir;
    private final Deque<Item> items = new ArrayDeque<>();

    public Buffer(Direction dir) {
        this.dir = dir;
    }

    public Buffer(Direction dir, List<Item> contents) {
        this.dir = dir;
        this.items.addAll(contents);
    }

    @Override public void update(TickContext ctx) {
        // пассивен: копит на приёме, отдаёт через output()
    }

    @Override public Optional<Handoff> output() {
        Item head = items.peek();
        return head == null ? Optional.empty() : Optional.of(new Handoff(head, dir));
    }

    @Override public boolean canAccept(Item item) {
        return items.size() < CAPACITY;
    }

    @Override public void accept(Item item) {
        items.add(item);
    }

    @Override public void removeOutput() {
        items.poll();
    }

    @Override public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    @Override public Appearance appearance() {
        return Appearance.of("Buffer")
                .arrow(dir, !items.isEmpty())
                .icon(items.peek())
                .counter(items.size());
    }

    public Direction dir() {
        return dir;
    }

    public List<Item> contents() {
        return new ArrayList<>(items);
    }
}
```

**`core/Tool.java`** — новый слот:

```java
    LAB("Lab", 8),
    BUFFER("Buffer", 9);
```

**`model/Building.java`** — список `permits` и фабрика:

```java
public sealed interface Building
        permits Miner, Belt, Furnace, Chest, Assembler, Splitter, UndergroundBelt, Lab, Buffer {
    // ...
    static Building create(Tool tool, Direction dir) {
        return switch (tool) {
            // ...
            case LAB -> new Lab();
            case BUFFER -> new Buffer(dir);
        };
    }
}
```

**`persist/BuildingDto.java`** — своё поле для содержимого (схема v3):

```java
public record BuildingDto(
        Tool type, int x, int y, @Nullable Direction dir, int amount,
        @Nullable MachineDto machine, @Nullable SplitterDto splitter,
        @Nullable List<Item> underground, @Nullable Item minerOutput,
        @Nullable List<Item> buffer) {   // <-- новое, v3
}
```

**`persist/SaveService.java`** — ветка + лишний `null` в остальных (аргумент `buffer`):

```java
            case Lab l -> new BuildingDto(Tool.LAB, x, y, null,
                    l.points(), new MachineDto(l.stockSnapshot(), l.readySnapshot()),
                    null, null, null, null);
            case Buffer b -> new BuildingDto(Tool.BUFFER, x, y, b.dir(),
                    0, null, null, null, null, b.contents());
```

**`persist/LoadService.java`** — сборка обратно:

```java
            case BUFFER -> new Buffer(dir,
                    dto.buffer() != null ? dto.buffer() : List.of());
```

**`persist/GameSnapshot.java`** — поднять версию:

```java
    public static final int SCHEMA_VERSION = 3;
```

Аддитивность: сейв v2 (без поля `buffer`) читается кодом v3 — поле становится `null`, то
есть «буфер пуст». Тот же приём совместимости, что и v1→v2.

**Проверка обещания урока.** После всего этого `git diff --stat` покажет изменения в
`core/`, `model/`, `persist/` — и **ни одного файла из `render/`**. Буфер видно на поле, он
работает и переживает сохранение, а графику для него никто не открывал.

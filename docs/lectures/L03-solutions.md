# L3 — Решения

> ⚠️ Ответы к [L03-workbook.md](L03-workbook.md). Заходите по ссылке из своего шага:
> сначала 💡 подсказка (П), потом 📄 решение (Р). Javadoc и комментарии в решениях —
> часть ответа, читайте их.
>
> Код совпадает с эталонным проектом 1:1 там, где срез это позволяет. Где сегодня
> берётся упрощённая версия — стоит пометка, какая лекция доведёт её до эталона.

---

<a id="п1"></a>
## П1 — `update` в контракте

Подсказка: вспомните L2. Как только вы добавляете в `sealed interface Building`
абстрактный метод, каждая реализация из `permits` обязана его определить — иначе
класс «не абстрактный, но не реализует абстрактный метод».

Ответ на «Предскажи-1»: компилятор скажет про `Chest` дословно
`Chest is not abstract and does not override abstract method update(TickContext)`
(та же ошибка, что в L2 была про `direction()`). Это не поломка — это список задач:
интерфейс перечисляет, что реализовать.

<a id="р1"></a>
## Р1 — `TickContext`, `Building.update`, `Chest.update`

```java
// core/TickContext.java
package com.rustorio.core;

/**
 * Всё, что зданию нужно знать про текущий шаг симуляции (тик).
 *
 * <p><b>Зачем «коробка» вместо простого {@code float dt}.</b> Сегодня буру и печи
 * нужна только длительность тика. Но стоит зданиям понадобиться что-то ещё общее —
 * и с «голым» параметром пришлось бы править сигнатуру {@code update} во ВСЕХ
 * зданиях сразу. С коробкой сигнатура не меняется никогда: нужно новое общее данное —
 * добавляется поле СЮДА. (В L13 так приедет баланс апгрейдов: одно поле здесь
 * проведёт компилятором по всем машинам — и ни одной правки в самих {@code update}.)
 *
 * <p><b>Один объект на тик, а не на здание.</b> Все поля одинаковы для всех зданий в
 * пределах шага, поэтому контекст создаётся один раз за тик, а не на каждое здание.
 */
public record TickContext(float dt) {
}
```

> Отличие от эталона (осознанное): в эталоне запись — `record TickContext(float dt,
> Balance balance)`. Поле `balance` появится в **L13** вместе с апгрейдами; до тех
> пор в игре нет изменяемого баланса, и класс `Balance` ещё не написан. Добавление
> поля в L13 — специально спроектированный урок: компилятор проведёт вас по всем
> `update`, где `ctx` используется.

```java
// model/Building.java — добавка к интерфейсу (полный файл — Р3, здесь только новое):
    /**
     * «Внутренняя» работа здания за один шаг симуляции (тик).
     *
     * <p>Единственный параметр — {@link TickContext}: коробка со всем, что нужно
     * зданиям на этом шаге. Так сигнатура не меняется, даже когда зданиям
     * понадобится новое общее данное (см. javadoc {@code TickContext}).
     */
    void update(TickContext ctx);
```

```java
// model/Chest.java — добавка:
    @Override
    public void update(TickContext ctx) {
        // Ящик пассивен: он ничего не делает сам, только хранит. Пустой update —
        // честная реализация «мне на тике делать нечего», а не забытый метод.
    }
```

### ❌ Частая ошибка (шаг 1)

```java
public record TickContext(float dt) {
    void update(...) { }   // ← НЕТ. update — метод ЗДАНИЯ, а не контекста.
}
// TickContext — только данные тика. Кто что делает на тике, решают здания.
```

---

<a id="п2"></a>
## П2 — Сколько тиков до руды

Подсказка: `cooldown` стартует с `MINER_TIME` и убывает на `dt` (= `TICK`) каждый
тик. Руда кладётся, когда `cooldown <= 0`.

Ответ на «Предскажи-2»: `MINER_TIME / TICK = 0.9 / 0.18 = 5` тиков — на пятом
`cooldown` дойдёт до нуля и `output` станет `IRON_ORE`. (Строго говоря, float-хвосты
могли бы сдвинуть это на тик — `0.18f` и `0.9f` не представимы в двоичной дроби
точно; здесь округления складываются удачно, но именно поэтому тест гоняет 10 тиков
«с запасом», а не ровно 5.) На **шестом** тике первая же
строка `if (output != null || !onOre) return;` сработает по `output != null` и метод
выйдет, ничего не делая: выход занят, копать больше некуда. Именно поэтому без
потребителя (L4) бур застывает после одной руды.

<a id="р2"></a>
## Р2 — `Miner` целиком

```java
// model/Miner.java
package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Бур: стоит на руде, раз в {@link Config#MINER_TIME} кладёт руду в выход.
 *
 * <p><b>Про {@code onOre}.</b> «Есть ли подо мной руда» — свойство КЛЕТКИ, а бур
 * своих координат не знает. Поэтому мир сообщает буру факт ОДИН раз, при постройке
 * ({@link World#place} знает клетку), а бур его кэширует. Это безопасно, пока руда
 * не иссякает. <b>Если появится истощение руды — мир ОБЯЗАН сообщить буру об
 * изменении</b> через {@code setOnOre}, иначе бур продолжит «копать» из пустоты.
 *
 * <p><b>Чего бур пока НЕ умеет.</b> Отдавать руду соседу. Протокол передачи
 * (output/accept) добавит L4 — тогда добытая руда поедет в ящик, и бур снова
 * закрутится. Сегодня он добывает одну руду и держит её на выходе.
 */
public final class Miner implements Building {

    private final Direction dir;
    private boolean onOre;
    private float cooldown;
    /** Готовая руда на выходе (или {@code null} — выход пуст). */
    private @Nullable Item output;

    public Miner(Direction dir) {
        this.dir = dir;
        this.cooldown = Config.MINER_TIME;
    }

    /** Мир сообщает буру, стоит ли он на руде (см. javadoc класса). */
    void setOnOre(boolean onOre) {
        this.onOre = onOre;
    }

    @Override
    public void update(TickContext ctx) {
        if (output != null || !onOre) {
            return; // выход занят или копать нечего
        }
        cooldown -= ctx.dt();
        if (cooldown <= 0f) {
            output = Item.IRON_ORE;
            cooldown = Config.MINER_TIME;
        }
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    // ── Чтение состояния для отрисовки ────────────────────────────────
    public Direction dir() {
        return dir;
    }

    public boolean onOre() {
        return onOre;
    }

    /** Доля выполненной добычи 0..1 — по ней рендер выбирает кадр анимации бура. */
    public float progressFraction() {
        return Math.clamp(1f - cooldown / Config.MINER_TIME, 0f, 1f);
    }

    /** Руда на выходе, если есть. */
    public Optional<Item> outputItem() {
        return Optional.ofNullable(output);
    }
}
```

Бур использует `Config.MINER_TIME` — этой константы в стартовом `Config` нет, её
добавляет шаг 2 (первый пункт алгоритма): `public static final float MINER_TIME = 0.9f;`.

> Отличия от эталона (вынужденные срезом, все сходятся обратно):
> - Нет методов `output()`, `canAccept()`, `accept()`, `removeOutput()` и типа
>   `Handoff` — это **протокол передачи предметов, приедет в L4**. Пока бур только
>   копает и держит.
> - В эталоне `cooldown = cycleTime`, где `cycleTime = Config.MINER_TIME /
>   ctx.balance().speed(Tool.MINER)` — множитель скорости от апгрейдов. Пока баланса
>   нет (**L13**), цикл всегда равен `Config.MINER_TIME`, поэтому отдельного поля
>   `cycleTime` мы не заводим и делим на константу.

### ❌ Частые неправильные варианты (шаг 2)

```java
@Override
public void update(TickContext ctx) {
    cooldown -= ctx.dt();
    if (cooldown <= 0f) { output = Item.IRON_ORE; cooldown = Config.MINER_TIME; }
}   // ← забыт guard `if (output != null || !onOre) return;`
// Без него бур «копает» даже не на руде (тест minerOffOreNeverDigs красный),
// а на занятом выходе перезапустит кулдаун поверх готовой руды.
```

```java
public float progressFraction() {
    return 1f - cooldown / Config.MINER_TIME;   // ← без Math.clamp
}
// На старте cooldown == MINER_TIME → вернёт ровно 0.0 (ок), но float-арифметика
// на границе даёт ±хвост: 1.0000001f. В рендере (int)(1.0000001f * 3) == 3 —
// выход за массив miner[3]. clamp(.., 0f, 1f) в модели + потолок 0.999f в рендере.
```

```java
public void setOnOre(boolean onOre) { ... }   // ← public
// Тогда кто угодно снаружи пакета «переубедит» бур про руду в обход мира.
// Пакетно-приватный (без модификатора): менять факт вправе только World, как
// Tile.setBuilding в L2.
```

---

<a id="п3"></a>
## П3 — `place` и `create`: направление

Подсказка: ветку `create` для `MINER` делаете как для `CHEST` в L2, только
`new Miner(dir)`. В `place` факт про руду сообщается ПОСЛЕ `setBuilding`, и только
если поставленное здание — бур: `instanceof Miner miner` (привязка из «Нового в
языке») даёт вам типизированную ссылку в одну строку. `tile` в методе уже есть.

<a id="р3"></a>
## Р3 — изменения в `Building` + добавка в `World.place`

`Building` из L2 меняется в трёх местах (метод `update` уже показан в Р1; `sameKind`
и его javadoc — без изменений с L2):

```java
// 1. permits: бур входит в замкнутый список.
public sealed interface Building permits Chest, Miner {

// 2. новый метод контракта (см. Р1) — уже реализован в Chest и Miner:
    void update(TickContext ctx);

// 3. фабрика: ветка MINER теперь возвращает бур.
    static Optional<Building> create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> Optional.of(new Miner(dir));
            case CHEST -> Optional.of(new Chest());
            // Эти ветки заполнят лекции про ленту (L5) и печь (L8):
            case BELT, FURNACE -> Optional.empty();
        };
    }
}
```

Не забудьте импорт `com.rustorio.core.TickContext`.

> Отличие от эталона: `permits Chest, Miner` — пока два здания вместо восьми; список
> растёт по одному зданию в лекции. И `create` всё ещё возвращает `Optional`
> (рефакторинг `Optional → Building` — L8, когда заполнится последняя базовая ветка).

Добавка в `World.place` — одна ветка **после** `tile.setBuilding(building)`:

```java
public void place(int x, int y, Building building) {
    if (!inBounds(x, y)) {
        return;
    }
    Tile tile = tile(x, y);
    Building existing = tile.building();
    if (existing != null) {
        if (existing.getClass() != building.getClass()) {
            return; // другой тип — не трогаем
        }
        if (existing.sameKind(building)) {
            return; // ровно такое же — не пересоздаём
        }
        // Тот же тип, другое направление: замена (для бура достижимо — у него есть dir).
    }
    tile.setBuilding(building);
    // Здание не знает своих координат, поэтому то, что зависит от МЕСТА, сообщает
    // ему мир — один раз, при постройке (см. javadoc Miner).
    if (building instanceof Miner miner) {
        miner.setOnOre(tile.hasOre());
    }
}
```

### ❌ Частая ошибка (шаг 3)

```java
if (building instanceof Miner miner) {
    miner.setOnOre(true);            // ← всегда true вместо tile.hasOre()
}
// Компилируется, minerLearnsAboutOreWhenPlaced красный: бур на (0,0) «думает»,
// что на руде. Факт берётся из КЛЕТКИ — tile.hasOre().
```

```java
// setOnOre ДО setBuilding, или в отдельном if-else, забывшем про guard'ы —
// порядок важен: сначала здание встаёт на клетку (с проверками), потом мир
// сообщает ему свойство места. Ветку кладём ПОСЛЕ tile.setBuilding.
```

---

<a id="п4"></a>
## П4 — `Simulation` и `forEachBuilding`: направление

Подсказка: `BuildingVisitor` — интерфейс с одним методом `visit(int, int, Building)`;
объявите его внутри `World`. `forEachBuilding` идёт двойным циклом по сетке кусков
(`chunksX × chunksY`), для существующих кусков — двойным циклом по клеткам внутри
(`Chunk.SIZE`), и для непустых зовёт `visitor.visit(worldX, worldY, building)`.
Мировые координаты собираются из координат куска и локальных: `(cx << Chunk.SHIFT) +
lx`. `Simulation.step` зовёт одну систему `runMachines`, а та — `forEachBuilding` с
лямбдой `b.update(ctx)`.

<a id="р4"></a>
## Р4 — `World.forEachBuilding` + `Simulation`

Добавка в `World` (рядом с `place`/`remove`):

```java
/**
 * Обойти все здания мира и дать каждому {@code visitor}. Порядок — по координатной
 * сетке кусков (не по {@code chunks.values()}: порядок карты произволен, а симуляция
 * обязана быть воспроизводимой).
 */
public void forEachBuilding(BuildingVisitor visitor) {
    int chunksX = (width + Chunk.SIZE - 1) >> Chunk.SHIFT;
    int chunksY = (height + Chunk.SIZE - 1) >> Chunk.SHIFT;
    for (int cy = 0; cy < chunksY; cy++) {
        for (int cx = 0; cx < chunksX; cx++) {
            Chunk chunk = chunks.get(key(cx, cy));
            if (chunk == null) {
                continue; // в этот кусок ещё никто не заглядывал — зданий там нет
            }
            for (int ly = 0; ly < Chunk.SIZE; ly++) {
                for (int lx = 0; lx < Chunk.SIZE; lx++) {
                    Building building = chunk.tile(lx, ly).building();
                    if (building != null) {
                        visitor.visit((cx << Chunk.SHIFT) + lx, (cy << Chunk.SHIFT) + ly,
                                building);
                    }
                }
            }
        }
    }
}

/**
 * «Что сделать с каждым зданием» — один метод, поэтому можно передать лямбдой.
 * {@code @FunctionalInterface} — обещание компилятору: здесь всегда РОВНО один
 * абстрактный метод; попытка добавить второй сломает сборку, а не лямбды по всему коду.
 */
@FunctionalInterface
public interface BuildingVisitor {
    void visit(int x, int y, Building building);
}
```

> `chunks`, `key`, `Chunk.SIZE`, `Chunk.SHIFT`, `chunk.tile(...)` — всё это уже есть
> в мире с L0; `forEachBuilding` только собирает их в детерминированный обход.
> Это 1:1 с эталоном.

```java
// sim/Simulation.java
package com.rustorio.sim;

import com.rustorio.core.TickContext;
import com.rustorio.model.World;

/**
 * Симуляция: как мир продвигается на один тик.
 *
 * <p>Один шаг мира — конвейер «систем», выполненных по порядку. «Система» — это
 * функция над миром: приватный метод, который читает и меняет его данные. Хочешь
 * новое поведение мира? Пишешь новую систему и добавляешь её вызов в {@link #step}.
 *
 * <p>Сейчас система одна — {@link #runMachines}: каждое здание делает свою
 * внутреннюю работу (Update Method). Передачу предметов между зданиями добавит
 * второй системой L4, движение по лентам — L5.
 *
 * <p><b>Почему это ОБЪЕКТ, а не набор {@code static}-методов.</b> Пока у симуляции
 * нет состояния, кроме {@code world}, — но в L4 у неё появятся переиспользуемые
 * между тиками буферы (список запланированных передач). Их место — поле объекта;
 * поэтому симуляция объект уже сейчас.
 */
public final class Simulation {

    private final World world;

    public Simulation(World world) {
        this.world = world;
    }

    /** ОДИН шаг симуляции = конвейер систем по порядку. */
    public void step(TickContext ctx) {
        runMachines(ctx);
    }

    /** Система: каждое здание делает свою внутреннюю работу (бур копает и т.д.). */
    private void runMachines(TickContext ctx) {
        world.forEachBuilding((x, y, building) -> building.update(ctx));
    }
}
```

### ❌ Частая ошибка (шаг 4)

```java
public void forEachBuilding(BuildingVisitor visitor) {
    for (Chunk chunk : chunks.values()) { ... }   // ← обход по значениям карты
}
// Компилируется и «работает» — но порядок HashMap произволен и меняется между
// запусками. Симуляции сегодня всё равно (здания независимы), но в L4, где важен
// порядок передач, это станет невоспроизводимым багом. Обходим по сетке кусков.
```

---

<a id="п5"></a>
## П5 — `GameState` зовёт симуляцию

Подсказка: поле `Simulation simulation`, создать в конструкторе. В `update`
контекст `new TickContext(Config.TICK)` — один раз до цикла; внутри `while` —
`simulation.step(ctx)` на месте старого комментария.

Ответ на «Предскажи-3»: `update(1.0f)` сделает **один** тик. Аккумулятор обрезается
`Math.min(accumulator + 1.0f, MAX_FRAME_TIME)` = `0.25`; цикл `while (0.25 >= 0.18)`
проходит один раз (списывает `0.18`, остаётся `0.07 < 0.18`). Один тик = `dt` 0.18 с,
а на полную добычу нужно пять тиков (`0.9 / 0.18`). Потолок `MAX_FRAME_TIME` —
защита из L0 от «спирали смерти»: после зависания мир не отрабатывает секунды разом.
Поэтому бур за один `update(1.0f)` лишь продвигается (`progressFraction() > 0`), но не
дозревает — что и проверяет тест.

<a id="р5"></a>
## Р5 — `GameState`: что добавить

```java
// поле:
private final Simulation simulation;

// в конструкторе:
public GameState(World world) {
    this.world = world;
    this.simulation = new Simulation(world);
}

// в update, тело цикла:
public void update(float deltaTime) {
    if (paused) {
        return;
    }
    accumulator = Math.min(accumulator + deltaTime, Config.MAX_FRAME_TIME);
    // Контекст создаётся ОДИН раз за вызов: его поля одинаковы для всех тиков кадра.
    TickContext ctx = new TickContext(Config.TICK);
    while (accumulator >= Config.TICK) {
        accumulator -= Config.TICK;
        simulation.step(ctx);
    }
}
```

Импорты: `com.rustorio.core.TickContext`, `com.rustorio.sim.Simulation`.

> Отличие от эталона: в эталоне `new TickContext(Config.TICK, balance)` и после
> `simulation.step(ctx)` идёт `research.collect(world)` — обе строки про механики
> L12–L13, которых пока нет.

### ❌ Частая ошибка (шаг 5)

```java
while (accumulator >= Config.TICK) {
    accumulator -= Config.TICK;
    TickContext ctx = new TickContext(Config.TICK);   // ← создание ВНУТРИ цикла
    simulation.step(ctx);
}
// Работает, но на каждый тик — новый объект-коробка. Поля одинаковы; создаём один
// раз до цикла. Мелочь сейчас, дисциплина на будущее (в L11 такие мелочи меряют).
```

---

<a id="п6"></a>
## П6 — Рендер бура: направление

Подсказка: ветка `case Miner m` в `renderSprites` считает кадр из
`progressFraction`; `renderOverlays` рисует стрелку (`ShapeType.Filled`);
`renderOutlines` — красную рамку для бура не на руде (`ShapeType.Line`). `drawArrow`
— геометрия, копируйте как есть. В `Renderer` передайте существующий `shapes` в
конструктор `BuildingRenderer` и добавьте два вызова после `renderSprites`.

<a id="р6"></a>
## Р6 — `BuildingRenderer` (добавки) + подключение

`BuildingRenderer` — конструктор получает `ShapeRenderer`, поля и три прохода:

```java
// render/BuildingRenderer.java
package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Miner;
import com.rustorio.model.Tile;
import com.rustorio.model.World;

/**
 * Слой «здания»: спрайты и накладки (стрелки направлений, рамки-подсказки). Спрайты
 * и фигуры рисуются РАЗНЫМИ проходами: {@link SpriteBatch} и {@link ShapeRenderer}
 * нельзя мешать без переоткрытия.
 */
final class BuildingRenderer {

    private static final float TILE = Config.TILE;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final Textures textures;
    private final Grid grid;

    BuildingRenderer(SpriteBatch batch, ShapeRenderer shapes, Textures textures, Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.textures = textures;
        this.grid = grid;
    }

    /** Проход 1: спрайты зданий. */
    void renderSprites(World world, TileRange range) {
        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                // Исчерпывающий switch по sealed-типу: без `default`. Добавишь
                // здание — компилятор ПОТРЕБУЕТ здесь новую ветку.
                switch (b) {
                    case Miner m -> {
                        int frame = (int) (Math.clamp(m.progressFraction(), 0f, 0.999f) * 3);
                        batch.draw(textures.miner[frame], px, py, TILE, TILE);
                    }
                    case Chest _ -> batch.draw(textures.chest, px, py, TILE, TILE);
                }
            }
        }
        batch.end();
    }

    /** Проход 2: стрелки направлений (зелёная — работает, красная — простаивает). */
    void renderOverlays(World world, TileRange range) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Tile tile = world.tile(x, y);
                Building b = tile.building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                switch (b) {
                    case Miner m ->
                            drawArrow(px, py, m.dir(), m.outputItem().isEmpty() && tile.hasOre());
                    case Chest _ -> { /* у ящика накладок нет */ }
                }
            }
        }
        shapes.end();
    }

    /** Проход 3: рамка «бур не на руде». */
    void renderOutlines(World world, TileRange range) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Tile tile = world.tile(x, y);
                if (tile.building() instanceof Miner && !tile.hasOre()) {
                    shapes.setColor(Palette.IDLE);
                    shapes.rect(grid.x(x) + 2, grid.yBottom(y) + 2, TILE - 4, TILE - 4);
                }
            }
        }
        shapes.end();
    }

    /**
     * Треугольник-стрелка в центре клетки, смотрящий в направлении {@code dir}.
     * Зелёная, пока здание работает; красная, пока простаивает.
     */
    private void drawArrow(float px, float py, Direction dir, boolean active) {
        float cx = px + TILE / 2f;
        float cy = py + TILE / 2f;
        float r = TILE * 0.26f;
        // Экранный «низ» (South, dy=+1) — это -Y, поэтому vy = -dir.dy().
        float vx = dir.dx();
        float vy = -dir.dy();
        float perpX = -vy;
        float perpY = vx;
        float tipX = cx + vx * r;
        float tipY = cy + vy * r;
        float base1X = cx - vx * r * 0.6f + perpX * r * 0.7f;
        float base1Y = cy - vy * r * 0.6f + perpY * r * 0.7f;
        float base2X = cx - vx * r * 0.6f - perpX * r * 0.7f;
        float base2Y = cy - vy * r * 0.6f - perpY * r * 0.7f;
        shapes.setColor(active ? Palette.WORKING : Palette.IDLE);
        shapes.triangle(tipX, tipY, base1X, base1Y, base2X, base2Y);
    }
}
```

> Отличия от эталона (срез): в эталоне `renderSprites` принимает ещё `float elapsed`
> (для анимации ленты, L5), а `switch` содержит все восемь зданий; `renderOverlays`
> рисует «призрак» здания под курсором и полоски прогресса печи/сборщика (L8+);
> `renderOutlines` рисует ещё и белую рамку под курсором. Всё это приедет со своими
> зданиями; сегодня в switch ровно два случая — `Miner` и `Chest`.

В `Palette` добавьте два цвета — накладки бура ими и рисуются:

```java
static final Color WORKING = Color.GREEN;   // здание работает — зелёная стрелка
static final Color IDLE = Color.RED;        // простаивает / не на руде — красный
```

(модификатор — как у соседних цветов в файле: пакетно-приватный `static final`).

Подключение в `Renderer`:

```java
// конструктор BuildingRenderer теперь получает shapes (он уже создан в Renderer):
this.buildingRenderer = new BuildingRenderer(batch, shapes, textures, grid);
```

```java
// в render(), между спрайтами зданий и HUD:
buildingRenderer.renderSprites(world, visible);    // 2. спрайты зданий
buildingRenderer.renderOverlays(world, visible);   // 3. стрелки направлений
buildingRenderer.renderOutlines(world, visible);   // 4. рамки
```

### ❌ Частые неправильные варианты (шаг 6)

```java
case Miner m -> {
    int frame = (int) (m.progressFraction() * 3);   // ← без потолка 0.999f
    batch.draw(textures.miner[frame], ...);         // frame == 3 при полном прогрессе
}                                                   // → ArrayIndexOutOfBoundsException: 3
// miner — массив из ТРЁХ кадров (индексы 0,1,2). Потолок 0.999f держит индекс < 3.
```

```java
switch (b) {
    case Miner m -> ...;
    case Chest _ -> ...;
    default -> { }        // ← НАВСЕГДА выключает проверку полноты. В sealed-switch
}                         // default запрещён (правило курса из L2).
```

```java
// renderOverlays/renderOutlines вызваны ДО renderSprites или вообще не вызваны:
// стрелки/рамки уедут под спрайты или пропадут. Порядок = слои: спрайт, потом
// накладки поверх.
```

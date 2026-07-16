# L4 — Решения

> ⚠️ Ответы к [L04-workbook.md](L04-workbook.md). Заходите по ссылке из своего шага:
> сначала 💡 подсказка (П), потом 📄 решение (Р). Javadoc и комментарии в решениях —
> часть ответа.
>
> Код совпадает с эталонным проектом 1:1 там, где срез это позволяет. Где сегодня
> берётся упрощённая версия — стоит пометка, какая лекция доведёт её до эталона.

---

<a id="п1"></a>
## П1 — Протокол в контракте

Подсказка: как в L2 (`direction`) и L3 (`update`) — каждый новый абстрактный метод
интерфейса требует реализации во ВСЕХ классах из `permits`.

Ответ на «Предскажи-1»: две ошибки — по одной на каждую реализацию
(`Miner is not abstract and does not override abstract method output()` и то же про
`Chest`). Компилятор называет ПЕРВЫЙ недостающий метод; дописав его, увидите
следующий. Это и есть «карта задач» на шаги 2–3.

<a id="р1"></a>
## Р1 — `Handoff` + добавка в `Building`

```java
// model/Handoff.java
package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;

/**
 * Что здание готово отдать наружу и в какую сторону.
 *
 * <p>Аналог кортежа {@code (Item, Direction)}. В Java кортежей нет, зато есть
 * {@code record} — он даёт этим двум полям осмысленные имена ({@code item},
 * {@code direction}) и неизменяемость, что читается лучше безымянной пары.
 */
public record Handoff(Item item, Direction direction) {
}
```

Добавка к `Building` (полный файл — Р3; здесь только новые методы контракта):

```java
    /**
     * Что здание готово отдать соседу прямо сейчас (и в какую сторону).
     *
     * @return предмет с направлением, либо {@link Optional#empty()}, если отдавать нечего
     */
    Optional<Handoff> output();

    /** Может ли здание принять {@code item} от соседа прямо сейчас? */
    boolean canAccept(Item item);

    /** Принять предмет в себя (после успешной передачи). */
    void accept(Item item);

    /** Убрать отданный предмет из выхода (после успешной передачи). */
    void removeOutput();
```

Импорт `com.rustorio.core.Item` в `Building`.

---

<a id="п2"></a>
## П2 — Бур отдаёт руду

Подсказка: `output()` заворачивает поле `output` в `Handoff` с направлением `dir`.
`removeOutput()` — это `output = null`.

Ответ на «Предскажи-2»: копать снова буру позволит `removeOutput()` — он обнулит
`output`, и на следующем тике `update` больше не выйдет по `output != null`.
Вызовет его **симуляция** (фаза 2 `moveItems`), когда ящик заберёт руду. То есть бур
крутится не «сам по себе», а ровно тогда, когда его выход освобождает потребитель.

<a id="р2"></a>
## Р2 — `Miner`: четыре метода протокола

```java
    @Override
    public Optional<Handoff> output() {
        return output == null ? Optional.empty() : Optional.of(new Handoff(output, dir));
    }

    @Override
    public boolean canAccept(Item item) {
        return false; // бур ничего не принимает — он источник
    }

    @Override
    public void accept(Item item) {
        // Ничего: буру нельзя ничего отдать.
    }

    @Override
    public void removeOutput() {
        output = null;
    }
```

> Остальное в `Miner` (поля, конструктор, `update`, `progressFraction`, `outputItem`)
> — из L3, без изменений. Эти четыре метода — ровно эталонные: `Miner` в L4 достигает
> своей финальной формы (позже к нему прикоснётся лишь L13 — множитель скорости).

Новые тесты `MinerTest` (`minerExposesDugOreAsHandoff`, `minerDigsAgainAfterOutputRemoved`)
позеленеют в конце шага 3 — раньше проект просто не собирается из-за `Chest`.

---

<a id="п3"></a>
## П3 — Ящик принимает и считает

Подсказка: поле-счётчик `int items`, `accept` его увеличивает, `output` пуст,
`canAccept` всегда `true`, `removeOutput` пуст.

<a id="р3"></a>
## Р3 — `Chest` целиком + полный `Building`

```java
// model/Chest.java
package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;

import java.util.Optional;

/**
 * Ящик: просто копит предметы (счётчик). Ничего не отдаёт и не имеет направления —
 * конечная точка любой цепочки.
 */
public final class Chest implements Building {

    private int items;

    public Chest() {
    }

    @Override
    public void update(TickContext ctx) {
        // Ящик пассивен: за тик ничего не делает, только хранит.
    }

    @Override
    public Optional<Handoff> output() {
        return Optional.empty(); // ящик — «чёрная дыра», наружу не отдаёт
    }

    @Override
    public boolean canAccept(Item item) {
        return true; // примет что угодно
    }

    @Override
    public void accept(Item item) {
        items++;
    }

    @Override
    public void removeOutput() {
        // Нечего убирать: ящик не отдаёт предметы.
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.empty(); // у ящика нет направления
    }

    /** Сколько предметов накоплено (для отрисовки счётчика). */
    public int items() {
        return items;
    }
}
```

> Отличие от эталона: в эталоне у `Chest` есть ещё конструктор `Chest(int items)` для
> восстановления из сохранения — он приедет в **L15** (сохранения). Сегодня ящик
> начинает всегда с нуля.

После этого шага `./gradlew test` зелёный целиком: `ChestTest` (2 теста) и
пополневший `MinerTest` (5 тестов) подтверждают протокол обоих зданий.

`Building` целиком после L4:

```java
// model/Building.java
package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.Optional;

/**
 * Всё, что можно поставить на клетку поля.
 *
 * <p>Sealed-интерфейс: {@code permits} перечисляет ЗАКРЫТЫЙ набор зданий. {@code switch}
 * по зданию не требует {@code default} — все случаи известны компилятору.
 *
 * <p>Домен ничего не знает про экран: здание умеет «жить» ({@link #update}) и
 * участвовать в передаче ({@link #output}/{@link #canAccept}/{@link #accept}).
 */
public sealed interface Building permits Chest, Miner {

    /** «Внутренняя» работа здания за один шаг симуляции (тик). */
    void update(TickContext ctx);

    /** Что здание готово отдать соседу прямо сейчас (и в какую сторону). */
    Optional<Handoff> output();

    /** Может ли здание принять {@code item} от соседа прямо сейчас? */
    boolean canAccept(Item item);

    /** Принять предмет в себя (после успешной передачи). */
    void accept(Item item);

    /** Убрать отданный предмет из выхода (после успешной передачи). */
    void removeOutput();

    /** Направление здания, если оно у него есть (у ящика — нет). */
    Optional<Direction> direction();

    /** «Такое же» здание — тот же тип и то же направление? (default: одинаково для всех) */
    default boolean sameKind(Building other) {
        return this.getClass() == other.getClass()
                && this.direction().equals(other.direction());
    }

    /** Единственная фабрика зданий: {@code switch} исчерпывающий по {@code Tool}. */
    static Optional<Building> create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> Optional.of(new Miner(dir));
            case CHEST -> Optional.of(new Chest());
            case BELT, FURNACE -> Optional.empty(); // L5, L8
        };
    }
}
```

---

<a id="п4"></a>
## П4 — Штамп версии

Подсказка: `Tile` помнит `int claimedTick = -1`; `claim(tick)` возвращает `false`,
если `claimedTick == tick` (уже занята в этом тике), иначе метит и возвращает `true`.
`World` держит `int tick`, `beginTick()` его увеличивает, `claimNeighbor` зовёт
`claim(tick)` у соседней клетки.

<a id="р4"></a>
## Р4 — `Tile.claim` + методы `World`

Добавка в `Tile`:

```java
    /**
     * Номер тика, на котором клетку уже «застолбил» отдающий сосед (штамп версии).
     * Занята ⇔ {@code claimedTick == текущий тик}. Новый тик — все прошлые отметки
     * автоматически устарели: обнуление массива заменилось увеличением одного числа.
     */
    private int claimedTick = -1;

    /**
     * Попытаться застолбить клетку на этот тик.
     *
     * @return {@code true}, если удалось (в этом тике ещё не занимали);
     *         {@code false}, если сосед успел раньше
     */
    boolean claim(int tick) {
        if (claimedTick == tick) {
            return false;
        }
        claimedTick = tick;
        return true;
    }
```

Добавка в `World` (импорты `com.rustorio.core.Direction`,
`org.jspecify.annotations.Nullable`):

```java
    /**
     * Номер текущего тика — им клетки метятся как «уже занятые». Счётчик живёт ЗДЕСЬ,
     * вместе со штампами на клетках: новая симуляция над тем же миром не должна
     * начинать счёт заново и натыкаться на старые штампы.
     */
    private int tick;

    /** Начать новый тик: все прошлые «застолблённые» клетки автоматически освобождаются. */
    public void beginTick() {
        tick++;
    }

    /**
     * Застолбить соседнюю клетку на этот тик: «в неё уже кладут предмет».
     *
     * @return {@code true}, если клетка досталась вам; {@code false}, если сосед успел
     *         раньше или клетки за краем поля не существует
     */
    public boolean claimNeighbor(int x, int y, Direction dir) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) && tile(nx, ny).claim(tick);
    }

    /**
     * Здание на соседней клетке в направлении {@code dir}, если оно там есть.
     *
     * <p>Возвращает {@code null}, а не {@code Optional}: это горячий путь симуляции, и
     * {@code Optional} создавал бы объект на каждое здание на каждом тике.
     */
    public @Nullable Building neighborBuilding(int x, int y, Direction dir) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) ? tile(nx, ny).building() : null;
    }
```

### ❌ Частая ошибка (шаг 4)

```java
boolean claim(int tick) {
    claimedTick = tick;   // ← пометили ДО проверки
    return claimedTick == tick;   // всегда true — штамп не работает
}
// Сначала ПРОВЕРЯЕМ (уже занята в этом тике?), только потом метим.
```

---

<a id="п5"></a>
## П5 — Двухфазная передача

Подсказка: буфер `moves` — поле, не локальная переменная; чистится в начале
`moveItems`. Фаза 1 только читает и собирает `Move`; клетку столбим ПОСЛЕДНИМ
условием (после `canAccept`), иначе «займём» её зря. Фаза 2 отдельным циклом
применяет.

<a id="р5"></a>
## Р5 — `Simulation`: `step` + `moveItems`

```java
package com.rustorio.sim;

import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.model.Building;
import com.rustorio.model.Handoff;
import com.rustorio.model.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Симуляция: как мир продвигается на один тик — конвейер систем по порядку.
 *
 * <p>Систем две: {@link #runMachines} (Update Method из L3) и {@link #moveItems}
 * (двухфазная передача). Симуляция — объект: она владеет переиспользуемым буфером
 * {@link #moves}, чтобы не создавать список заново каждый тик.
 */
public final class Simulation {

    private final World world;

    /** Переиспользуемый буфер запланированных передач: чистится в начале каждого тика. */
    private final List<Move> moves = new ArrayList<>();

    public Simulation(World world) {
        this.world = world;
    }

    /** ОДИН шаг симуляции = конвейер систем по порядку. */
    public void step(TickContext ctx) {
        world.beginTick(); // счётчик штампов живёт в мире — там же, где сами штампы
        runMachines(ctx);
        moveItems();
    }

    /** Система №1: каждое здание делает свою внутреннюю работу (бур копает). */
    private void runMachines(TickContext ctx) {
        world.forEachBuilding((x, y, building) -> building.update(ctx));
    }

    /**
     * Система №2: передача предметов между зданиями в две фазы:
     * <ol>
     *   <li><b>Планируем</b>, только ЧИТАЯ поле: собираем список передач.</li>
     *   <li><b>Применяем</b>, меняя поле.</li>
     * </ol>
     * Штамп тика на клетке гарантирует, что за тик в неё отдаст предмет только один сосед.
     */
    private void moveItems() {
        moves.clear(); // очистить, а не создать заново

        // Фаза 1: планирование (только чтение).
        world.forEachBuilding((x, y, source) -> {
            Optional<Handoff> handoff = source.output();
            if (handoff.isEmpty()) {
                return;
            }
            Item item = handoff.get().item();
            Building receiver = world.neighborBuilding(x, y, handoff.get().direction());
            if (receiver == null || !receiver.canAccept(item)) {
                return;
            }
            // Столбим клетку последней: если сосед успел раньше — передачи не будет.
            if (world.claimNeighbor(x, y, handoff.get().direction())) {
                moves.add(new Move(source, receiver, item));
            }
        });

        // Фаза 2: применение (можно менять).
        for (Move move : moves) {
            move.source().removeOutput();
            move.target().accept(move.item());
        }
    }

    /** Запланированная передача предмета от здания-источника к приёмнику. */
    private record Move(Building source, Building target, Item item) {
    }
}
```

> Отличие от эталона: у эталонной `Simulation` в `step` пять систем (ленты, развилки,
> подземка) — они приедут с L5/L10. Сама `moveItems` и `Move` — 1:1.

### ❌ Частые неправильные варианты (шаг 5)

```java
// Столбим клетку ПЕРВОЙ, до проверки соседа:
if (!world.claimNeighbor(x, y, dir)) return;
Building receiver = world.neighborBuilding(x, y, dir);
if (receiver == null || !receiver.canAccept(item)) return;  // ← клетка уже занята зря!
// Застолбили клетку, потом передумали — а сосед напротив в этот тик уже не сможет
// туда положить, хотя мы ничего не кладём. Столбить — ПОСЛЕДНИМ условием.
```

```java
// Фазы слиты: меняем мир прямо в обходе.
world.forEachBuilding((x, y, source) -> {
    ...
    source.removeOutput();
    receiver.accept(item);   // ← невоспроизводимо: результат зависит от порядка обхода
});
```

```java
List<Move> moves = new ArrayList<>();   // ← локально, новый список КАЖДЫЙ тик
// Работает, но мусорит. Буфер — поле, которое чистят (moves.clear()).
```

---

<a id="п6"></a>
## П6 — `ItemRenderer`

Подсказка: слой отдельный, потому что предметы рисуются поверх зданий. `render`
делает `switch` по зданию: бур → иконка его `outputItem`, ящик → счётчик `items()`.
`iconFor(Item)` — `switch` по `Item`.

<a id="р6"></a>
## Р6 — `ItemRenderer` целиком + подключение

```java
// render/ItemRenderer.java
package com.rustorio.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Miner;
import com.rustorio.model.World;

/**
 * Слой «предметы»: иконки на машинах и счётчики ящиков. Отдельный слой от
 * {@link BuildingRenderer}, потому что предметы рисуются ПОВЕРХ спрайтов зданий.
 * По ходу курса сюда добавится груз, едущий по лентам (L5).
 */
final class ItemRenderer {

    private static final float TILE = Config.TILE;

    private final SpriteBatch batch;
    private final BitmapFont font;
    private final Textures textures;
    private final Grid grid;

    ItemRenderer(SpriteBatch batch, BitmapFont font, Textures textures, Grid grid) {
        this.batch = batch;
        this.font = font;
        this.textures = textures;
        this.grid = grid;
    }

    void render(World world, TileRange range) {
        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                Building b = world.tile(x, y).building();
                if (b == null) {
                    continue;
                }
                float px = grid.x(x);
                float py = grid.yBottom(y);
                switch (b) {
                    case Miner m -> m.outputItem().ifPresent(it -> drawItemIcon(px, py, it));
                    case Chest c -> drawCounter(px, py, c.items());
                }
            }
        }
        batch.end();
    }

    /** Иконка предмета в правом нижнем углу клетки. */
    private void drawItemIcon(float px, float py, Item item) {
        float size = TILE * 0.4f;
        batch.draw(iconFor(item), px + TILE - size - 2, py + 2, size, size);
    }

    /** Число на здании (сколько накоплено в ящике). */
    private void drawCounter(float px, float py, int value) {
        font.getData().setScale(0.9f);
        font.setColor(Color.WHITE);
        font.draw(batch, Integer.toString(value), px + 6, py + 20);
    }

    /** Спрайт предмета. Исчерпывающий switch по {@code enum Item}. */
    private TextureRegion iconFor(Item item) {
        return switch (item) {
            case IRON_ORE -> textures.ironOre;
            case IRON_PLATE -> textures.ironPlate;
        };
    }
}
```

> Отличие от эталона: у эталонного `ItemRenderer` switch по зданию покрывает все
> восемь зданий и рисует ещё груз на лентах; `iconFor` — все предметы. Здесь ровно два
> здания и два предмета. Эталон отдаёт спрайт через `textures.itemTexture(item)`;
> в каркасе такого метода нет, поэтому маппинг `Item → регион` живёт локальным
> `iconFor`.

Подключение в `Renderer`:

```java
private final ItemRenderer itemRenderer;
```

```java
// в конструкторе, после buildingRenderer:
this.itemRenderer = new ItemRenderer(batch, font, textures, grid);
```

```java
// в render(), после слоёв зданий, до HUD:
buildingRenderer.renderOutlines(world, visible);   // 4. рамки
itemRenderer.render(world, visible);               // 5. предметы/счётчики
```

### ❌ Частая ошибка (шаг 6)

```java
switch (b) {
    case Miner m -> ...;
    case Chest c -> ...;
    default -> { }   // ← в sealed-switch запрещено (правило из L2)
}
```

# L2 — Решения

> ⚠️ Ответы к [L02-workbook.md](L02-workbook.md). Заходите по ссылке из своего шага:
> сначала 💡 подсказка (П), потом 📄 решение (Р). Javadoc и комментарии в решениях —
> часть ответа, читайте их.

---

## П1 — Почему не enum

Константа enum — **синглтон**: `Tool.CHEST` один на всю программу. А зданий типа
«ящик» на карте много, и у каждого — СВОЁ состояние (скоро: свой счётчик предметов;
у каждой печи — свой прогресс плавки). Значит, здание — это обычный объект,
создаваемый `new` сколько угодно раз. Но нам нужна и «enum-ность»: замкнутый список
видов + исчерпывающий разбор. Sealed-интерфейс — ровно эта комбинация:
«классы как классы, а список — как у enum».

## Р1 — Building и Chest целиком

```java
// model/Building.java
package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Tool;

import java.util.Optional;

/**
 * Всё, что можно поставить на клетку поля.
 *
 * <p><b>Главная идея всей игры.</b> Sealed-интерфейс: {@code permits} перечисляет
 * ЗАКРЫТЫЙ набор зданий. Никто не добавит новый вид здания в обход этого списка,
 * а {@code switch} по зданию не требует {@code default} — все случаи известны
 * компилятору, и новое здание он потребует разобрать везде.
 *
 * <p>Отрисовки здесь СПЕЦИАЛЬНО нет: домен ничего не знает про экран и libGDX.
 * «Как здание выглядит» — забота слоя {@code render}.
 */
public sealed interface Building permits Chest {

    /** Направление здания, если оно у него есть (у ящика — нет). */
    Optional<Direction> direction();

    /**
     * «Такое же» здание — тот же тип и то же направление?
     *
     * <p>Нужно, чтобы рисование мышью с зажатой ЛКМ не пересоздавало здание
     * каждый кадр. Реализация одинакова для всех зданий, поэтому это
     * {@code default}-метод: тип сравниваем по классу, направление — по значению.
     */
    default boolean sameKind(Building other) {
        return this.getClass() == other.getClass()
                && this.direction().equals(other.direction());
    }

    /**
     * Создать НОВОЕ здание по выбранному инструменту и направлению.
     *
     * <p>Единственная «фабрика» зданий. {@code switch} исчерпывающий по
     * {@code enum Tool}: добавишь инструмент — компилятор потребует ветку.
     * {@link Optional}, потому что зданий пока меньше, чем инструментов;
     * когда каждая ветка вернёт здание, Optional станет лишним — и мы его уберём.
     */
    static Optional<Building> create(Tool tool, Direction dir) {
        return switch (tool) {
            case CHEST -> Optional.of(new Chest());
            // Эти ветки заполнят лекции про бур, ленту и печь:
            case MINER, BELT, FURNACE -> Optional.empty();
        };
    }
}
```

```java
// model/Chest.java
package com.rustorio.model;

import com.rustorio.core.Direction;

import java.util.Optional;

/**
 * Ящик: пассивная коробка, конечная точка любой будущей цепочки.
 * Направления не имеет; счётчик предметов появится вместе с предметами.
 */
public final class Chest implements Building {

    @Override
    public Optional<Direction> direction() {
        return Optional.empty(); // у ящика нет направления
    }
}
```

Обратите внимание: `Chest` — `final`. Классы из `permits` обязаны быть `final`,
`sealed` или `non-sealed` — замкнутость списка распространяется вглубь.

### ❌ Частые неправильные варианты (шаг 1)

```java
public class Chest implements Building { ... }
// компилятор: sealed, non-sealed or final modifiers expected
// Забыли final. Замкнутость иерархии распространяется вглубь — иначе кто угодно
// отнаследовался бы от Chest и пролез в иерархию через чёрный ход.
```

```java
static Optional<Building> create(Tool tool, Direction dir) {
    return switch (tool) {
        case CHEST -> Optional.of(new Chest());
        default -> Optional.empty();   // ← компилируется. И ЭТО ПЛОХО.
    };
}
// default «съедает» будущие инструменты: добавите Tool.LAB — компилятор
// промолчит, а клавиша 8 будет молча мёртвой. Перечисляйте ветки явно:
// case MINER, BELT, FURNACE -> Optional.empty();
```

```java
@Override
public Optional<Direction> direction() {
    return null;   // ← НЕТ. Возвращать null вместо Optional.empty() —
}                  // худшее из двух миров: сигнатура обещает коробку,
                   // а прилетает бомба. Optional либо есть, либо empty.
```

---

## П2 — Какой баг предотвращает правило «sameKind — не пересоздаём»

Без него зажатая ЛКМ заменяет здание новым 60 раз в секунду. Новый объект = новое
пустое состояние. Как только у ящика появится счётчик — он будет **обнуляться**,
пока вы держите кнопку над клеткой. Как только у ленты появится груз — лента будет
**терять предметы** под курсором. Хуже всего, что баг «мигающий»: в тестах клик
одиночный — зелено, а у игрока с зажатой мышью всё пропадает. Поэтому правило
закрепляется тестом с `assertSame` уже сейчас, пока состояние зданий пустое.

## П3 — Tile и World: направление

`Tile`: поле `@Nullable Building building` (импорт
`org.jspecify.annotations.Nullable`), публичный геттер, **пакетно**-приватный
сеттер (без модификатора). `World.place`: guard `inBounds` → прочитать клетку →
два правила защиты → `setBuilding`. `World.remove`: guard `inBounds` →
`setBuilding(null)`.

## Р3 — Tile и World

`Tile` целиком:

```java
package com.rustorio.model;

import org.jspecify.annotations.Nullable;

/**
 * Одна клетка поля: есть ли под ней руда и какое здание на ней стоит.
 *
 * <p>{@code ore} — {@code final}: залежи задаются при генерации карты и не
 * двигаются. {@code building} изменяемо: игрок ставит и сносит здания. Наружу —
 * только чтение; менять здание клетки вправе только {@link World}, чтобы правила
 * постановки/сноса жили в одном месте.
 */
public final class Tile {

    private final boolean ore;
    private @Nullable Building building;

    Tile(boolean ore) {
        this.ore = ore;
    }

    /** Есть ли под клеткой залежь руды (бур будет копать только тут). */
    public boolean hasOre() {
        return ore;
    }

    /** Здание на клетке, либо {@code null}, если пусто. */
    public @Nullable Building building() {
        return building;
    }

    // Пакетно-приватно: менять здание клетки вправе только World.
    void setBuilding(@Nullable Building building) {
        this.building = building;
    }
}
```

Добавка в `World` (после `tile`/`chunk`/`key`):

```java
/**
 * Поставить здание. Безопасно к координатам вне поля. Два правила защиты:
 * <ul>
 *   <li>НЕ затираем здание ДРУГОГО типа — чтобы протаскивание линии не сносило
 *       соседей;</li>
 *   <li>НЕ пересоздаём точно такое же (тип + направление) — иначе здание теряло
 *       бы своё состояние каждый кадр, пока держишь ЛКМ.</li>
 * </ul>
 */
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
        // Тот же тип, другое направление: замена. Для ящика недостижимо
        // (у него нет направления), станет важным с направленными зданиями.
    }
    tile.setBuilding(building);
}

/** Убрать здание с клетки. Безопасно к координатам вне поля. */
public void remove(int x, int y) {
    if (!inBounds(x, y)) {
        return;
    }
    tile(x, y).setBuilding(null);
}
```

---

## П4 — Ввод: направление

Начните с «многословной» версии из алгоритма шага 3 (`isPresent()`/`get()` через
`if`) — она правильная. Затем сверните в цепочку: `game.hover().ifPresent(...)` →
внутри фабрика + `ifPresent(place)`. Для ПКМ — `Input.Buttons.RIGHT` и `remove`,
без фабрики. Не забудьте импорт `com.rustorio.model.Building`.

## Р4 — InputHandler: что добавить

В `handle`, после блока с `R`:

```java
// Строительство и снос. Кнопки ОПРАШИВАЮТСЯ каждый кадр (isButtonPressed):
// ведёшь зажатую мышь — рисуешь линию. От пересоздания на месте защищает
// правило sameKind в World.place — ввод может позволить себе быть простым,
// потому что правила лежат в мире.
if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
    game.hover().ifPresent(cell ->
            Building.create(game.tool(), game.direction())
                    .ifPresent(b -> game.world().place(cell.x(), cell.y(), b)));
}
if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
    game.hover().ifPresent(cell -> game.world().remove(cell.x(), cell.y()));
}
```

«Многословная» версия с `isPresent()/get()` из шага 3 — тоже засчитывается: она
делает то же самое. Свёрнутая читается быстрее, когда глаз привыкнет к лямбдам;
если пока не привык — оставьте развёрнутую и вернитесь через лекцию.

### ❌ Частая ошибка (шаг 3)

```java
if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) { ... }
// «JustPressed» = только в кадр нажатия: линию протаскиванием нарисовать
// нельзя, каждый ящик — отдельный клик. Нам нужно «зажата» = isButtonPressed;
// от пересоздания на месте защищает sameKind-правило мира, а не ввод.
```

---

## П5 — Renderer: направление

Ответ на «Предскажи-3»: когда в `permits` добавится `Miner`, компилятор скажет про
этот `switch` «не все случаи разобраны» — и не соберёт проект, пока вы не решите,
как бур выглядит. С веткой `default -> {}` проект собрался бы молча — и бур просто
не рисовался бы, а вы бы искали «почему пусто» глазами. Ошибка компиляции — это
подарок: она называет файл и строку; молчаливый баг не называет ничего.

## Р5 — BuildingRenderer целиком + подключение

```java
// render/BuildingRenderer.java
package com.rustorio.render;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.core.Config;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.World;

/**
 * Слой «здания»: спрайты по видимому диапазону клеток.
 *
 * <p>Исчерпывающий {@code switch} по sealed-типу — БЕЗ {@code default}: добавите
 * здание в {@code permits} — компилятор потребует здесь новую ветку. Это и есть
 * «карта задач от компилятора».
 */
final class BuildingRenderer {

    private static final float TILE = Config.TILE;

    private final SpriteBatch batch;
    private final Textures textures;
    private final Grid grid;

    BuildingRenderer(SpriteBatch batch, Textures textures, Grid grid) {
        this.batch = batch;
        this.textures = textures;
        this.grid = grid;
    }

    /** Спрайты зданий. Обходит только клетки из {@code range} (culling). */
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
                switch (b) {
                    case Chest _ -> batch.draw(textures.chest, px, py, TILE, TILE);
                }
            }
        }
        batch.end();
    }
}
```

В `Renderer`: удалить «спящее» поле `textures` вместе с `@SuppressWarnings` и
комментарием, добавить поле слоя и вызов:

```java
private final BuildingRenderer buildingRenderer;
```

```java
// в конструкторе, после создания worldRenderer:
this.buildingRenderer = new BuildingRenderer(batch, textures, grid);
```

```java
// в render(), между землёй и HUD:
worldRenderer.render(world, visible, game.hover().orElse(null)); // 1. фон + сетка
buildingRenderer.renderSprites(world, visible);                  // 2. спрайты зданий

// HUD — поверх всего, в координатах окна.
```

Порядок вызовов — это порядок слоёв картинки: земля под зданиями, HUD поверх всего.

### ❌ Частые неправильные варианты (шаг 4)

```java
switch (b) {
    case Chest _ -> batch.draw(...);
    default -> {}   // ← компилируется — и НАВСЕГДА выключает проверку полноты.
}                   // Правило курса: в switch по sealed-типам default запрещён.
```

```java
for (...) {
    batch.begin();                    // ← begin/end на КАЖДУЮ клетку —
    batch.draw(...);                  // видеокарта рисует по одному спрайту,
    batch.end();                      // весь смысл батчинга (и атласа из L0) убит.
}                                     // begin — один раз ДО циклов, end — после.
```

```java
float py = y * Config.TILE;           // ← свой расчёт Y вместо grid.yBottom(y):
                                      // здания встанут зеркально по вертикали.
                                      // Переворот оси живёт ТОЛЬКО в Grid.
```

# L8 — Решения

> ⚠️ Ответы к [L08-workbook.md](L08-workbook.md). Заходите по ссылке из своего
> шага: сначала 💡 подсказка (П), потом 📄 решение (Р). Javadoc и комментарии —
> часть ответа.
>
> Код совпадает с ранней версией эталона (git-коммит `66f540d`, 86-строчный
> `ProcessKernel`), адаптированной под `TickContext` (сигнатура `update` в
> `Building` появилась только в L3 нашего курса, в этом коммите её ещё не было).
> Финальная версия эталона (218 строк) хранит склад из нескольких ингредиентов и
> очередь готовой продукции — это материал L9 (составные рецепты).

---

<a id="разогрев"></a>
## Р+ — Ответы разминки (одной строкой, не подглядывая до попытки)

1. Три: `BuildingRenderer.renderSprites`, `BuildingRenderer.renderOverlays`,
   `ItemRenderer.render` — компилятор написал `the switch statement does not
   cover all possible input values` в каждом.
2. Протокол — вопрос-ответ «что отдаёшь / примешь ли / прими / убери отданное»;
   он одинаков для любого здания, потому что `Simulation.moveItems` работает с
   интерфейсом `Building`, а не с конкретными классами.
3. `Optional` был нужен, пока не у каждого `Tool` было здание (ветка `FURNACE`
   возвращала `empty()`); тетрадь L2 обещала убрать обёртку, когда «каждая
   ветка вернёт здание» — это и произошло сегодня.
4. Update Method — каждый объект получает единообразный вызов на каждом тике;
   в `Building` это метод `update(TickContext ctx)`.

---

<a id="п1"></a>
## П1 — Данные, а не код

Подсказка: `Recipe.find` уже ищет по ПАРЕ `(machine, input)` в списке — механизм
поиска не завязан на то, сколько записей в списке.

Ответ на «Предскажи-1»: чтобы завести сборщика в L9, достаточно ДОПИСАТЬ ВТОРУЮ
СТРОКУ в `Recipe.ALL` (`new Recipe(Tool.ASSEMBLER, Item.IRON_PLATE, Item.GEAR,
Config.ASSEMBLE_TIME)`) — ни `ProcessKernel`, ни `Recipe.find`, ни `switch` не
меняются вообще. Это и есть «500 рецептов = 500 строк данных, ноль нового
кода», обещанное в javadoc `Recipe`.

<a id="р1"></a>
## Р1 — `Recipe` и `ProcessKernel` целиком

```java
// model/Recipe.java
package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;

import java.util.List;
import java.util.Optional;

public record Recipe(Tool machine, Item input, Item output, float time) {

    private static final List<Recipe> ALL = List.of(
            new Recipe(Tool.FURNACE, Item.IRON_ORE, Item.IRON_PLATE, Config.SMELT_TIME)
    );

    public static Optional<Recipe> find(Tool machine, Item input) {
        return ALL.stream()
                .filter(r -> r.machine == machine && r.input == input)
                .findFirst();
    }
}
```

```java
// model/ProcessKernel.java
package com.rustorio.model;

import com.rustorio.core.Item;
import com.rustorio.core.Tool;

import java.util.Optional;

final class ProcessKernel {

    private final Tool machine;
    private Item input;
    private float progress;
    private Item output;

    ProcessKernel(Tool machine) {
        this.machine = machine;
    }

    void update(float dt) {
        if (output == null && input != null) {
            Recipe recipe = Recipe.find(machine, input).orElseThrow(() ->
                    new IllegalStateException(
                            "canAccept должен был гарантировать рецепт для " + input));
            progress += dt;
            if (progress >= recipe.time()) {
                output = recipe.output();
                input = null;
                progress = 0f;
            }
        }
    }

    boolean canAccept(Item item) {
        return input == null && Recipe.find(machine, item).isPresent();
    }

    void accept(Item item) {
        this.input = item;
    }

    Optional<Item> output() {
        return Optional.ofNullable(output);
    }

    void removeOutput() {
        this.output = null;
    }

    Optional<Item> input() {
        return Optional.ofNullable(input);
    }

    float progressFraction() {
        if (input == null) {
            return 0f;
        }
        return Recipe.find(machine, input)
                .map(r -> Math.min(progress / r.time(), 1f))
                .orElse(0f);
    }
}
```

### ❌ Частые неправильные варианты (шаг 1)

```java
boolean canAccept(Item item) {
    return input == null; // ← забыли проверить, что рецепт вообще существует
}
// Печь начнёт "принимать" предмет, для которого рецепта нет: input запишется,
// а update() наткнётся на orElseThrow — исключение вместо тихого игнорирования
// чужого предмета.
```

---

<a id="п2"></a>
## П2 — Почему `ProcessKernel` не знает про `TickContext`

Подсказка: посмотрите, от чего РЕАЛЬНО зависит `ProcessKernel.update` —
только от количества прошедших секунд.

Ответ на «Предскажи-2»: специально. `ProcessKernel` — это модель переработки
сама по себе, ей нужно только «сколько секунд прошло» (`float dt`), а не вся
«коробка» контекста тика. Так `ProcessKernel` остаётся decoupled от каркаса
симуляции — его можно было бы протестировать или переиспользовать вообще без
`TickContext`, `Building` или `World`. `Furnace` — единственное место, которое
знает про `TickContext`, и её же дело — достать из него нужное число (`ctx.dt()`)
и передать дальше уже как простой `float`.

<a id="р2"></a>
## Р2 — `Furnace` целиком

```java
// model/Furnace.java
package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;

import java.util.Optional;

/**
 * Печь: принимает руду, за {@link com.rustorio.core.Config#SMELT_TIME} плавит
 * её в пластину и отдаёт соседу по направлению {@code dir}.
 *
 * <p>Вся логика переработки делегирована {@link ProcessKernel} — печь лишь
 * добавляет к нему направление отдачи. Печь НЕ является {@code ProcessKernel}
 * (не наследует его) — она им ВЛАДЕЕТ и делегирует ему протокол L4.
 */
public final class Furnace implements Building {

    private final Direction dir;
    private final ProcessKernel kernel = new ProcessKernel(Tool.FURNACE);

    public Furnace(Direction dir) {
        this.dir = dir;
    }

    @Override
    public void update(TickContext ctx) {
        kernel.update(ctx.dt());
    }

    @Override
    public Optional<Handoff> output() {
        return kernel.output().map(item -> new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item item) {
        return kernel.canAccept(item);
    }

    @Override
    public void accept(Item item) {
        kernel.accept(item);
    }

    @Override
    public void removeOutput() {
        kernel.removeOutput();
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    public Direction dir() {
        return dir;
    }

    public Optional<Item> outputItem() {
        return kernel.output();
    }

    public boolean isWorking() {
        return kernel.input().isPresent();
    }

    public float progressFraction() {
        return kernel.progressFraction();
    }
}
```

### ❌ Частые неправильные варианты (шаг 2)

```java
@Override
public void update(TickContext ctx) {
    kernel.update(ctx); // ← не компилируется: ProcessKernel.update ждёт float,
                        // не TickContext. Соблазн "передать всё" ломает
                        // независимость ядра от каркаса симуляции.
}
```

---

<a id="п3"></a>
## П3 — Куда компилятор потребует ветку

Подсказка: те же три места, что и в L5 с лентой — исчерпывающих `switch` по
`Building` в каркасе ровно три.

Ответ на «Предскажи-3»: `BuildingRenderer.renderSprites`,
`BuildingRenderer.renderOverlays`, `ItemRenderer.render` — ровно те же три
места, что и при добавлении `Belt` в L5, потому что новых `switch` по
`Building` с тех пор не появилось. `Building.create` — тоже нужно поправить, но
это switch по `Tool`, и ветка `FURNACE` там уже существовала (заглушкой).

<a id="р3"></a>
## Р3 — `Building`: `permits` + `create`

```java
public sealed interface Building permits Chest, Miner, Belt, Furnace {
```

```java
    static Optional<Building> create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> Optional.of(new Miner(dir));
            case CHEST -> Optional.of(new Chest());
            case BELT -> Optional.of(new Belt(dir));
            case FURNACE -> Optional.of(new Furnace(dir));
        };
    }
```

> Сигнатура пока остаётся `Optional<Building>` — уборка обёртки отдельным
> шагом 5, честно и по одной причине изменения за раз.

---

<a id="п4"></a>
## П4 — Сколько мест держат `.orElseThrow()`/`.ifPresent()`

Подсказка: ищите `Building.create(` во всей кодовой базе, не только в
production-коде — тесты тоже вызывающий код.

Ответ на «Предскажи-4»: 7 файлов, 17 вызовов всего — `InputHandler.java` (1,
`.ifPresent(...)`) и шесть тестовых файлов с `.orElseThrow()`:
`GameStateTest` (1), `WorldPlaceRemoveTest` (3), `BeltSegmentTest` (1),
`BeltNetworkTest` (1), `MovementTest` (6), `TransferTest` (4). Каждый вызов
просто перестаёт быть нужен — сама постройка объекта (`new Miner(dir)` и т.п.)
не меняется, меняется только то, что оборачивало результат.

<a id="р4"></a>
## Р4 — Отрисовка печи + бонусный рефакторинг

`BuildingRenderer` — импорт, спрайт, стрелка+полоска, хелпер:

```java
import com.rustorio.model.Furnace;
```

```java
        // ...в switch (b) внутри renderSprites:
        case Furnace f -> batch.draw(
                f.isWorking() ? textures.furnaceOn : textures.furnaceOff,
                px, py, TILE, TILE);
```

```java
        // ...в switch (b) внутри renderOverlays:
        case Furnace f -> {
            drawArrow(px, py, f.dir(), f.isWorking());
            drawProgressBar(px, py, f.progressFraction());
        }
```

```java
    /** Полоска прогресса переработки у нижнего края клетки. */
    private void drawProgressBar(float px, float py, float fraction) {
        float pad = 3f;
        float inner = TILE - 2 * pad;
        shapes.setColor(Palette.BAR);
        shapes.rect(px + pad, py + pad, inner * fraction, 4f);
    }
```

`Palette` — новая константа:

```java
    static final Color BAR = rgb(230, 190, 60);  // полоска прогресса переработки
```

`ItemRenderer` — ветка:

```java
        // ...в switch (b):
        case Furnace f -> f.outputItem().ifPresent(it -> drawItemIcon(px, py, it));
```

Бонус — `Building.create` без `Optional`:

```java
    static Building create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> new Miner(dir);
            case CHEST -> new Chest();
            case BELT -> new Belt(dir);
            case FURNACE -> new Furnace(dir);
        };
    }
```

`InputHandler` — постройка без обёртки:

```java
        if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
            game.hover().ifPresent(cell -> game.world().place(cell.x(), cell.y(),
                    Building.create(game.tool(), game.direction())));
        }
```

Во всех тестовых файлах — просто уберите `.orElseThrow()` после каждого
`Building.create(...)`; сама постройка мира не меняется.

### ❌ Частые неправильные варианты (шаг 4)

```java
case Furnace f -> f.outputItem().ifPresent(it -> drawItemCentered(px, py, it));
// drawItemCentered — для грузов НА ЛЕНТЕ (по центру клетки). Продукт на машине
// рисуется в углу, как у бура: drawItemIcon. Перепутать — значит визуально
// увеличить пластину и сдвинуть её не в тот угол.
```

```java
static Building create(Tool tool, Direction dir) {
    return switch (tool) {
        case MINER -> new Miner(dir);
        case CHEST -> new Chest();
        case BELT -> new Belt(dir);
        // ← забыли ветку FURNACE после смены сигнатуры
    };
}
// compileJava: "the switch expression does not cover all possible input
// values" — тот же чек-лист компилятора, что и для permits, только теперь
// над Tool, а не Building.
```

---

<a id="билет"></a>
## Ответы на выходной билет

1. HAS-A — `Furnace` хранит ссылку на `ProcessKernel` как поле и делегирует
   вызовы; IS-A — `Furnace` расширяла бы `ProcessKernel` (или общий предок) и
   наследовала бы его поведение как часть СВОЕГО типа. Мы выбрали HAS-A.
2. Потому что только сегодня ВСЕ ветки `Tool` (`MINER`/`CHEST`/`BELT`/`FURNACE`)
   получили настоящую реализацию — раньше `FURNACE` возвращал `Optional.empty()`,
   и обёртка была правдой, а не церемонией.
3. Таблица рецептов (`Recipe.ALL`) — данные, не код; в L9 сборщик добавится
   ещё одной строкой в этот список, без единой правки `ProcessKernel` или
   `switch`.

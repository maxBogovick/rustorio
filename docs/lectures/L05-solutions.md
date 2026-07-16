# L5 — Решения

> ⚠️ Ответы к [L05-workbook.md](L05-workbook.md). Заходите по ссылке из своего шага:
> сначала 💡 подсказка (П), потом 📄 решение (Р). Javadoc и комментарии — часть ответа.
>
> Код совпадает с ранней «простой лентой» эталона (git-коммит `66f540d`), адаптированной
> под `TickContext` и `@Nullable`. Финальная лента эталона сложнее — это L6–L7.

---

<a id="п1"></a>
## П1 — Что двигает ленту

Подсказка: посмотрите на `moveItems` из L4 (открывать не надо, вспомните). Он берёт у
здания `output()`, находит соседа, спрашивает `canAccept`, потом `accept`. Ему всё
равно, кто отдал — бур или лента.

Ответ на «Предскажи-1»: цепочку двигает **система `moveItems` из L4**, потому что
лента реализует тот же протокол: её `output()` возвращает груз с направлением, а
`accept()`/`canAccept()` позволяют соседу его принять. `moveItems` не знает слова
`Belt` — он работает с интерфейсом `Building`. Каждый тик он перекладывает груз с
ленты на следующую (если та свободна) — вот и движение.

<a id="р1"></a>
## Р1 — `Belt` целиком

```java
// model/Belt.java
package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Лента: держит ровно один предмет и толкает его вперёд по направлению {@code dir}.
 * Сама ничего не производит — только переносит.
 *
 * <p><b>Как лента «везёт», ничего не делая в {@link #update}.</b> Всё движение — это
 * тот же протокол передачи из L4: лента отдаёт свой предмет соседу ({@link #output}) и
 * принимает от предыдущего ({@link #accept}). Систему {@code moveItems} менять не
 * пришлось: для неё лента — просто ещё один {@link Building} с выходом и приёмом.
 *
 * <p><b>Один предмет на клетку — это упрощение.</b> Настоящая лента возит НЕСКОЛЬКО
 * грузов и рисует их плавно между клетками. Это переписывание (транспортные линии,
 * слоты, {@code tickAlpha}) — L6–L7; сегодня предмет прыгает по клетке за тик.
 */
public final class Belt implements Building {

    private final Direction dir;
    /** Предмет на ленте (или {@code null} — лента пуста). */
    private @Nullable Item item;

    public Belt(Direction dir) {
        this.dir = dir;
        this.item = null;
    }

    @Override
    public void update(TickContext ctx) {
        // Лента за тик не «работает» сама — предметы двигает система moveItems.
    }

    @Override
    public Optional<Handoff> output() {
        return item == null ? Optional.empty() : Optional.of(new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item incoming) {
        return item == null; // свободна ⇒ примет любой предмет
    }

    @Override
    public void accept(Item incoming) {
        this.item = incoming;
    }

    @Override
    public void removeOutput() {
        this.item = null;
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    // ── Геттеры для отрисовки ────────────────────────────────────────
    public Direction dir() {
        return dir;
    }

    public Optional<Item> item() {
        return Optional.ofNullable(item);
    }
}
```

### ❌ Частые неправильные варианты (шаг 1)

```java
public boolean canAccept(Item incoming) {
    return true;   // ← лента примет ВСЕГДА
}
// Тогда занятая лента примет второй груз и затрёт первый — предмет исчез.
// Свободна ⇔ item == null.
```

```java
public void update(TickContext ctx) {
    // «двигаю сам»: найти соседа, переложить...
}
// Здание не знает своих координат — соседа ему не найти. Двигать может только
// симуляция (у неё есть карта). Лента лишь хранит и отвечает протоколу.
```

---

<a id="п2"></a>
## П2 — Куда компилятор потребует ветку

Подсказка: ветка `case Belt` нужна в КАЖДОМ исчерпывающем `switch (b)` по
`Building`. Их три: спрайты, накладки (BuildingRenderer) и предметы (ItemRenderer).
`create` уже перечисляет `BELT` — там просто меняем `empty` на `new Belt`.

Ответ на «Предскажи-2»: три `switch` потребуют `case Belt` —
`BuildingRenderer.renderSprites`, `BuildingRenderer.renderOverlays`,
`ItemRenderer.render`. (`renderOutlines` — не `switch`, а `instanceof Miner`, его лента
не касается.) Плюс правка ветки `BELT` в `Building.create`. Компилятор назовёт каждый
файл и строку.

<a id="р2"></a>
## Р2 — `Building`: `permits` + `create`

```java
public sealed interface Building permits Chest, Miner, Belt {
```

```java
    static Optional<Building> create(Tool tool, Direction dir) {
        return switch (tool) {
            case MINER -> Optional.of(new Miner(dir));
            case CHEST -> Optional.of(new Chest());
            case BELT -> Optional.of(new Belt(dir));
            // Ветка печи заполнится в L8:
            case FURNACE -> Optional.empty();
        };
    }
```

> Остальное в `Building` — без изменений. Заметьте: `Simulation` в списке правок нет.

---

<a id="п3"></a>
## П3 — Отрисовка ленты

Подсказка: спрайт ленты рисуется повёрнутым (`batch.draw` с углом `beltRotation(dir)`)
и с кадром по настенному времени. Груз — по центру клетки (`drawItemCentered`), а не в
углу. `elapsed` копится в `Renderer` и передаётся в `renderSprites`.

<a id="р3"></a>
## Р3 — Рендер: `BuildingRenderer`, `ItemRenderer`, `Renderer`

`BuildingRenderer` — константа, ветки и хелпер:

```java
    /** Смена кадров ленты в секунду. */
    private static final float BELT_ANIM_SPEED = 4.0f;
```

```java
    /** Проход 1: спрайты зданий. {@code elapsed} — настенные часы для анимации ленты. */
    void renderSprites(World world, TileRange range, float elapsed) {
        // ...в switch (b):
        case Belt belt -> {
            int frame = (int) (elapsed * BELT_ANIM_SPEED) % 2;
            batch.draw(textures.belt[frame], px, py, TILE / 2, TILE / 2,
                    TILE, TILE, 1f, 1f, beltRotation(belt.dir()));
        }
```

```java
    // ...в renderOverlays, switch (b):
        case Belt _ -> { /* у ленты стрелки нет — её направление видно по спрайту */ }
```

```java
    /** Угол поворота спрайта ленты (спрайт нарисован вдоль East), Y-вверх. */
    private static float beltRotation(Direction dir) {
        return switch (dir) {
            case EAST -> 0f;
            case NORTH -> 90f;
            case WEST -> 180f;
            case SOUTH -> -90f;
        };
    }
```

Аргументы `batch.draw(region, x, y, originX, originY, width, height, scaleX, scaleY,
rotation)`: точка вращения — центр клетки (`TILE/2, TILE/2`), угол — от направления.

`ItemRenderer` — ветка и хелпер:

```java
    // ...в switch (b):
        case Belt belt -> belt.item().ifPresent(it -> drawItemCentered(px, py, it));
```

```java
    /** Иконка предмета по центру клетки (груз на ленте). */
    private void drawItemCentered(float px, float py, Item item) {
        float size = TILE * 0.5f;
        batch.draw(iconFor(item), px + (TILE - size) / 2f, py + (TILE - size) / 2f, size, size);
    }
```

`Renderer` — накопление настенного времени:

```java
    /** Настенное время с запуска — гонит анимацию ленты (к симуляции отношения не имеет). */
    private float elapsed;
```

```java
    public void render(GameState game, float delta) {
        World world = game.world();
        elapsed += delta;
        // ...
        buildingRenderer.renderSprites(world, visible, elapsed);   // 2. спрайты зданий
```

### ❌ Частые неправильные варианты (шаг 3)

```java
case Belt belt -> belt.item().ifPresent(it -> drawItemIcon(px, py, it));
// drawItemIcon рисует в УГЛУ (для выхода бура). Груз на ленте — по ЦЕНТРУ клетки:
// drawItemCentered. Иначе предмет «жмётся» в угол и выглядит как выход машины.
```

```java
// «привяжу кадры к тикам симуляции» — пробросить в рендер счётчик тиков и:
int frame = tickCount % 2;   // ← анимация по тику, а не по настенным часам
// Дорожка меняла бы кадр 5,5 раза в секунду рывками тика и замирала бы на паузе
// вместе с миром. Кадры дорожки — украшение, миру не принадлежат: им положено
// настенное время (elapsed), которое идёт всегда, пока открыто окно.
```

> Отличие от эталона: у эталонного `ItemRenderer` груз на ленте рисуется НЕ по клетке,
> а по дробной позиции внутри транспортной линии, с интерполяцией по `tickAlpha` —
> отсюда плавное скольжение. Это L6–L7. Сегодня центр клетки, прыжками.

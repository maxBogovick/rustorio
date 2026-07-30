# Эпоха 3 — Интегральные схемы

> Зависит от Эпохи 2 (`Transistor`, `PCB`, `Solder`). Здесь предел в 2 ингредиента на рецепт
> реально мешает — ниже полный код решения (вариант B из обзора), с чётко отмеченной границей:
> всё, кроме `accept()`/`tick()` нового здания, готово для вставки как есть.

## Цепочка

```
Copper Plate --[Press]--> Resistor Pack
Plastic + Copper Cable --[Press]--> Capacitor Pack
Copper Cable --[Press]--> Inductor Pack
Silicon Wafer + Quartz Sand --[Press]--> Crystal Oscillator

PCB(1) + Transistor(4) + Resistor Pack(2) + Solder(1) --[Lithography]--> Logic IC
Logic IC(4) + PCB(1) --[Lithography]--> Memory Module
Logic IC(6) + Inductor Pack(2) --[Lithography]--> ALU Module
```

`Logic IC` — первый рецепт с 4 РАЗНЫМИ ингредиентами. Это физически не влезает в
сегодняшний `Recipe`/`Furnace` (см. [00-overview.md §3](00-overview.md#3-главная-техническая-находка-предел-в-2-ингредиента-на-рецепт)).

## Решение по N-арным рецептам

### Вариант A (без правки движка) — не расписываю кодом

Цепочка «наборов»: `Transistor + Resistor Pack → Logic Core Kit`, `PCB + Solder → Board Kit`,
`Logic Core Kit + Board Kit → Logic IC` — три обычных 2-ингредиентных `Recipe` через
`PRESS`/`ASSEMBLER`, ровно тем же приёмом, что и вся Эпоха 1-2. Если выбираете этот путь —
просто повторите паттерн предыдущих файлов, отдельный код не нужен.

### Вариант B (рекомендую) — полный код ниже

Ядро — обычная механика: карта «предмет → сколько накоплено» вместо `bufferA`/`bufferB`.
**Не готовы только `accept()`/`tick()` нового здания** — помечены `TODO backend` ниже, это
и есть та самая логика, которую нужно продумать самостоятельно (по образцу
`Furnace.accept`/`Furnace.tick`, на которые код ниже прямо ссылается).

#### 1. `Ingredient.java` — новый файл

Путь: `src/main/java/com/rustorio/domain/Ingredient.java`

```java
package com.rustorio.domain;

/** Один именованный слот ингредиента в {@link MultiRecipe} — предмет + сколько единиц нужно за раз. */
public record Ingredient(ItemType item, int count) {
    public Ingredient {
        if (count <= 0) {
            throw new IllegalArgumentException("Ingredient count must be positive: " + count + " of " + item);
        }
    }
}
```

#### 2. `MultiRecipe.java` — новый файл

Путь: `src/main/java/com/rustorio/domain/MultiRecipe.java`

```java
package com.rustorio.domain;

import java.util.List;

/** Рецепт с произвольным (до {@link #MAX_INGREDIENTS}) числом РАЗНЫХ ингредиентов — аналог {@link Recipe} для {@code MultiAssembler}. */
public record MultiRecipe(List<Ingredient> ingredients, ItemType output, int time, BuildingType type) {

    // 5, не 4 — потолок подобран под финальную сборку ПК в Эпохе 4 (Motherboard + CPU + RAM +
    // GPU + Case, ровно 5 разных ингредиентов), самый широкий рецепт всего плана.
    public static final int MAX_INGREDIENTS = 5;

    public MultiRecipe {
        ingredients = List.copyOf(ingredients);
        if (ingredients.isEmpty()) {
            throw new IllegalArgumentException("MultiRecipe needs at least 1 ingredient: " + output);
        }
        if (ingredients.size() > MAX_INGREDIENTS) {
            throw new IllegalArgumentException(
                    "MultiRecipe supports at most " + MAX_INGREDIENTS + " distinct ingredients, got "
                            + ingredients.size() + ": " + output);
        }
        long distinctItems = ingredients.stream().map(Ingredient::item).distinct().count();
        if (distinctItems != ingredients.size()) {
            throw new IllegalArgumentException("MultiRecipe ingredients must all be distinct items: " + output);
        }
    }
}
```

- [ ] Создать оба файла как есть.

#### 3. `RecipeBook.java` — параллельный список `MultiRecipe`

Файл: [`src/main/java/com/rustorio/domain/RecipeBook.java`](../../src/main/java/com/rustorio/domain/RecipeBook.java)

Добавить поле, новый двухаргументный конструктор (старый однохаргументный становится
удобным оверлоадом — существующие вызовы `new RecipeBook(List.of(...))` в тестах не сломаются):

```java
private final List<MultiRecipe> multiRecipes;

public RecipeBook(List<Recipe> recipes, List<MultiRecipe> multiRecipes) {
    this.recipes = List.copyOf(recipes);
    this.multiRecipes = List.copyOf(multiRecipes);
    for (int i = 0; i < this.recipes.size(); i++) {
        Recipe a = this.recipes.get(i);
        for (int j = i + 1; j < this.recipes.size(); j++) {
            Recipe b = this.recipes.get(j);
            if (a.type() == b.type() && sameIngredients(a, b)) {
                throw new IllegalArgumentException(
                        "Two " + a.type() + " recipes share the exact same ingredients: " + a + " and " + b);
            }
        }
    }
    for (int i = 0; i < this.multiRecipes.size(); i++) {
        MultiRecipe a = this.multiRecipes.get(i);
        for (int j = i + 1; j < this.multiRecipes.size(); j++) {
            MultiRecipe b = this.multiRecipes.get(j);
            if (a.type() == b.type() && sameIngredientMultiset(a, b)) {
                throw new IllegalArgumentException(
                        "Two " + a.type() + " multi-recipes share the exact same ingredients: " + a + " and " + b);
            }
        }
    }
}

/** Существующий однохаргументный конструктор — оверлоад без multi-рецептов, для тестов/старых вызовов. */
public RecipeBook(List<Recipe> recipes) {
    this(recipes, List.of());
}

private static boolean sameIngredientMultiset(MultiRecipe a, MultiRecipe b) {
    if (a.ingredients().size() != b.ingredients().size()) {
        return false;
    }
    Map<ItemType, Integer> countsA = new HashMap<>();
    for (Ingredient i : a.ingredients()) {
        countsA.merge(i.item(), i.count(), Integer::sum);
    }
    Map<ItemType, Integer> countsB = new HashMap<>();
    for (Ingredient i : b.ingredients()) {
        countsB.merge(i.item(), i.count(), Integer::sum);
    }
    return countsA.equals(countsB);
}
```

И параллельные методы чтения, рядом с `find`/`findAll`/`forKind`/`findByOutput` (тот же
приём, только по списку `multiRecipes` и по членству в `ingredients()`):

```java
public List<MultiRecipe> allMulti() {
    return multiRecipes;
}

public Optional<MultiRecipe> findMulti(BuildingType kind, ItemType input) {
    return multiRecipes.stream()
            .filter(r -> r.type() == kind && r.ingredients().stream().anyMatch(i -> i.item().equals(input)))
            .findFirst();
}

/** Аналог findAll — несколько MultiRecipe могут делить один и тот же ингредиент. */
public List<MultiRecipe> findAllMulti(BuildingType kind, ItemType input) {
    return multiRecipes.stream()
            .filter(r -> r.type() == kind && r.ingredients().stream().anyMatch(i -> i.item().equals(input)))
            .toList();
}

public List<MultiRecipe> forKindMulti(BuildingType kind) {
    return multiRecipes.stream().filter(r -> r.type() == kind).toList();
}

public Optional<MultiRecipe> findMultiByOutput(BuildingType kind, ItemType output) {
    return multiRecipes.stream().filter(r -> r.type() == kind && r.output().equals(output)).findFirst();
}
```

- [ ] Добавить `import java.util.HashMap;` и `import java.util.Map;` в начало файла, если их там
      ещё нет.
- [ ] Вставить поле, оба конструктора, `sameIngredientMultiset` и пять методов чтения.

#### 4. `BuildingMemento.java` — новый вариант

Файл: [`src/main/java/com/rustorio/domain/building/BuildingMemento.java`](../../src/main/java/com/rustorio/domain/building/BuildingMemento.java)

Добавить внутрь `sealed interface BuildingMemento { ... }` (permits не нужен — все варианты
вложены в тот же файл, компилятор сам расширит разрешённый набор):

```java
/**
 * {@code buffers} — сколько накоплено каждого ингредиента прямо сейчас (детерминированный
 * порядок, тот же приём, что {@link ChestState#contents} — TreeMap, не Map.copyOf).
 */
record MultiAssemblerState(
        BuildingType kind,
        Direction direction,
        Map<ItemType, Integer> buffers,
        int cooldown,
        @Nullable ItemType recipeOutput,
        @Nullable ItemType pendingOutput,
        @Nullable ItemType selectedRecipeOutput) implements BuildingMemento {
    public MultiAssemblerState {
        buffers = Collections.unmodifiableMap(new TreeMap<>(buffers));
    }
}
```

- [ ] Вставить внутрь интерфейса, рядом с `FurnaceState`.

#### 5. `BuildingMementoMixin.java` — новая Jackson-запись

Файл: [`src/main/java/com/rustorio/persistence/BuildingMementoMixin.java`](../../src/main/java/com/rustorio/persistence/BuildingMementoMixin.java)

```java
@JsonSubTypes.Type(value = BuildingMemento.MultiAssemblerState.class, name = "MULTI_ASSEMBLER"),
```

- [ ] Добавить эту строку в массив `@JsonSubTypes` (любое место, порядок не важен).

#### 6. `Building.java` — новый разрешённый тип

Файл: [`src/main/java/com/rustorio/domain/building/Building.java`](../../src/main/java/com/rustorio/domain/building/Building.java)

```java
public sealed interface Building
        permits Miner, Chest, Furnace, Belt, Splitter, Filter, Inserter, SpeedModule, Lab, UndergroundBelt, MultiAssembler {
```

- [ ] Добавить `MultiAssembler` в конец списка `permits` (строка 25).

#### 7. `MultiAssembler.java` — новый файл, ядро

Путь: `src/main/java/com/rustorio/domain/building/MultiAssembler.java`

Всё готово, КРОМЕ `accept()`/`tick()`/`cycleRecipe()` — это прямой аналог
`Furnace.accept`/`Furnace.tick`/`Furnace.cycleRecipe`
([Furnace.java:126-197](../../src/main/java/com/rustorio/domain/building/Furnace.java)),
только по карте `Map<ItemType, Integer>` вместо двух именованных полей `bufferA`/`bufferB`.
Сигнатуры и всё, что вокруг них (конструкторы, `memento()`, `appearance()`, footprint,
поворот) — уже реализовано:

```java
package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Ingredient;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.MultiRecipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaSprites;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Как {@link Furnace}, но принимает до {@link MultiRecipe#MAX_INGREDIENTS} РАЗНЫХ ингредиентов
 * за раз вместо двух ({@code bufferA}/{@code bufferB}) — карта "предмет -> сколько накоплено".
 * {@code accept}/{@code tick}/{@code cycleRecipe} сознательно НЕ реализованы здесь — это тот
 * самый алгоритм, который остаётся продумать (см. docs/pc-roadmap/04-era3-integrated-circuits.md).
 */
public final class MultiAssembler implements Building {

    private final BuildingType kind;
    private final Direction direction;
    private final RecipeBook recipeBook;

    private @Nullable MultiRecipe active;
    private @Nullable MultiRecipe selectedRecipe;
    private final Map<ItemType, Integer> buffers = new HashMap<>();
    private @Nullable ProcessTimer timer;
    private @Nullable ItemType pendingOutput;
    private BuildingStatus status = BuildingStatus.WORKING;

    public MultiAssembler(BuildingType kind, Direction direction, RecipeBook recipeBook) {
        this.kind = kind;
        this.direction = direction;
        this.recipeBook = recipeBook;
    }

    /** Restore-конструктор, вызывается из {@code BuildingFactory.restore} — тот же приём, что {@code Furnace(FurnaceState, RecipeBook)}. */
    MultiAssembler(BuildingMemento.MultiAssemblerState state, RecipeBook recipeBook) {
        this(state.kind(), state.direction(), recipeBook);
        this.buffers.putAll(state.buffers());
        ItemType recipeOutput = state.recipeOutput();
        if (recipeOutput != null) {
            this.active = recipeBook.findMultiByOutput(state.kind(), recipeOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + recipeOutput));
            int cooldown = state.cooldown() > 0 ? state.cooldown() : active.time();
            this.timer = new ProcessTimer(cooldown);
        }
        this.pendingOutput = state.pendingOutput();
        ItemType selectedOutput = state.selectedRecipeOutput();
        if (selectedOutput != null) {
            this.selectedRecipe = recipeBook.findMultiByOutput(state.kind(), selectedOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + selectedOutput));
        }
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        // TODO backend — аналог Furnace.accept (Furnace.java:126-170) + pickRecipe (178-184):
        //  1. active == null: найти MultiRecipe через recipeBook.findAllMulti(kind, item);
        //     неоднозначность (>1 кандидат) — так же, как Furnace: только если selectedRecipe
        //     входит в кандидаты, или ровно один кандидат; иначе отказать (не гадать).
        //  2. active != null: искать Ingredient в active.ingredients(), где ingredient.item()
        //     равен item; отказать, если такого нет или buffers.getOrDefault(item, 0) уже
        //     достиг ingredient.count() (аналог "bufferA < max" у Furnace).
        //  3. buffers.merge(item, 1, Integer::sum); вернуть true.
        throw new UnsupportedOperationException("TODO: реализовать (см. комментарий выше)");
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        // TODO backend — аналог Furnace.tick (Furnace.java:200-244):
        //  1. если pendingOutput != null — попытаться сдать world.offerForward(...), как Furnace.
        //  2. иначе: current = active; если current == null или НЕ ДЛЯ ВСЕХ ingredient in
        //     current.ingredients() выполняется buffers.getOrDefault(ingredient.item(), 0) >=
        //     ingredient.count() — status = NO_INPUT, return (аналог secondInputReady у Furnace,
        //     только по N слотам, а не по одному bufferB).
        //  3. timer.tick(effectiveTime) — если не готово, status = WORKING, return.
        //  4. вычесть ingredient.count() из buffers для каждого ingredient; если все обнулились —
        //     active = null; pendingOutput = current.output(); world.notifyProduced(pendingOutput).
        throw new UnsupportedOperationException("TODO: реализовать (см. комментарий выше)");
    }

    /** Аналог Furnace.cycleRecipe (Furnace.java:193-198), источник кандидатов — recipeBook.forKindMulti(kind). */
    public Optional<ItemType> cycleRecipe() {
        // TODO backend — идентичная механика Furnace.cycleRecipe, просто forKindMulti вместо forKind.
        throw new UnsupportedOperationException("TODO: реализовать (см. комментарий выше)");
    }

    @Override
    public Appearance appearance() {
        int totalBuffered = buffers.values().stream().mapToInt(Integer::intValue).sum();
        ItemType recipeHint = active != null ? active.output() : selectedRecipe != null ? selectedRecipe.output() : null;
        return totalBuffered > 0
                ? Appearance.of(VanillaSprites.LITHOGRAPHY, totalBuffered, status, recipeHint)
                : Appearance.of(VanillaSprites.LITHOGRAPHY, status, recipeHint);
    }

    @Override
    public BuildingType type() {
        return kind;
    }

    @Override
    public BuildingMemento memento() {
        MultiRecipe current = active;
        ProcessTimer currentTimer = timer;
        return new BuildingMemento.MultiAssemblerState(
                kind, direction, buffers,
                currentTimer == null ? 0 : currentTimer.cooldown(),
                current == null ? null : current.output(),
                pendingOutput,
                selectedRecipe == null ? null : selectedRecipe.output());
    }

    @Override
    public int footprintWidth() {
        return kind.footprintWidth();
    }

    @Override
    public int footprintHeight() {
        return kind.footprintHeight();
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(pendingOutput);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        BuildingMemento.MultiAssemblerState state = (BuildingMemento.MultiAssemblerState) memento();
        BuildingMemento.MultiAssemblerState rotated = new BuildingMemento.MultiAssemblerState(
                state.kind(), direction.rotate(), state.buffers(), state.cooldown(),
                state.recipeOutput(), state.pendingOutput(), state.selectedRecipeOutput());
        MultiAssembler turned = new MultiAssembler(rotated, recipeBook);
        turned.status = status;
        return Optional.of(turned);
    }
}
```

- [ ] Создать файл. Реализовать три помеченных `TODO backend` метода — это единственная новая
      логика во всей эпохе.

#### 8. Диспетчеризация и границы пакетов

Файл: [`src/main/java/com/rustorio/domain/building/BuildingFactory.java`](../../src/main/java/com/rustorio/domain/building/BuildingFactory.java)

```java
// в create():
case LITHOGRAPHY -> new MultiAssembler(BuildingType.LITHOGRAPHY, direction, recipeBook);

// в restore(): компилятор сам укажет, что не хватает этой ветки (exhaustive switch по sealed BuildingMemento)
case BuildingMemento.MultiAssemblerState s -> new MultiAssembler(s, recipeBook);

// в clearArrivalMark(): компилятор сам укажет, что не хватает этой ветки (exhaustive switch по sealed Building)
case MultiAssembler ignored -> { }
```

- [ ] Вставить все три строки в соответствующие `switch`. Компилятор физически не даст забыть
      ни одну — соберите проект, он покажет оставшиеся места, если что-то пропущено.

## Новое здание: `LITHOGRAPHY`

Файл: [`src/main/java/com/rustorio/domain/BuildingType.java`](../../src/main/java/com/rustorio/domain/BuildingType.java)

```java
public enum BuildingType {
    MINER("Miner"),
    CHEST("Chest"),
    FURNACE("Furnace"),
    BELT("Belt"),
    SPLITTER("Splitter"),
    PRESS("Press"),
    UNDERGROUND_IN("Tunnel in"),
    UNDERGROUND_OUT("Tunnel out"),
    LAB("Lab"),
    FILTER("Filter"),
    INSERTER("Inserter"),
    ASSEMBLER("Assembler"),
    SILICON_FURNACE("Silicon Furnace"),
    // Appended — 2x2, как ASSEMBLER (см. footprintWidth/Height ниже). Реализация — MultiAssembler.
    LITHOGRAPHY("Lithography");

    private final String label;

    BuildingType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public int footprintWidth() {
        return switch (this) {
            case ASSEMBLER, LITHOGRAPHY -> 2;
            default -> 1;
        };
    }

    public int footprintHeight() {
        return switch (this) {
            case ASSEMBLER, LITHOGRAPHY -> 2;
            default -> 1;
        };
    }
}
```

- [ ] Заменить `footprintWidth`/`footprintHeight` на версию с `switch` выше (было `this ==
      ASSEMBLER ? 2 : 1`), добавить константу `LITHOGRAPHY`.

Файл: [`src/main/java/com/rustorio/domain/building/VanillaBuildings.java`](../../src/main/java/com/rustorio/domain/building/VanillaBuildings.java)

```java
register(prototypes, BuildingType.LITHOGRAPHY, new BuildingCost(VanillaItems.PCB, 20),
        PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.LITHOGRAPHY);
```

- [ ] Заодно обновить число в javadoc `registerAll` (`13` → `14`, если Этап 0/Эпоха 2 уже
      подняли его до 13 добавлением `SILICON_FURNACE`).

Файл: [`src/main/java/com/rustorio/domain/VanillaSprites.java`](../../src/main/java/com/rustorio/domain/VanillaSprites.java)

```java
public static final ContentId LITHOGRAPHY = ContentId.of("rustorio:lithography");
```

Файл: [`src/main/java/com/graphics/render/TextureIndex.java`](../../src/main/java/com/graphics/render/TextureIndex.java)

```java
index.put(VanillaSprites.LITHOGRAPHY, "resources/lithography.png");
```

- [ ] Нужен PNG (`resources/lithography.png`) — быстрый путь без рисования: скопировать
      `resources/assembler.png` как временную заглушку (как и в Эпохе 2 с печью кремния).

## Обновление рендерера книги рецептов (для multi-рецептов)

Без этой правки `Logic IC`/`Memory Module`/`ALU Module` не появятся в книге рецептов (TAB) —
`RecipeBookRenderer` сегодня читает только `recipeBook.all()`.

Файл: [`src/main/java/com/graphics/render/RecipeBookRenderer.java`](../../src/main/java/com/graphics/render/RecipeBookRenderer.java)

```java
void render(RecipeBook recipeBook) {
    int screenW = Gdx.graphics.getWidth();
    int screenH = Gdx.graphics.getHeight();
    int rowCount = recipeBook.all().size() + recipeBook.allMulti().size();
    float panelH = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * rowCount;
    float panelX = (screenW - PANEL_WIDTH) / 2f;
    float panelY = (screenH - panelH) / 2f;
    float firstRowY = panelY + panelH - PADDING - TITLE_HEIGHT;

    shapes.begin(ShapeRenderer.ShapeType.Filled);
    shapes.setColor(Palette.PANEL_BG);
    shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
    float y = firstRowY;
    for (Recipe recipe : recipeBook.all()) {
        shapes.setColor(Palette.itemColor(recipe.input()));
        shapes.circle(panelX + PADDING + ICON_RADIUS, y + 3, ICON_RADIUS, 16);
        y -= ROW_HEIGHT;
    }
    for (MultiRecipe recipe : recipeBook.allMulti()) {
        // Первый ингредиент как представительный цвет строки — этого достаточно для узнавания.
        shapes.setColor(Palette.itemColor(recipe.ingredients().get(0).item()));
        shapes.circle(panelX + PADDING + ICON_RADIUS, y + 3, ICON_RADIUS, 16);
        y -= ROW_HEIGHT;
    }
    shapes.end();

    batch.begin();
    font.setColor(Color.WHITE);
    font.getData().setScale(1.1f);
    font.draw(batch, "Recipe book  (TAB to close)", panelX + PADDING, panelY + panelH - PADDING);

    font.getData().setScale(0.8f);
    font.setColor(Palette.HINT);
    float textX = panelX + PADDING + ICON_RADIUS * 2 + 10;
    y = firstRowY;
    for (Recipe recipe : recipeBook.all()) {
        font.draw(batch, describe(recipe), textX, y);
        y -= ROW_HEIGHT;
    }
    for (MultiRecipe recipe : recipeBook.allMulti()) {
        font.draw(batch, describeMulti(recipe), textX, y);
        y -= ROW_HEIGHT;
    }
    font.getData().setScale(1f);
    font.setColor(Color.WHITE);
    batch.end();
}

/** "1x PCB + 4x Transistor + 2x Resistor Pack + 1x Solder -> Logic IC   (Lithography, 20 ticks)". */
private static String describeMulti(MultiRecipe recipe) {
    StringBuilder inputs = new StringBuilder();
    for (Ingredient ingredient : recipe.ingredients()) {
        if (!inputs.isEmpty()) {
            inputs.append(" + ");
        }
        inputs.append(ingredient.count()).append("x ").append(ingredient.item().label());
    }
    return inputs + "  ->  " + recipe.output().label()
            + "   (" + recipe.type().label() + ", " + recipe.time() + " ticks)";
}
```

- [ ] Заменить `render` целиком (добавлены циклы по `allMulti()`), добавить `describeMulti`,
      добавить `import com.rustorio.domain.Ingredient;` и `import com.rustorio.domain.MultiRecipe;`.

## Новые `ItemType` и обычные (2-ингредиентные) рецепты

`VanillaItems.java` — добавить:

```java
private static final ContentId RESISTOR_PACK_ID = ContentId.of("rustorio:resistor_pack");
private static final ContentId CAPACITOR_PACK_ID = ContentId.of("rustorio:capacitor_pack");
private static final ContentId INDUCTOR_PACK_ID = ContentId.of("rustorio:inductor_pack");
private static final ContentId CRYSTAL_OSCILLATOR_ID = ContentId.of("rustorio:crystal_oscillator");
private static final ContentId LOGIC_IC_ID = ContentId.of("rustorio:logic_ic");
private static final ContentId MEMORY_MODULE_ID = ContentId.of("rustorio:memory_module");
private static final ContentId ALU_MODULE_ID = ContentId.of("rustorio:alu_module");
```

```java
public static final ItemType RESISTOR_PACK = frozen().get(RESISTOR_PACK_ID);
public static final ItemType CAPACITOR_PACK = frozen().get(CAPACITOR_PACK_ID);
public static final ItemType INDUCTOR_PACK = frozen().get(INDUCTOR_PACK_ID);
public static final ItemType CRYSTAL_OSCILLATOR = frozen().get(CRYSTAL_OSCILLATOR_ID);
public static final ItemType LOGIC_IC = frozen().get(LOGIC_IC_ID);
public static final ItemType MEMORY_MODULE = frozen().get(MEMORY_MODULE_ID);
public static final ItemType ALU_MODULE = frozen().get(ALU_MODULE_ID);
```

`registerAll` (число `28` → `35`):

```java
register(items, RESISTOR_PACK_ID, "Resistor Pack", false, rgb(180, 140, 90), ItemShape.SQUARE);
register(items, CAPACITOR_PACK_ID, "Capacitor Pack", false, rgb(90, 140, 180), ItemShape.SQUARE);
register(items, INDUCTOR_PACK_ID, "Inductor Pack", false, rgb(140, 90, 180), ItemShape.SQUARE);
register(items, CRYSTAL_OSCILLATOR_ID, "Crystal Oscillator", true, rgb(230, 230, 240), ItemShape.TRIANGLE);
register(items, LOGIC_IC_ID, "Logic IC", true, rgb(20, 20, 25), ItemShape.TRIANGLE);
register(items, MEMORY_MODULE_ID, "Memory Module", true, rgb(50, 160, 90), ItemShape.TRIANGLE);
register(items, ALU_MODULE_ID, "ALU Module", true, rgb(160, 50, 60), ItemShape.TRIANGLE);
```

`RecipeBook.java` — обычные (2-ингредиентные) рецепты для этих предметов:

```java
private static final Recipe RESISTOR_PACK =
        new Recipe(VanillaItems.COPPER_PLATE, VanillaItems.RESISTOR_PACK, 6, BuildingType.PRESS);
private static final Recipe CAPACITOR_PACK =
        new Recipe(VanillaItems.PLASTIC, VanillaItems.COPPER_CABLE, VanillaItems.CAPACITOR_PACK, 6, BuildingType.PRESS);
private static final Recipe INDUCTOR_PACK =
        new Recipe(VanillaItems.COPPER_CABLE, VanillaItems.INDUCTOR_PACK, 6, BuildingType.PRESS);
private static final Recipe CRYSTAL_OSCILLATOR =
        new Recipe(VanillaItems.SILICON_WAFER, VanillaItems.QUARTZ_SAND, VanillaItems.CRYSTAL_OSCILLATOR, 12, BuildingType.PRESS);
```

Multi-рецепты (см. §3 выше):

```java
private static final MultiRecipe LOGIC_IC = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.PCB, 1), new Ingredient(VanillaItems.TRANSISTOR, 4),
                new Ingredient(VanillaItems.RESISTOR_PACK, 2), new Ingredient(VanillaItems.SOLDER, 1)),
        VanillaItems.LOGIC_IC, 20, BuildingType.LITHOGRAPHY);
private static final MultiRecipe MEMORY_MODULE = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.LOGIC_IC, 4), new Ingredient(VanillaItems.PCB, 1)),
        VanillaItems.MEMORY_MODULE, 25, BuildingType.LITHOGRAPHY);
private static final MultiRecipe ALU_MODULE = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.LOGIC_IC, 6), new Ingredient(VanillaItems.INDUCTOR_PACK, 2)),
        VanillaItems.ALU_MODULE, 30, BuildingType.LITHOGRAPHY);
```

Обновить `STANDARD`, используя новый двухаргументный конструктор:

```java
private static final RecipeBook STANDARD = new RecipeBook(
        List.of(IRON, GEAR, COPPER, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR, CHASSIS_ASSEMBLED,
                COPPER_CABLE, VACUUM_TUBE, SIMPLE_RADIO,
                SILICON_WAFER, TIN_PLATE, LEAD_PLATE, SOLDER, PLASTIC, TEXTOLITE, TRANSISTOR, PCB, LOGIC_BLOCK,
                RESISTOR_PACK, CAPACITOR_PACK, INDUCTOR_PACK, CRYSTAL_OSCILLATOR),
        List.of(LOGIC_IC, MEMORY_MODULE, ALU_MODULE));
```

- [ ] Вставить все блоки выше. Не забыть `import com.rustorio.domain.Ingredient;`/`MultiRecipe`
      в файле `RecipeBook.java`, если они в другом пакете (сейчас всё в `com.rustorio.domain` —
      импорт не нужен).

## Тех-дерево

```java
INTEGRATED_CIRCUITS(700, "Integrated circuits", TRANSISTORS);
```

- [ ] Добавить в конец списка `Tech`, после `TRANSISTORS` (точка с запятой переносится сюда).

## Тесты

- [ ] `VanillaItemsTest` — число `28→35`, семь новых `assertItem`.
- [ ] Новый `MultiRecipeTest`/`IngredientTest` — конструктор отклоняет: 0 ингредиентов, >4
      ингредиентов, дублирующийся `ItemType`, неположительный `count`.
- [ ] Новый `RecipeBookMultiTest` (или расширение `RecipeBookGraphTest`) — `sameIngredientMultiset`
      ловит два рецепта с одним и тем же мультимножеством ингредиентов в разном порядке.
- [ ] Новый `MultiAssemblerTest` — как только `accept`/`tick` реализованы: накопление по
      нескольким слотам, отказ при переполнении конкретного слота, отказ при неизвестном
      предмете, `cycleRecipe` при неоднозначности, персистентность (memento → restore round-trip
      с частично заполненными буферами).
- [ ] `BuildingFactoryTest` — `LITHOGRAPHY` создаётся, footprint 2×2.
- [ ] `JsonSaveRepositoryTest` — сохранить/загрузить мир с `MultiAssembler` посреди сборки.

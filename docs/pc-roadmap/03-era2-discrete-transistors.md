# Эпоха 2 — Дискретные транзисторы

> Зависит от Эпохи 1 (`Copper Cable`) и Этапа 0 (`QUARTZ_SAND`, `TIN_ORE`, `LEAD_ORE`,
> `CRUDE_OIL`). Первое новое **здание** — но это переиспользование существующего `Furnace`,
> тем же приёмом, что уже применён к `ASSEMBLER` — см. [BuildingFactory.java:110](../../src/main/java/com/rustorio/domain/building/BuildingFactory.java).
> Готового кода здесь достаточно, чтобы всё скомпилировалось и работало без единой новой строки
> алгоритма.

## Цепочка

```
Quartz Sand --[Silicon Furnace, без топлива]--> Silicon Wafer
Tin Ore --[Furnace, +уголь]--> Tin Plate  \
                                            --[Press]--> Solder
Lead Ore --[Furnace, +уголь]--> Lead Plate/

Crude Oil --[Press]--> Plastic --[Press, +Copper Cable]--> Textolite

Silicon Wafer + Copper Cable --[Press]--> Transistor
Textolite + Solder --[Press]--> PCB

Transistor + PCB --[Assembler]--> Logic Block (трофей, researchGrade)
```

## 1. Новое здание: `SILICON_FURNACE`

### `BuildingType.java`

Файл: [`src/main/java/com/rustorio/domain/BuildingType.java`](../../src/main/java/com/rustorio/domain/BuildingType.java)

Добавить константу **в конец списка**, после `ASSEMBLER` (тот же приём, что уже применён к
`FILTER`/`INSERTER`/`ASSEMBLER` — appended, not inserted, чтобы клавиши 1-9 первых девяти
зданий не сдвинулись):

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
    // Appended (тот же приём X-01, что и FILTER/INSERTER выше) — мышь-only, 1x1 (footprint по
    // умолчанию). Реализация — тот же Furnace, см. BuildingFactory.create.
    SILICON_FURNACE("Silicon Furnace");

    // ... остальное (label(), footprintWidth(), footprintHeight()) без изменений —
    // SILICON_FURNACE не входит в проверку footprintWidth/Height, значит 1x1 по умолчанию.
```

- [ ] Вставить константу. `footprintWidth`/`footprintHeight` не трогать — дефолт `1` подходит.

### `BuildingFactory.java`

Файл: [`src/main/java/com/rustorio/domain/building/BuildingFactory.java`](../../src/main/java/com/rustorio/domain/building/BuildingFactory.java)

Добавить один `case` в `create()` (строки 84-112), рядом с `ASSEMBLER`:

```java
case ASSEMBLER -> new Furnace(BuildingType.ASSEMBLER, direction, recipeBook);
// Тот же приём (X-03-style reuse): SILICON_FURNACE — Furnace с другим kind, без топлива
// (Furnace.accept проверяет fuel только для kind == FURNACE буквально — см. Furnace.java).
case SILICON_FURNACE -> new Furnace(BuildingType.SILICON_FURNACE, direction, recipeBook);
```

- [ ] Добавить эту одну строку. **`restore()` трогать не нужно** — `BuildingMemento.FurnaceState`
      уже несёт `kind` сквозь память (см. [BuildingFactory.java:131](../../src/main/java/com/rustorio/domain/building/BuildingFactory.java)),
      компилятор ничего не потребует поменять в `restore`/`clearArrivalMark`.

### `VanillaBuildings.java`

Файл: [`src/main/java/com/rustorio/domain/building/VanillaBuildings.java`](../../src/main/java/com/rustorio/domain/building/VanillaBuildings.java)

Добавить в `registerAll` (обновить javadoc-число `12` → `13`):

```java
register(prototypes, BuildingType.SILICON_FURNACE, new BuildingCost(VanillaItems.IRON_PLATE, 10),
        PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.SILICON_FURNACE_COLD);
```

- [ ] Добавить эту строку (по образцу `FURNACE`/`PRESS` — текстура на хотбар-иконку берётся
      «холодная», как и у них).

### `Furnace.java` — отдельный спрайт, а не общий с `FURNACE`

Без этой правки `SILICON_FURNACE` визуально выглядел бы как обычная печь (тот же `if
(kind == BuildingType.ASSEMBLER) {...}` в `appearance()` иначе не подхватит новый `kind`, и
код упадёт в дефолтную ветку `FURNACE_HOT/COLD`). Это единственная содержательная правка во
всей эпохе — не алгоритм, просто ещё одна ветка по образцу уже существующей для `ASSEMBLER`.

Файл: [`src/main/java/com/rustorio/domain/building/Furnace.java`](../../src/main/java/com/rustorio/domain/building/Furnace.java),
метод `appearance()` (строки 349-365):

```java
@Override
public Appearance appearance() {
    ActiveRecipe current = active;
    ItemType recipeHint = current != null ? current.recipe().output()
            : selectedRecipe != null ? selectedRecipe.output() : null;
    if (kind == BuildingType.ASSEMBLER) {
        return Appearance.of(VanillaSprites.ASSEMBLER, bufferA, status, recipeHint);
    }
    if (kind == BuildingType.SILICON_FURNACE) {
        return bufferA > 0
                ? Appearance.of(VanillaSprites.SILICON_FURNACE_HOT, bufferA, status, recipeHint)
                : Appearance.of(VanillaSprites.SILICON_FURNACE_COLD, status, recipeHint);
    }
    return bufferA > 0
            ? Appearance.of(VanillaSprites.FURNACE_HOT, bufferA, status, recipeHint)
            : Appearance.of(VanillaSprites.FURNACE_COLD, status, recipeHint);
}
```

- [ ] Заменить метод целиком на версию выше (добавлена только средняя ветка `if`).

### `VanillaSprites.java` + `TextureIndex.java` + PNG

Файл: [`src/main/java/com/rustorio/domain/VanillaSprites.java`](../../src/main/java/com/rustorio/domain/VanillaSprites.java) — добавить:

```java
public static final ContentId SILICON_FURNACE_HOT = ContentId.of("rustorio:silicon_furnace_hot");
public static final ContentId SILICON_FURNACE_COLD = ContentId.of("rustorio:silicon_furnace_cold");
```

Файл: [`src/main/java/com/graphics/render/TextureIndex.java`](../../src/main/java/com/graphics/render/TextureIndex.java),
в `vanilla()` — добавить:

```java
index.put(VanillaSprites.SILICON_FURNACE_HOT, "resources/silicon_furnace_on.png");
index.put(VanillaSprites.SILICON_FURNACE_COLD, "resources/silicon_furnace_off.png");
```

- [ ] **Нужны сами PNG-файлы**, иначе `Textures.build()` бросит `FileNotFoundException` при
      старте (в отличие от `LAB`, для которого в `Textures.java` есть автозаглушка — здесь такой
      нет, копировать этот механизм не обязательно). Быстрый путь без рисования: скопировать
      `resources/furnace_on.png`/`furnace_off.png` в `resources/silicon_furnace_on.png`/`silicon_furnace_off.png`
      один в один как временную заглушку — игра запустится, здание будет визуально неотличимо от
      обычной печи до тех пор, пока не появится своя художка.

## 2. Решение по топливу (баланс, не код)

`Furnace.accept`/`tick` требуют уголь только когда `kind == BuildingType.FURNACE` буквально —
`SILICON_FURNACE` под этой проверкой **автоматически работает без угля**, никакой правки для
этого не нужно. Это принятый дефолт для MVP. Если позже захочется реализма (кремний тоже жжёт
топливо) — единственное место править: `Furnace.java`, заменить `kind != BuildingType.FURNACE`
на проверку принадлежности множеству «топливных» kind'ов (`FURNACE`, `SILICON_FURNACE`) в обоих
местах (`accept`, `tick`). Не делаю этого сейчас без явного запроса — MVP не требует.

## 3. `VanillaItems.java` — новые предметы

Добавить в блок `ContentId`-полей (после `SIMPLE_RADIO_ID` из Эпохи 1):

```java
private static final ContentId SILICON_WAFER_ID = ContentId.of("rustorio:silicon_wafer");
private static final ContentId TIN_PLATE_ID = ContentId.of("rustorio:tin_plate");
private static final ContentId LEAD_PLATE_ID = ContentId.of("rustorio:lead_plate");
private static final ContentId SOLDER_ID = ContentId.of("rustorio:solder");
private static final ContentId PLASTIC_ID = ContentId.of("rustorio:plastic");
private static final ContentId TEXTOLITE_ID = ContentId.of("rustorio:textolite");
private static final ContentId TRANSISTOR_ID = ContentId.of("rustorio:transistor");
private static final ContentId PCB_ID = ContentId.of("rustorio:pcb");
private static final ContentId LOGIC_BLOCK_ID = ContentId.of("rustorio:logic_block");
```

Публичные константы:

```java
public static final ItemType SILICON_WAFER = frozen().get(SILICON_WAFER_ID);
public static final ItemType TIN_PLATE = frozen().get(TIN_PLATE_ID);
public static final ItemType LEAD_PLATE = frozen().get(LEAD_PLATE_ID);
public static final ItemType SOLDER = frozen().get(SOLDER_ID);
public static final ItemType PLASTIC = frozen().get(PLASTIC_ID);
public static final ItemType TEXTOLITE = frozen().get(TEXTOLITE_ID);
public static final ItemType TRANSISTOR = frozen().get(TRANSISTOR_ID);
public static final ItemType PCB = frozen().get(PCB_ID);
public static final ItemType LOGIC_BLOCK = frozen().get(LOGIC_BLOCK_ID);
```

`registerAll` (число `19` → `28`):

```java
register(items, SILICON_WAFER_ID, "Silicon Wafer", false, rgb(80, 85, 90), ItemShape.SQUARE);
register(items, TIN_PLATE_ID, "Tin Plate", false, rgb(200, 200, 210), ItemShape.SQUARE);
register(items, LEAD_PLATE_ID, "Lead Plate", false, rgb(100, 100, 110), ItemShape.SQUARE);
register(items, SOLDER_ID, "Solder", true, rgb(180, 180, 160), ItemShape.TRIANGLE);
register(items, PLASTIC_ID, "Plastic", false, rgb(60, 130, 120), ItemShape.SQUARE);
register(items, TEXTOLITE_ID, "Textolite", true, rgb(50, 110, 60), ItemShape.TRIANGLE);
register(items, TRANSISTOR_ID, "Transistor", true, rgb(40, 40, 45), ItemShape.TRIANGLE);
register(items, PCB_ID, "PCB", true, rgb(30, 100, 50), ItemShape.TRIANGLE);
register(items, LOGIC_BLOCK_ID, "Logic Block", true, rgb(70, 130, 180), ItemShape.TRIANGLE);
```

- [ ] Вставить все три блока.

## 4. `RecipeBook.java` — новые рецепты

Добавить константы (после рецептов Эпохи 1):

```java
private static final Recipe SILICON_WAFER =
        new Recipe(VanillaItems.QUARTZ_SAND, VanillaItems.SILICON_WAFER, 8, BuildingType.SILICON_FURNACE);
private static final Recipe TIN_PLATE =
        new Recipe(VanillaItems.TIN_ORE, VanillaItems.TIN_PLATE, 5, BuildingType.FURNACE);
private static final Recipe LEAD_PLATE =
        new Recipe(VanillaItems.LEAD_ORE, VanillaItems.LEAD_PLATE, 5, BuildingType.FURNACE);
private static final Recipe SOLDER =
        new Recipe(VanillaItems.TIN_PLATE, VanillaItems.LEAD_PLATE, VanillaItems.SOLDER, 8, BuildingType.PRESS);
private static final Recipe PLASTIC =
        new Recipe(VanillaItems.CRUDE_OIL, VanillaItems.PLASTIC, 6, BuildingType.PRESS);
private static final Recipe TEXTOLITE =
        new Recipe(VanillaItems.PLASTIC, VanillaItems.COPPER_CABLE, VanillaItems.TEXTOLITE, 8, BuildingType.PRESS);
private static final Recipe TRANSISTOR =
        new Recipe(VanillaItems.SILICON_WAFER, VanillaItems.COPPER_CABLE, VanillaItems.TRANSISTOR, 10, BuildingType.PRESS);
private static final Recipe PCB =
        new Recipe(VanillaItems.TEXTOLITE, VanillaItems.SOLDER, VanillaItems.PCB, 10, BuildingType.PRESS);
private static final Recipe LOGIC_BLOCK =
        new Recipe(VanillaItems.TRANSISTOR, VanillaItems.PCB, VanillaItems.LOGIC_BLOCK, 15, BuildingType.ASSEMBLER);
```

И в `STANDARD`:

```java
private static final RecipeBook STANDARD = new RecipeBook(
        List.of(IRON, GEAR, COPPER, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR, CHASSIS_ASSEMBLED,
                COPPER_CABLE, VACUUM_TUBE, SIMPLE_RADIO,
                SILICON_WAFER, TIN_PLATE, LEAD_PLATE, SOLDER, PLASTIC, TEXTOLITE, TRANSISTOR, PCB, LOGIC_BLOCK));
```

- [ ] Вставить девять констант и обновить `STANDARD`.

## 5. `Tech.java` — новая ветка

Файл: [`src/main/java/com/rustorio/domain/Tech.java`](../../src/main/java/com/rustorio/domain/Tech.java)

Добавить в конец списка констант (после `FAST_LAB`, точка с запятой переносится на последнюю
новую константу):

```java
public enum Tech {
    FAST_MINING(80, "Fast mining"),
    FAST_SMELTING(200, "Fast smelting", FAST_MINING),
    BIG_BUFFER(350, "Big buffers", FAST_MINING),
    LONG_TUNNEL(550, "Long tunnels", BIG_BUFFER),
    FAST_LAB(800, "Fast research", FAST_SMELTING, BIG_BUFFER),
    SILICON_PURIFICATION(300, "Silicon purification", FAST_SMELTING),
    TRANSISTORS(500, "Transistors", SILICON_PURIFICATION);
```

- [ ] Вставить два новых constants. Остальной класс (`cost()`, `label()`, `prerequisites()`) не
      трогать.
- [ ] **Важно**: сегодня `TRANSISTOR`-рецепт доступен с самого начала игры (`RecipeBook` не умеет
      блокировать рецепт по неразблокированному `Tech` — это делает `Furnace.accept`, глядя только
      в `RecipeBook`, без обращения к `ResearchView`). Если нужно, чтобы `TRANSISTORS`-тех
      РЕАЛЬНО что-то отпирал (а не был косметическим счётчиком) — это отдельная, не описанная
      здесь задача: научить `Furnace.pickRecipe`/`accept` спрашивать `world.research().isUnlocked(...)`
      для рецептов, помеченных как «требует тех». Сегодня `Tech` влияет только на числа
      (`fasterIfUnlocked`/`biggerIfUnlocked`), не на доступность рецептов — заведено как открытый
      вопрос, не решаю его тут явочным порядком.

## Тесты

- [ ] `VanillaItemsTest` — число `19→28`, девять новых `assertItem`-тестов по образцу Этапа 0/Эпохи 1.
- [ ] `VanillaBuildingsTest` — новая запись `SILICON_FURNACE` (по образцу существующих проверок
      для `ASSEMBLER`/`PRESS`).
- [ ] Новый тест на `SILICON_FURNACE`: без угля работает (в отличие от `FURNACE`), footprint 1×1,
      появляется в `BuildingFactoryTest`.
- [ ] `RecipeBookGraphTest` — прогнать, новые рецепты не создают циклов.

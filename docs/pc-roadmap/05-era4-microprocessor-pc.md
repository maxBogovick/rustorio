# Эпоха 4 — Микропроцессоры и ПК (финал)

> Зависит от Эпохи 3 (`Logic IC`, `Memory Module`, `ALU Module`, `Crystal Oscillator`, а также
> от `LITHOGRAPHY`/`MultiAssembler`, реализованных там). Финальная сборка ПК — 5 разных
> ингредиентов, ровно `MultiRecipe.MAX_INGREDIENTS` — самый широкий рецепт всего плана.
> Никаких новых зданий не заводим — всё через уже существующий `LITHOGRAPHY`.

## Цепочка

```
Gold Ore --[Furnace]--> Gold Plate
Iron Plate --[Press]--> Heatsink/Case
Memory Module + PCB --[Assembler]--> RAM Module        (обычный 2-ингредиентный рецепт)

Logic IC(6) + Gold Plate(1) + Silicon Wafer(2) --[Lithography]--> VLSI Chip
VLSI Chip(1) + PCB(2) + Copper Cable(4) + Solder(2) --[Lithography]--> Motherboard
VLSI Chip(2) + Crystal Oscillator(1) + ALU Module(1) --[Lithography]--> CPU
VLSI Chip(2) + Memory Module(2) + Heatsink/Case(1) --[Lithography]--> GPU

Motherboard(1) + CPU(1) + RAM Module(2) + GPU(1) + Heatsink/Case(1) --[Lithography]--> Personal Computer
                                                                              |
                                                                    notifyProduced(...)
                                                                              |
                                                                    кат-сцена загрузки
```

## `VanillaItems.java` — новые предметы

```java
private static final ContentId GOLD_PLATE_ID = ContentId.of("rustorio:gold_plate");
private static final ContentId HEATSINK_CASE_ID = ContentId.of("rustorio:heatsink_case");
private static final ContentId RAM_MODULE_ID = ContentId.of("rustorio:ram_module");
private static final ContentId VLSI_CHIP_ID = ContentId.of("rustorio:vlsi_chip");
private static final ContentId MOTHERBOARD_ID = ContentId.of("rustorio:motherboard");
private static final ContentId CPU_ID = ContentId.of("rustorio:cpu");
private static final ContentId GPU_ID = ContentId.of("rustorio:gpu");
private static final ContentId PERSONAL_COMPUTER_ID = ContentId.of("rustorio:personal_computer");
```

```java
public static final ItemType GOLD_PLATE = frozen().get(GOLD_PLATE_ID);
public static final ItemType HEATSINK_CASE = frozen().get(HEATSINK_CASE_ID);
public static final ItemType RAM_MODULE = frozen().get(RAM_MODULE_ID);
public static final ItemType VLSI_CHIP = frozen().get(VLSI_CHIP_ID);
public static final ItemType MOTHERBOARD = frozen().get(MOTHERBOARD_ID);
public static final ItemType CPU = frozen().get(CPU_ID);
public static final ItemType GPU = frozen().get(GPU_ID);
public static final ItemType PERSONAL_COMPUTER = frozen().get(PERSONAL_COMPUTER_ID);
```

`registerAll` (число `35` → `43`):

```java
register(items, GOLD_PLATE_ID, "Gold Plate", false, rgb(212, 175, 55), ItemShape.SQUARE);
register(items, HEATSINK_CASE_ID, "Heatsink/Case", false, rgb(140, 145, 150), ItemShape.SQUARE);
register(items, RAM_MODULE_ID, "RAM Module", true, rgb(60, 180, 100), ItemShape.TRIANGLE);
register(items, VLSI_CHIP_ID, "VLSI Chip", true, rgb(15, 15, 20), ItemShape.TRIANGLE);
register(items, MOTHERBOARD_ID, "Motherboard", true, rgb(30, 90, 40), ItemShape.TRIANGLE);
register(items, CPU_ID, "CPU", true, rgb(200, 200, 210), ItemShape.TRIANGLE);
register(items, GPU_ID, "GPU", true, rgb(80, 150, 60), ItemShape.TRIANGLE);
register(items, PERSONAL_COMPUTER_ID, "Personal Computer", true, rgb(220, 220, 225), ItemShape.TRIANGLE);
```

## `RecipeBook.java` — обычные + multi-рецепты

```java
private static final Recipe GOLD_PLATE =
        new Recipe(VanillaItems.GOLD_ORE, VanillaItems.GOLD_PLATE, 6, BuildingType.FURNACE);
private static final Recipe HEATSINK_CASE =
        new Recipe(VanillaItems.IRON_PLATE, VanillaItems.HEATSINK_CASE, 8, BuildingType.PRESS);
private static final Recipe RAM_MODULE =
        new Recipe(VanillaItems.MEMORY_MODULE, VanillaItems.PCB, VanillaItems.RAM_MODULE, 15, BuildingType.ASSEMBLER);

private static final MultiRecipe VLSI_CHIP = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.LOGIC_IC, 6), new Ingredient(VanillaItems.GOLD_PLATE, 1),
                new Ingredient(VanillaItems.SILICON_WAFER, 2)),
        VanillaItems.VLSI_CHIP, 30, BuildingType.LITHOGRAPHY);
private static final MultiRecipe MOTHERBOARD = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.VLSI_CHIP, 1), new Ingredient(VanillaItems.PCB, 2),
                new Ingredient(VanillaItems.COPPER_CABLE, 4), new Ingredient(VanillaItems.SOLDER, 2)),
        VanillaItems.MOTHERBOARD, 35, BuildingType.LITHOGRAPHY);
private static final MultiRecipe CPU = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.VLSI_CHIP, 2), new Ingredient(VanillaItems.CRYSTAL_OSCILLATOR, 1),
                new Ingredient(VanillaItems.ALU_MODULE, 1)),
        VanillaItems.CPU, 40, BuildingType.LITHOGRAPHY);
private static final MultiRecipe GPU = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.VLSI_CHIP, 2), new Ingredient(VanillaItems.MEMORY_MODULE, 2),
                new Ingredient(VanillaItems.HEATSINK_CASE, 1)),
        VanillaItems.GPU, 35, BuildingType.LITHOGRAPHY);
// Пять разных ингредиентов — ровно MultiRecipe.MAX_INGREDIENTS, самый широкий рецепт плана.
private static final MultiRecipe PERSONAL_COMPUTER = new MultiRecipe(
        List.of(new Ingredient(VanillaItems.MOTHERBOARD, 1), new Ingredient(VanillaItems.CPU, 1),
                new Ingredient(VanillaItems.RAM_MODULE, 2), new Ingredient(VanillaItems.GPU, 1),
                new Ingredient(VanillaItems.HEATSINK_CASE, 1)),
        VanillaItems.PERSONAL_COMPUTER, 60, BuildingType.LITHOGRAPHY);
```

Обновить `STANDARD`:

```java
private static final RecipeBook STANDARD = new RecipeBook(
        List.of(IRON, GEAR, COPPER, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR, CHASSIS_ASSEMBLED,
                COPPER_CABLE, VACUUM_TUBE, SIMPLE_RADIO,
                SILICON_WAFER, TIN_PLATE, LEAD_PLATE, SOLDER, PLASTIC, TEXTOLITE, TRANSISTOR, PCB, LOGIC_BLOCK,
                RESISTOR_PACK, CAPACITOR_PACK, INDUCTOR_PACK, CRYSTAL_OSCILLATOR,
                GOLD_PLATE, HEATSINK_CASE, RAM_MODULE),
        List.of(LOGIC_IC, MEMORY_MODULE, ALU_MODULE, VLSI_CHIP, MOTHERBOARD, CPU, GPU, PERSONAL_COMPUTER));
```

- [ ] Вставить все блоки, обновить `STANDARD` (три обычных рецепта дописаны в первый список, пять
      multi-рецептов — во второй).

## Здание

Ничего нового — всё идёт через `FURNACE`/`PRESS`/`ASSEMBLER`/`LITHOGRAPHY` из предыдущих эпох.

## Тех-дерево

```java
MICROPROCESSORS(1200, "Microprocessors", INTEGRATED_CIRCUITS, BIG_BUFFER);
```

- [ ] Добавить в конец списка `Tech`, после `INTEGRATED_CIRCUITS`. Намеренно требует ОБЕ ветки —
      как сегодня `FAST_LAB` требует `FAST_SMELTING` И `BIG_BUFFER`
      ([Tech.java:34-37](../../src/main/java/com/rustorio/domain/Tech.java)) — реальная сборка ПК
      нуждается и в электронике, и в логистике высокого объёма одновременно.

## Финальная кат-сцена — готовая проводка

Файл: [`src/main/java/com/graphics/screen/GameScreen.java`](../../src/main/java/com/graphics/screen/GameScreen.java)

Сегодня там уже есть (строки 43, 85-86):

```java
private final ProductionLog productionLog = new ProductionLog();
...
this.world = new World(GfxConfig.GRID_W, GfxConfig.GRID_H, buildingFactory);
world.addProductionListener(productionLog);
```

`ProductionListener` — `@FunctionalInterface` с единственным методом
`void onProduced(long tick, ItemType item)` ([ProductionListener.java:24](../../src/main/java/com/rustorio/domain/world/ProductionListener.java)),
значит подписка — это одна лямбда, без нового класса:

```java
// Новое поле рядом с productionLog:
private boolean personalComputerBuilt = false;

// Рядом с world.addProductionListener(productionLog):
world.addProductionListener((tick, item) -> {
    if (item == VanillaItems.PERSONAL_COMPUTER && !personalComputerBuilt) {
        personalComputerBuilt = true;
        // TODO: включить показ кат-сцены — здесь чисто дизайн/арт-задача, не логика:
        // например, showBootCutscene = true, а render()/GameScreen дальше сам решает,
        // что рисовать вместо (или поверх) обычного мира, пока флаг включён.
    }
});
```

- [ ] Вставить поле и лямбду в конструктор `GameScreen`, рядом с уже существующей регистрацией
      `productionLog`.
- [ ] **Единственное, что реально нужно придумать** (не код, дизайн): что показывает
      `showBootCutscene` — новый `com.graphics.screen`-класс с анимацией текста, или оверлей
      поверх текущего экрана. Само срабатывание (проверка «это тот самый предмет, и впервые»)
      уже готово выше.
- [ ] Решить: игра продолжается после кат-сцены (песочница) или это экран победы — влияет, нужен
      ли отдельный `GameScreen`-режим «завершено», но это уже развилка внутри самой кат-сцены,
      не в проводке `ProductionListener`.

## Активы

- [ ] Спрайты всех новых предметов — не обязательны для игры (см. [00-overview.md §6](00-overview.md#6-вторая-находка-сколько-на-самом-деле-кода-а-не-данных)):
      цвет+форма из `ItemType` уже достаточно для полностью играбельной цепочки.
- [ ] Арт/текст для самой кат-сцены — отдельная, не инженерная задача.

## Тесты

- [ ] `VanillaItemsTest` — число `35→43`, восемь новых `assertItem`.
- [ ] `RecipeBookGraphTest`/`RecipeBookMultiTest` — новые рецепты, включая `PERSONAL_COMPUTER`
      ровно на потолке `MAX_INGREDIENTS`.
- [ ] Новый тест на кат-сцену: `ProductionListener`-лямбда выше вызывается ровно один раз при
      первом появлении `PERSONAL_COMPUTER`, не срабатывает повторно при следующих (флаг
      `personalComputerBuilt` уже это гарантирует — тест фиксирует поведение, не открывает
      заново вопрос).

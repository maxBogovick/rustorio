# Этап 0 — Фундамент: Bronze→Copper и новое сырьё

> Полностью данные — ни одного места с новым алгоритмом. Копируется почти без раздумий,
> по образцу уже существующих записей в тех же файлах.

## 0.1 `VanillaItems.java` — переименование + новое сырьё

Файл: [`src/main/java/com/rustorio/domain/VanillaItems.java`](../../src/main/java/com/rustorio/domain/VanillaItems.java)

Заменить блок приватных `ContentId`-полей (сейчас строки 29-39) на:

```java
private static final ContentId IRON_ORE_ID = ContentId.of("rustorio:iron_ore");
private static final ContentId IRON_PLATE_ID = ContentId.of("rustorio:iron_plate");
private static final ContentId GEAR_ID = ContentId.of("rustorio:gear");
private static final ContentId COPPER_ORE_ID = ContentId.of("rustorio:copper_ore");
private static final ContentId COPPER_PLATE_ID = ContentId.of("rustorio:copper_plate");
private static final ContentId MECHANISM_ID = ContentId.of("rustorio:mechanism");
private static final ContentId ENGINE_ID = ContentId.of("rustorio:engine");
private static final ContentId CHASSIS_ID = ContentId.of("rustorio:chassis");
private static final ContentId ALLOY_PLATE_ID = ContentId.of("rustorio:alloy_plate");
private static final ContentId ALLOY_GEAR_ID = ContentId.of("rustorio:alloy_gear");
private static final ContentId COAL_ID = ContentId.of("rustorio:coal");
// Этап 0 — новое сырьё для электронной цепочки
private static final ContentId QUARTZ_SAND_ID = ContentId.of("rustorio:quartz_sand");
private static final ContentId TIN_ORE_ID = ContentId.of("rustorio:tin_ore");
private static final ContentId LEAD_ORE_ID = ContentId.of("rustorio:lead_ore");
private static final ContentId CRUDE_OIL_ID = ContentId.of("rustorio:crude_oil");
private static final ContentId GOLD_ORE_ID = ContentId.of("rustorio:gold_ore");
```

Публичные константы (сейчас строки 49-59) — на:

```java
public static final ItemType IRON_ORE = frozen().get(IRON_ORE_ID);
public static final ItemType IRON_PLATE = frozen().get(IRON_PLATE_ID);
public static final ItemType GEAR = frozen().get(GEAR_ID);
public static final ItemType COPPER_ORE = frozen().get(COPPER_ORE_ID);
public static final ItemType COPPER_PLATE = frozen().get(COPPER_PLATE_ID);
public static final ItemType MECHANISM = frozen().get(MECHANISM_ID);
public static final ItemType ENGINE = frozen().get(ENGINE_ID);
public static final ItemType CHASSIS = frozen().get(CHASSIS_ID);
public static final ItemType ALLOY_PLATE = frozen().get(ALLOY_PLATE_ID);
public static final ItemType ALLOY_GEAR = frozen().get(ALLOY_GEAR_ID);
public static final ItemType COAL = frozen().get(COAL_ID);
public static final ItemType QUARTZ_SAND = frozen().get(QUARTZ_SAND_ID);
public static final ItemType TIN_ORE = frozen().get(TIN_ORE_ID);
public static final ItemType LEAD_ORE = frozen().get(LEAD_ORE_ID);
public static final ItemType CRUDE_OIL = frozen().get(CRUDE_OIL_ID);
public static final ItemType GOLD_ORE = frozen().get(GOLD_ORE_ID);
```

`registerAll` (сейчас строки 70-82) — на (обновить и javadoc-число «11» на «16»):

```java
/** Registers all 16 vanilla items into {@code items}. For tests/custom assemblies that want their own isolated (unfrozen) copy instead of sharing {@link #frozen()}. */
public static void registerAll(Registry<ItemType> items) {
    register(items, IRON_ORE_ID, "Iron Ore", false, rgb(105, 100, 95), ItemShape.CIRCLE);
    register(items, IRON_PLATE_ID, "Iron Plate", false, rgb(170, 172, 178), ItemShape.SQUARE);
    register(items, GEAR_ID, "Gear", true, rgb(230, 195, 60), ItemShape.TRIANGLE);
    register(items, COPPER_ORE_ID, "Copper Ore", false, rgb(184, 98, 60), ItemShape.CIRCLE);
    register(items, COPPER_PLATE_ID, "Copper Plate", false, rgb(214, 130, 60), ItemShape.SQUARE);
    register(items, MECHANISM_ID, "Mechanism", true, rgb(163, 68, 40), ItemShape.TRIANGLE);
    register(items, ENGINE_ID, "Engine", true, rgb(90, 170, 90), ItemShape.TRIANGLE);
    register(items, CHASSIS_ID, "Chassis", true, rgb(60, 90, 150), ItemShape.TRIANGLE);
    register(items, ALLOY_PLATE_ID, "Alloy Plate", false, rgb(150, 140, 130), ItemShape.SQUARE);
    register(items, ALLOY_GEAR_ID, "Alloy Gear", true, rgb(190, 170, 90), ItemShape.TRIANGLE);
    register(items, COAL_ID, "Coal", false, rgb(35, 33, 32), ItemShape.CIRCLE);
    register(items, QUARTZ_SAND_ID, "Quartz Sand", false, rgb(194, 178, 128), ItemShape.CIRCLE);
    register(items, TIN_ORE_ID, "Tin Ore", false, rgb(180, 180, 190), ItemShape.CIRCLE);
    register(items, LEAD_ORE_ID, "Lead Ore", false, rgb(90, 90, 100), ItemShape.CIRCLE);
    register(items, CRUDE_OIL_ID, "Crude Oil", false, rgb(25, 20, 18), ItemShape.CIRCLE);
    register(items, GOLD_ORE_ID, "Gold Ore", false, rgb(212, 175, 55), ItemShape.CIRCLE);
}
```

- [ ] Вставить оба блока и `registerAll`, как показано выше.

## 0.2 `RecipeBook.java` — переименование рецепта

Файл: [`src/main/java/com/rustorio/domain/RecipeBook.java`](../../src/main/java/com/rustorio/domain/RecipeBook.java)

- [ ] Строка 27: `BRONZE` → `COPPER`, `BRONZE_ORE`/`BRONZE_PLATE` → `COPPER_ORE`/`COPPER_PLATE`:
  ```java
  private static final Recipe COPPER = new Recipe(VanillaItems.COPPER_ORE, VanillaItems.COPPER_PLATE, 5, BuildingType.FURNACE);
  ```
- [ ] Строка 28: `MECHANISM` — вход меняется на `COPPER_PLATE`:
  ```java
  private static final Recipe MECHANISM = new Recipe(VanillaItems.COPPER_PLATE, VanillaItems.MECHANISM, 8, BuildingType.PRESS);
  ```
- [ ] Строка 33-34: `ALLOY` — второй ингредиент на `COPPER_PLATE`:
  ```java
  private static final Recipe ALLOY =
          new Recipe(VanillaItems.IRON_PLATE, VanillaItems.COPPER_PLATE, VanillaItems.ALLOY_PLATE, 10, BuildingType.FURNACE);
  ```
- [ ] Строка 52-53: список `STANDARD` — заменить `BRONZE` на `COPPER` (порядок не важен, но не менять без причины):
  ```java
  private static final RecipeBook STANDARD = new RecipeBook(
          List.of(IRON, GEAR, COPPER, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR, CHASSIS_ASSEMBLED));
  ```

## 0.3 `PatchOreLayout.java` — новые жилы

Файл: [`src/main/java/com/rustorio/domain/PatchOreLayout.java`](../../src/main/java/com/rustorio/domain/PatchOreLayout.java)

- [ ] Строки 55-56: `BRONZE_ORE` → `COPPER_ORE` (то же имя переменной `PATCHES`, только сам
      `ItemType`, координаты не трогать):
  ```java
  new OrePatch(60, 52, 4, VanillaItems.COPPER_ORE), new OrePatch(84, 42, 3, VanillaItems.COPPER_ORE),
  new OrePatch(33, 56, 3, VanillaItems.COPPER_ORE), new OrePatch(88, 8, 3, VanillaItems.COPPER_ORE),
  ```
- [ ] Добавить новый блок жил СРАЗУ ПОСЛЕ угольных патчей (строка 63), в свежей части карты —
      подобраны так, чтобы не пересекаться ни с одной существующей жилой (все сегодняшние —
      внутри `x<92, y<60`) и ни с одним `TERRAIN_PATCHES` (все — `y≥53`), проверено вручную:
  ```java
  // Этап 0 — новое сырьё для электронной цепочки, свежий участок карты (x>=100), чтобы не
  // пересекаться ни с одной существующей жилой и ни с одним TERRAIN_PATCHES (все y>=53 —
  // здесь всё в пределах y<=45, конфликта нет).
  new OrePatch(110, 10, 4, VanillaItems.QUARTZ_SAND), new OrePatch(140, 30, 3, VanillaItems.QUARTZ_SAND),
  new OrePatch(175, 15, 4, VanillaItems.QUARTZ_SAND), new OrePatch(205, 35, 3, VanillaItems.QUARTZ_SAND),
  new OrePatch(185, 8, 3, VanillaItems.TIN_ORE), new OrePatch(220, 25, 3, VanillaItems.TIN_ORE),
  // Свинец — рядом с оловом (как уголь рядом с железом, D-05): паяльная линия требует, чтобы
  // обе ленты — с олова и со свинца — сходились в одном месте, а не были раскиданы по карте.
  new OrePatch(190, 12, 2, VanillaItems.LEAD_ORE), new OrePatch(225, 30, 2, VanillaItems.LEAD_ORE),
  new OrePatch(240, 10, 3, VanillaItems.CRUDE_OIL), new OrePatch(245, 45, 3, VanillaItems.CRUDE_OIL),
  // Золото — редкое (пометка документа): маленький радиус, всего два месторождения на всю карту.
  new OrePatch(248, 5, 1, VanillaItems.GOLD_ORE), new OrePatch(100, 48, 1, VanillaItems.GOLD_ORE),
  ```
- [ ] Обновить комментарий над `standard()` (строка 150) — было «sixteen patches: eight iron,
      four bronze, four coal», станет `28`: восемь iron, четыре copper, четыре coal, четыре sand,
      два tin, два lead, два oil, два gold.

## 0.4 `RandomOreLayout.java` — та же карта, но случайная

Файл: [`src/main/java/com/rustorio/domain/RandomOreLayout.java`](../../src/main/java/com/rustorio/domain/RandomOreLayout.java)

- [ ] Строка 24: `BRONZE_PATCHES` → `COPPER_PATCHES` (переименование, число то же — 4).
- [ ] Строка 52: `VanillaItems.BRONZE_ORE` → `VanillaItems.COPPER_ORE`.
- [ ] Новые константы рядом с `COAL_RADIUS` (после строки 30):
  ```java
  private static final int SAND_PATCHES = 4;
  private static final int TIN_PATCHES = 2;
  private static final int LEAD_PATCHES = 2;
  private static final int OIL_PATCHES = 2;
  private static final int GOLD_PATCHES = 2;
  private static final int GOLD_RADIUS = 1;
  ```
- [ ] Новый приватный метод (рядом с `fitRadius`) — обобщает уже существующий цикл роллинга
      патчей (сейчас продублирован для iron/copper и для coal), чтобы не копировать его ещё 5 раз:
  ```java
  /** Rolls {@code count} patches of {@code ore} into {@code patches}, starting at {@code offset}; returns the next free offset. */
  private static int rollOre(OrePatch[] patches, int offset, Random random, int count,
          int minRadius, int maxRadius, int width, int height, ItemType ore) {
      for (int i = 0; i < count; i++) {
          int radius = fitRadius(minRadius + random.nextInt(maxRadius - minRadius + 1), width, height);
          int cx = radius + random.nextInt(width - 2 * radius);
          int cy = radius + random.nextInt(height - 2 * radius);
          patches[offset + i] = new OrePatch(cx, cy, radius, ore);
      }
      return offset + count;
  }
  ```
- [ ] В конструкторе, сразу после существующего цикла по `COAL_PATCHES` (после строки 65) —
      увеличить размер массива `patches` и добавить роллы новых ресурсов тем же `random`, чтобы
      «тот же seed — та же карта» осталось верным и для нового сырья:
  ```java
  int newOreCount = SAND_PATCHES + TIN_PATCHES + LEAD_PATCHES + OIL_PATCHES + GOLD_PATCHES;
  // ^ добавить это ПЕРЕД строкой "OrePatch[] patches = new OrePatch[PATCH_COUNT + COAL_PATCHES];"
  // и заменить саму строку на:
  OrePatch[] patches = new OrePatch[PATCH_COUNT + COAL_PATCHES + newOreCount];
  ```
  а после цикла заполнения угля (сразу после `for (int i = 0; i < COAL_PATCHES; i++) { ... }`):
  ```java
  int offset = PATCH_COUNT + COAL_PATCHES;
  offset = rollOre(patches, offset, random, SAND_PATCHES, MIN_RADIUS, MAX_RADIUS, width, height, VanillaItems.QUARTZ_SAND);
  offset = rollOre(patches, offset, random, TIN_PATCHES, MIN_RADIUS, MAX_RADIUS, width, height, VanillaItems.TIN_ORE);
  offset = rollOre(patches, offset, random, LEAD_PATCHES, MIN_RADIUS, MAX_RADIUS, width, height, VanillaItems.LEAD_ORE);
  offset = rollOre(patches, offset, random, OIL_PATCHES, MIN_RADIUS, MAX_RADIUS, width, height, VanillaItems.CRUDE_OIL);
  rollOre(patches, offset, random, GOLD_PATCHES, GOLD_RADIUS, GOLD_RADIUS, width, height, VanillaItems.GOLD_ORE);
  ```

## 0.5 Рендер: цвет руды на земле

Файл: [`src/main/java/com/graphics/render/Palette.java`](../../src/main/java/com/graphics/render/Palette.java)

- [ ] Строка 17: `ORE_BRONZE` → `ORE_COPPER`, цвет чуть теплее/краснее:
  ```java
  static final Color ORE_COPPER = rgb(196, 110, 60);  // медная руда — тёплый рыжий
  ```
- [ ] Новые константы рядом (после `ORE_COAL`):
  ```java
  static final Color ORE_SAND = rgb(200, 185, 140);   // кварцевый песок — светлый песочный
  static final Color ORE_TIN = rgb(170, 170, 180);    // олово — светлый серебристый
  static final Color ORE_LEAD = rgb(80, 80, 90);       // свинец — тёмный сине-серый
  static final Color ORE_OIL = rgb(15, 10, 20);        // нефть — почти чёрный, чуть синее угля
  static final Color ORE_GOLD = rgb(200, 160, 40);     // золото — насыщенный жёлтый
  ```

Файл: [`src/main/java/com/graphics/render/WorldRenderer.java`](../../src/main/java/com/graphics/render/WorldRenderer.java)

- [ ] Заменить `oreColor` (строки 104-112) целиком:
  ```java
  private static Color oreColor(ItemType ore) {
      if (ore == VanillaItems.COPPER_ORE) {
          return Palette.ORE_COPPER;
      }
      if (ore == VanillaItems.COAL) {
          return Palette.ORE_COAL;
      }
      if (ore == VanillaItems.QUARTZ_SAND) {
          return Palette.ORE_SAND;
      }
      if (ore == VanillaItems.TIN_ORE) {
          return Palette.ORE_TIN;
      }
      if (ore == VanillaItems.LEAD_ORE) {
          return Palette.ORE_LEAD;
      }
      if (ore == VanillaItems.CRUDE_OIL) {
          return Palette.ORE_OIL;
      }
      if (ore == VanillaItems.GOLD_ORE) {
          return Palette.ORE_GOLD;
      }
      return Palette.ORE; // IRON_ORE, and any other/modded ore
  }
  ```

Это единственная строчка во всей рендер-подсистеме, которую в принципе нужно трогать под
новое сырьё — см. [00-overview.md §6](00-overview.md#6-вторая-находка-сколько-на-самом-деле-кода-а-не-данных).

## 0.6 Спрайты

- [ ] Переименовать файлы: `resources/bronse_ore.png` → `resources/copper_ore.png`,
      `resources/bronse_plate.png` → `resources/copper_plate.png` (в оригинале опечатка
      «bronse» — заодно и её испраляем).
- [ ] `TextureIndex`/`VanillaSprites` эти два файла не трогают напрямую — предметы не идут через
      атлас (см. `Textures.java`, комментарий про «заготовки 3×4/4×4 пикселя»), только цвет из
      `ItemType.colorRgb()`. Новые PNG для новых предметов физически не обязательны для игры —
      сойдёт цвет+форма (кружок/квадрат/треугольник), но при желании добавить арт для Steam-стиля
      «настоящих» текстур — они пойдут в `resources/` и будут упакованы, только когда появится
      здание нового `BuildingType` (см. Эпоху 2), не раньше.

## 0.7 Тесты

Файл: [`src/test/java/com/rustorio/domain/VanillaItemsTest.java`](../../src/test/java/com/rustorio/domain/VanillaItemsTest.java)

- [ ] Строка 23-24: число `11` → `16`.
- [ ] Строки 49-56 (`bronzeOreMatchesCurrentPaletteValues`/`bronzePlateMatchesCurrentPaletteValues`)
      — переименовать и обновить:
  ```java
  @Test
  void copperOreMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.COPPER_ORE, "copper_ore", "Copper Ore", false, 184, 98, 60, ItemShape.CIRCLE);
  }

  @Test
  void copperPlateMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.COPPER_PLATE, "copper_plate", "Copper Plate", false, 214, 130, 60, ItemShape.SQUARE);
  }
  ```
- [ ] Добавить пять новых тестов тем же приёмом:
  ```java
  @Test
  void quartzSandMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.QUARTZ_SAND, "quartz_sand", "Quartz Sand", false, 194, 178, 128, ItemShape.CIRCLE);
  }

  @Test
  void tinOreMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.TIN_ORE, "tin_ore", "Tin Ore", false, 180, 180, 190, ItemShape.CIRCLE);
  }

  @Test
  void leadOreMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.LEAD_ORE, "lead_ore", "Lead Ore", false, 90, 90, 100, ItemShape.CIRCLE);
  }

  @Test
  void crudeOilMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.CRUDE_OIL, "crude_oil", "Crude Oil", false, 25, 20, 18, ItemShape.CIRCLE);
  }

  @Test
  void goldOreMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.GOLD_ORE, "gold_ore", "Gold Ore", false, 212, 175, 55, ItemShape.CIRCLE);
  }
  ```

Остальные файлы из грепа по `BRONZE` (см. предыдущую версию этого файла) — везде та же механика
переименования, без новой логики:

- [ ] `RecipeBookGraphTest.java`, `ChestTest.java`, `FilterTest.java`, `FurnaceTest.java`,
      `LabTest.java`, `ProductionLogTest.java`, `DevSceneTest.java`, `HudRenderer.java`,
      `DevScene.java`, `architecture/SourceCodeScanner.java`, `BuildingFactory.java` (только
      комментарий на строке ~97-101) — заменить `BRONZE`/`Bronze`/`bronze` на
      `COPPER`/`Copper`/`copper` по месту; ни один из них не содержит алгоритма, который бы
      менялся от переименования.
- [ ] `PatchOreLayoutTest.java`, `RandomOreLayoutTest.java` — добавить проверки на новые жилы
      (непересечение друг с другом и с `TERRAIN_PATCHES`, детерминизм `RandomOreLayout` для
      одного seed) — по образцу существующих проверок для угля.

## 0.8 Решение: расширять ли `Recipe` до N ингредиентов

Не блокирует Этап 0 — нужно принять до старта Эпохи 3. Полный разбор с готовым кодом обоих
вариантов — в [04-era3-integrated-circuits.md](04-era3-integrated-circuits.md#решение-по-n-арным-рецептам).

## 0.9 Мелкое сопутствующее

- [ ] Поправить `README.md`/`Cargo.toml` (описывают устаревший Rust/macroquad прототип) —
      низкий приоритет, не блокирует остальной план.

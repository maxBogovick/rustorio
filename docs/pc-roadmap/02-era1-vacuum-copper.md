# Эпоha 1 — Вакуум и медь

> Зависит от [01-phase0-foundation.md](01-phase0-foundation.md) (нужен `COPPER_PLATE`).
> Полностью данные — два новых предмета и трофей, три новых рецепта, все в пределах
> 2 ингредиентов. Ноль правок в рендере/хэндлере (см. [00-overview.md §6](00-overview.md#6-вторая-находка-сколько-на-самом-деле-кода-а-не-данных)).
>
> Поправка к первому черновику: «Vacuum Tube × N → трофей» из первой версии этого файла не
> подходит — сегодняшний `Recipe`/`Furnace` не умеет «N единиц одного и того же входа на один
> выход» (буфер лишь копит ДО 5 штук для непрерывной работы конвейера, а рецепт срабатывает от
> ровно 1 единицы каждого входа за раз). Трофей ниже — обычный рецепт на 2 разных ингредиента,
> без изменений движка.

## Цепочка

```
Copper Ore --[Furnace]--> Copper Plate --[Press]--> Copper Cable
                                                            \
                                          (+ Iron Plate) --[Press]--> Vacuum Tube
                                                                            \
                                                    (+ Copper Cable) --[Assembler]--> Simple Radio (трофей)
```

## `VanillaItems.java` — новые предметы

Файл: [`src/main/java/com/rustorio/domain/VanillaItems.java`](../../src/main/java/com/rustorio/domain/VanillaItems.java)

Добавить в блок `ContentId`-полей (после `GOLD_ORE_ID` из Этапа 0):

```java
private static final ContentId COPPER_CABLE_ID = ContentId.of("rustorio:copper_cable");
private static final ContentId VACUUM_TUBE_ID = ContentId.of("rustorio:vacuum_tube");
private static final ContentId SIMPLE_RADIO_ID = ContentId.of("rustorio:simple_radio");
```

Публичные константы:

```java
public static final ItemType COPPER_CABLE = frozen().get(COPPER_CABLE_ID);
public static final ItemType VACUUM_TUBE = frozen().get(VACUUM_TUBE_ID);
public static final ItemType SIMPLE_RADIO = frozen().get(SIMPLE_RADIO_ID);
```

`registerAll` — добавить (обновить javadoc-число `16` → `19`):

```java
register(items, COPPER_CABLE_ID, "Copper Cable", false, rgb(224, 146, 82), ItemShape.SQUARE);
register(items, VACUUM_TUBE_ID, "Vacuum Tube", false, rgb(200, 210, 215), ItemShape.TRIANGLE);
register(items, SIMPLE_RADIO_ID, "Simple Radio", true, rgb(120, 100, 70), ItemShape.TRIANGLE);
```

(форма: `SQUARE` для кабеля — он получается прокаткой, как плита; `TRIANGLE` для собранных
деталей — тот же язык, что уже использует `GEAR`/`ENGINE`/`MECHANISM`.)

- [ ] Вставить оба блока констант и три строки `register`.

## `RecipeBook.java` — новые рецепты

Файл: [`src/main/java/com/rustorio/domain/RecipeBook.java`](../../src/main/java/com/rustorio/domain/RecipeBook.java)

Добавить рядом с остальными приватными константами-рецептами (после `CHASSIS_ASSEMBLED`):

```java
private static final Recipe COPPER_CABLE =
        new Recipe(VanillaItems.COPPER_PLATE, VanillaItems.COPPER_CABLE, 4, BuildingType.PRESS);
private static final Recipe VACUUM_TUBE =
        new Recipe(VanillaItems.COPPER_CABLE, VanillaItems.IRON_PLATE, VanillaItems.VACUUM_TUBE, 6, BuildingType.PRESS);
private static final Recipe SIMPLE_RADIO =
        new Recipe(VanillaItems.VACUUM_TUBE, VanillaItems.COPPER_CABLE, VanillaItems.SIMPLE_RADIO, 20, BuildingType.ASSEMBLER);
```

И дописать в список `STANDARD`:

```java
private static final RecipeBook STANDARD = new RecipeBook(
        List.of(IRON, GEAR, COPPER, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR, CHASSIS_ASSEMBLED,
                COPPER_CABLE, VACUUM_TUBE, SIMPLE_RADIO));
```

- [ ] Вставить три константы и обновить `STANDARD`.

## Здания

Ничего нового — весь путь идёт через существующие `MINER`/`FURNACE`/`PRESS`/`ASSEMBLER`.

## Тесты

Файл: [`src/test/java/com/rustorio/domain/VanillaItemsTest.java`](../../src/test/java/com/rustorio/domain/VanillaItemsTest.java)

- [ ] Число предметов `16` → `19`.
- [ ] Три новых теста тем же приёмом, что и в Этапе 0:
  ```java
  @Test
  void copperCableMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.COPPER_CABLE, "copper_cable", "Copper Cable", false, 224, 146, 82, ItemShape.SQUARE);
  }

  @Test
  void vacuumTubeMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.VACUUM_TUBE, "vacuum_tube", "Vacuum Tube", false, 200, 210, 215, ItemShape.TRIANGLE);
  }

  @Test
  void simpleRadioMatchesCurrentPaletteValues() {
      assertItem(VanillaItems.SIMPLE_RADIO, "simple_radio", "Simple Radio", true, 120, 100, 70, ItemShape.TRIANGLE);
  }
  ```

Файл: `src/test/java/com/rustorio/domain/RecipeBookGraphTest.java` — не читал его подробно, но
по описанию в [ARCHITECTURE_REVIEW.md](../../ARCHITECTURE_REVIEW.md) он гоняет `RecipeBook`
целиком (циклы, `depthOf`) — новые рецепты пройдут теми же проверками автоматически, отдельных
правок скорее всего не требуется, только прогнать и убедиться, что `depthOf(SIMPLE_RADIO)`
(`20 + depthOf(VACUUM_TUBE) + depthOf(COPPER_CABLE)`) не бросает исключений.

## Тех-дерево

Эпоха 1 доступна с самого начала игры, без нового узла `Tech` — как и сегодняшний
Iron→Gear путь. Первый новый узел (`SILICON_PURIFICATION`) появляется в Эпохе 2.

## Что действительно нужно решить (не код)

- Баланс времени (`4`/`6`/`20` тиков выше) и цвета (`rgb(...)`) — расставлены по аналогии с
  существующими рецептами/предметами, чтобы код компилировался и играбельно смотрелся с первой
  попытки. Правьте свободно под собственное чувство баланса — в этих числах нет скрытой логики.

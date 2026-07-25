# Полное архитектурное ревью — Rustorio (ветка `temp`)

**Дата:** 2026-07-24
**Охват:** весь `src/main/java` (домен, persistence, graphics) — 65 файлов, ~4465 строк.
**Тесты сознательно не рассматривались** — по прямой просьбе; весь `src/test` вне этого отчёта.
**Метод:** построчное чтение каждого production-файла (не выборка), без правок кода — это отчёт,
не патч. Все находки ниже подтверждены конкретной цитатой (файл:строка).

Это ревью — отдельный, более широкий заход, чем «раунд 2» (см. `PROGRESS.md`, задачи #22–#29,
уже исправлены). Там разбирались точечные баги; здесь — общая архитектурная картина: каких
СОВРЕМЕННЫХ практик не хватает, где нужны паттерны, что стоит переделать целиком.

---

## TL;DR

Кодовая база для учебного проекта необычно современная: sealed-иерархии с исчерпывающими
`switch`-выражениями, record'ы везде, где нужны неизменяемые данные, JSpecify-аннотации,
явное DI через конструкторы, честные javadoc-признания компромиссов. Это не типичный «студенческий»
код — большинство паттернов GoF применены осмысленно и по делу, не «для галочки».

Но есть системная проблема: **проект декларирует свои же принципы, а потом сам их нарушает** —
в 3 разных местах используется `Optional` как тип поля/компонента record'а, хотя именно этому
посвящён отдельный абзац javadoc в `Recipe.java`, объясняющий, почему так делать нельзя (Effective
Java Item 55). Это главный сигнал для отчёта: не отсутствие знания, а несистематическое
применение.

Второй системный вопрос — **инкапсуляция протекает на границе `World`**: `stats()`/`research()`
отдают наружу объекты с публичными разрушающими методами (`restore()`, `clear()`, `addPoints()`) —
любой код с ссылкой на `World` может обнулить статистику или накрутить очки исследований напрямую,
в обход `Lab`.

Третий — **`InputHandler` (322 строки) — единственный настоящий God Class** в проекте: 8+
обязанностей в одном классе, ни одной таблицы биндингов, копипаста между обработкой драга ЛКМ/ПКМ.

Ниже — всё по порядку, с доказательствами.

---

## 1. Что уже сделано на современном уровне

Прежде чем критиковать — контрольный список того, что здесь СИЛЬНЫЕ стороны, чтобы находки ниже
не читались как «код плохой». Это фундамент, на который дальнейшие правки должны опираться, а не
конкурировать с ним.

| Практика | Где | Оценка |
|---|---|---|
| Java 25 LTS toolchain | `build.gradle:12-17` | Новейшая LTS, осознанный выбор с задокументированной причиной апгрейда LWJGL |
| `sealed` + исчерпывающий pattern-matching `switch` | `Building.java:25-26`, `BuildingMemento.java:24`, `SaveResult.java:11` (новый, раунд 2), `BuildingFactory.restore/create` | Ровно то, для чего эта фича Java 21+ существует — компилятор ловит забытый case |
| `record` для DTO/снимков/мементо | `BuildingMemento.*`, `ProductionStats.Snapshot`, `Research.Snapshot`, `TilePos`, `TileRange`, `PlacedBuilding`, `WorldSnapshot` | Идиоматично, без единой лишней сеттер-обёртки |
| Pattern matching `instanceof` с guard-условиями | `Building.unwrap` (104), `World.removeBuilding` (209), `OverlayRenderer` (75-77) | Java 16+ идиома вместо `instanceof` + explicit cast |
| Явный DI через конструктор (без фреймворка) | `BuildingFactory`, `World(int,int,BuildingFactory)`, `Renderer`, все рендер-слои | **Осознанно достаточно** для этого масштаба — Spring/Guice/Dagger были бы избыточны |
| JSpecify `@NullMarked`/`@Nullable` | на КАЖДОМ `package-info.java` | Контракт null-безопасности задокументирован системно, не выборочно |
| Strategy как функциональные интерфейсы | `OreLayout`, `SortRule`, `ProductionListener` | Минимально, без ритуала классов-обёрток там, где хватает одного метода |
| Честная документация компромиссов | `Building.java:19-23` («decorator tax» sealed vs Decorator), `Splitter.java` (SortRule не переживает save/load), `BeltSegment.java:16-20` (O(n) вместо O(1) — осознанно, до замера) | Редкое качество — не только ЧТО сделано, но и ПОЧЕМУ, и какова цена |
| Text blocks, `var`, enhanced switch | `Main.java` (text block), `BeltSegment.java:96` (`var it = ...`) | По месту, не показное |
| Разделение домена и вида (`Sprite` ≠ `BuildingType`) | `Appearance`, `Sprite.java`, `Textures.forSprite` | Здание описывает СЕБЯ («я похож на горячую печь»), а не рендер разбирает здание через `instanceof`/`switch` |
| Границы пакетов задокументированы и (предположительно) проверяются ArchUnit | все `package-info.java` + `archunit` в `build.gradle:37-38` | Архитектурные инварианты — не просто комментарий, а зависимость собрана под тест |

---

## 2. Находки

### 2.1 Главная находка: `Optional` как поле — принцип нарушен в 3 местах из тех же рук, что его сформулировали

`Recipe.java` содержит образцовое обоснование:

```java
// src/main/java/com/rustorio/domain/Recipe.java:12-16
 * <p>{@code input2} is deliberately a plain, possibly-{@code null} field rather than an {@code
 * Optional<Item>} component: Effective Java (Item 55) is explicit that {@code Optional} should
 * never be a field or record-component type, only a method return type. {@link #secondInput()}
 * is the Optional-returning view for callers who want it; {@link #input2()} (raw, nullable) stays
 * for the tight hot-path comparisons inside {@code Furnace}.
```

Раунд 2 этого же ревью специально чинил ровно это в `Belt.attachToNeighbors` (было
`Optional<Belt>` параметром). Но в текущем коде принцип нарушен ещё в трёх местах, которые
раунд 2 не разбирал:

**(а) `RemoveAction` — `Optional` как поле класса:**
```java
// src/main/java/com/rustorio/domain/action/RemoveAction.java:14
private Optional<Building> removed = Optional.empty();
```

**(б) `UpgradeSpeedAction` — то же самое:**
```java
// src/main/java/com/rustorio/domain/action/UpgradeSpeedAction.java:19
private Optional<Building> previous = Optional.empty();
```

**(в) `Appearance` — `OptionalInt` как компонент record'а:**
```java
// src/main/java/com/rustorio/domain/Appearance.java:10
public record Appearance(Sprite sprite, OptionalInt badge) {
```

**Почему это важно.** Не только стиль: `Optional`-поле почти всегда занимает больше памяти,
не сериализуется Jackson'ом «из коробки» (см. обоснование в `BuildingMemento.java:18-19` — именно
поэтому мементо используют `@Nullable`, а не `Optional`), и обманывает читателя — кажется, что
проверка на «есть/нет» защищена типом, хотя `Optional`, будучи полем, ничего не мешает присвоить
`null` вместо `Optional.empty()`. Для `PlayerAction`-иерархии это к тому же ЕДИНСТВЕННОЕ
несоответствие стилю: `PlaceAction`/`CompositeAction` вообще не хранят состояние такого рода.

**Что сделать.** В `RemoveAction`/`UpgradeSpeedAction` — заменить `Optional<Building> removed`
на `private @Nullable Building removed`, с `removed != null` вместо `removed.isPresent()`. В
`Appearance` — заменить `OptionalInt badge` на `int badge` с сентинел-значением (например,
`-1` = «нет бейджа») ИЛИ (чище, раз record не обязан свою внутреннюю форму синхронизировать с
Jackson, т.к. `Appearance` никогда не сериализуется) оставить два статических фабричных метода,
как уже есть, но заменить представление на sealed-пару `NoBadge`/`WithBadge(int)` — не обязательно,
но соответствовало бы остальному стилю проекта (sealed вместо Optional-в-данных).

---

### 2.2 Инкапсуляция протекает: `World.stats()`/`research()` отдают объекты с публичными «деструктивными» методами

```java
// src/main/java/com/rustorio/domain/world/World.java:308-314
public ProductionStats stats() {
    return stats;
}

public Research research() {
    return research;
}
```

Оба возвращаемых типа — не read-only «снимок», а живой мутируемый объект с ПУБЛИЧНЫМИ методами,
которые могут стереть или подделать прогресс:

```java
// src/main/java/com/rustorio/domain/Research.java:39-53
public void addPoints(int amount) { ... }   // публично, без ограничений
...
public void clear() { ... }                  // публично — сбрасывает исследования
...
// src/main/java/com/rustorio/domain/Research.java:66-70
public void restore(Snapshot snapshot) { ... } // публично — подменяет весь прогресс

// src/main/java/com/rustorio/domain/world/ProductionStats.java:45-48
public void restore(Snapshot snapshot) {     // тоже публично
    clear();
    totals.putAll(snapshot.totals());
}
```

Любой код, у которого есть ссылка на `World` — рендер, будущий плагин, HUD, да хоть тестовый
хелпер по ошибке — может написать `world.research().addPoints(9999)` или
`world.stats().restore(new ProductionStats.Snapshot(Map.of()))` в обход `Lab`/производственного
цикла. Сейчас этим никто не пользуется (в проверенном коде — не пользуется), но абстракция это
не запрещает, а должна.

Показательно, что `ProductionStats.clear()` (без модификатора, package-private,
`ProductionStats.java:30`) СДЕЛАН правильно — доступен только внутри `domain.world`, где живёт
`World`. А вот `restore()` в том же классе — public. Т.е. в одном файле есть и правильный, и
неправильный пример инкапсуляции одного и того же по духу метода.

**Что сделать.** Один из двух путей:
1. Сузить видимость: `Research`/`ProductionStats` держат мутаторы package-private (как уже
   сделано для `ProductionStats.clear()`), а мутация из `domain.building` (`Lab.tick()` вызывает
   `world.research().addPoints(1)` — другой пакет!) идёт не напрямую в `Research`, а через новый
   метод самого `World`, например `World.addResearchPoints(int)` — тогда `research()`/`stats()`
   можно оставить возвращающими сами объекты, но их мутаторы сузить до пакета `domain`/`domain.world`.
2. Либо завести read-only «вид»: `ResearchView`/`ProductionStatsView` — интерфейсы с одними
   геттерами, `research()`/`stats()` возвращают их, а `Research`/`ProductionStats` (полный
   мутируемый тип) видны только `World` и `JsonSaveRepository` (persistence уже и так единственный,
   кто трогает `restore()`, кроме потенциальных внешних вызовов).

Первый вариант меньше по объёму правок и не плодит новые интерфейсы — вероятно, предпочтительнее.

---

### 2.3 `InputHandler` — единственный настоящий God Class в проекте

322 строки, один класс, восемь обязанностей: камера (пан/зум), выбор постройки клавишами,
клик по хотбару, драг-постройка ЛКМ, драг-снос ПКМ, undo/redo, сохранение/загрузка, пауза/скорость,
переключатель книги рецептов. Все — в одном методе `handle()` (58 строк, `InputHandler.java:117-174`)
как последовательность `if (Gdx.input.isKeyJustPressed(...))`.

```java
// src/main/java/com/graphics/input/InputHandler.java:117-174 (сокращено)
public void handle(World world, float delta) {
    handleCamera(delta);
    handleBuildSelection();
    handleHotbarClick();
    handleBuildDrag(world);
    handleRemoveDrag(world);
    if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { facing = facing.rotate(); }
    if (Gdx.input.isKeyJustPressed(Input.Keys.U)) { ... history.perform(...) ... }
    if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) { history.undo(world); }
    if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) { history.redo(world); }
    if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) { ... saveRepository.save(world) ... }
    if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) { ... saveRepository.load(world) ... }
    if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) { showRecipeBook = !showRecipeBook; }
    if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) { paused = !paused; }
    if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) { ... }
    if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) { ... }
}
```

Каждая новая клавиша — это ещё один `if` в теле уже немаленького метода: чистое нарушение
Open/Closed (не расширить поведение, не трогая существующий код).

**Дублирование.** `handleBuildDrag` (210-239) и `handleRemoveDrag` (242-271) — почти буквально
одна и та же логика (собрать тайлы под зажатой кнопкой, на отпускание — собрать в один
`CompositeAction`), продублированная под разные кнопки мыши и разные типы `PlayerAction`:

```java
// handleBuildDrag, 210-239                     // handleRemoveDrag, 242-271 — тот же скелет
boolean pressed = ...LEFT...                    boolean pressed = ...RIGHT...
if (isButtonJustPressed) blockedByHotbar = ...   if (isButtonJustPressed) blockedByHotbar = ...
if (pressed && blocked) return;                  if (pressed && blocked) return;
if (pressed) { ...collect tile... return; }      if (pressed) { ...collect tile... return; }
if (!dragging) return;                           if (!dragging) return;
... build CompositeAction, history.perform ...   ... build CompositeAction, history.perform ...
```

**Скрытая зависимость вместо инъекции.** В отличие от `World`/`BuildingFactory`/`Renderer`,
которые везде получают зависимости через конструктор, `InputHandler` создаёт `SaveRepository`
сам:

```java
// src/main/java/com/graphics/input/InputHandler.java:49
private final SaveRepository saveRepository = new JsonSaveRepository();
```

Это ломает ту же самую идею, ради которой раунд 2 добавил `RandomOreLayout` как второй
`OreLayout` (задача #26 — «DI-возможности объявлены, но не используются») — только на этот раз
дело не в том, что нет второй реализации `SaveRepository`, а в том, что даже ОДНА существующая
реализация не может быть подменена без правки исходника `InputHandler` (например, для теста, для
облачного сохранения, для второго слота сохранений).

**Что сделать.**
1. `InputHandler(GameCamera camera, SaveRepository saveRepository)` — принять зависимость снаружи
   (её создаёт `GameScreen`, который и так уже владеет DI-графом игры).
2. Вынести повторяющуюся логику драга в один параметризованный хелпер — например
   `DragCollector`, принимающий кнопку мыши и функцию `TilePos -> PlayerAction`, используемый
   дважды с разными аргументами вместо двух почти одинаковых 30-строчных методов.
3. Разбить `InputHandler` на маленькие, каждый со своей ответственностью
   (`CameraInput`, `BuildSelectionInput`, `HistoryInput`/undo-redo, `SaveLoadInput`), с тонким
   координатором, который просто вызывает их по очереди — то же разделение, что уже применено к
   рендеру (`Renderer` → `WorldRenderer`/`BuildingRenderer`/`ItemRenderer`/`OverlayRenderer`/
   `HudRenderer`). Модель для подражания уже есть в этом же кодбейзе, просто не применена к вводу.
4. (Опционально, дальше) — таблица привязок вместо цепочки `if`: список
   `record KeyBinding(int key, Runnable action)` и один цикл `for (var b : bindings) if
   (Gdx.input.isKeyJustPressed(b.key())) b.action().run();` — тогда новая клавиша — это одна
   строка в списке, а не новый `if` в разросшемся методе.

---

### 2.4 Примитивная одержимость: неявные конечные автоматы через nullable-поля

`Furnace` реализует состояния «простаивает / знает рецепт, копит буфер / готовит / ждёт
доставки готового» через КОМБИНАЦИЮ трёх nullable-полей одновременно:

```java
// src/main/java/com/rustorio/domain/building/Furnace.java:34-38
private Recipe recipe;        // null = "рецепт ещё не выбран"
private int bufferA;
private int bufferB;
private ProcessTimer timer;   // null = синхронизировано с recipe == null, но НЕ гарантировано типом
private Item pendingOutput;   // null = "нечего доставлять"
```

Валидность связей между этими полями (например, «`timer != null` тогда и только тогда, когда
`recipe != null`») существует только как договорённость в голове автора и в комментариях — ничто
в системе типов не мешает создать `Furnace`, где `recipe == null`, а `timer != null` (собственно,
конструктор восстановления, `Furnace.java:52-63`, вручную поддерживает этот инвариант руками,
проверяя `recipeOutput != null` перед тем, как одновременно выставить и `recipe`, и `timer`).

**Почему это важно.** «Illegal states unrepresentable» (сделать нелегальные состояния
невозможными на уровне типов) — одна из самых востребованных практик в современной Java именно
благодаря sealed-иерархиям, которые в этом же проекте отлично применены к `Building`/
`BuildingMemento`/`SaveResult`. Здесь та же техника напрашивается, но не применена: три поля,
которые обязаны изменяться синхронно, вместо одного поля sealed-типа с состояниями `Idle`,
`Accumulating(Recipe, bufferA, bufferB, timer)`, `AwaitingDelivery(Item pendingOutput, ...)`.

`Miner` — та же идея, но безопаснее по масштабу (всего 2 неявных состояния: `held == null`
означает «копит», иначе «ждёт доставки», `Miner.java:31-32,45-62`) — здесь риск ниже, но паттерн
тот же.

**Что сделать.** Не обязательно горящая задача (текущий код работает и покрыт тестами), но
кандидат на модернизацию: описать состояние `Furnace` одним полем sealed-типа вместо трёх
независимых nullable-полей. Само по себе это не то же самое, что `BuildingMemento.FurnaceState`
(тот — снимок для persistence; здесь речь про внутреннее, живое представление состояния во время
игры) — их не следует путать/объединять, но подход («sealed вместо комбинации nullable-полей»)
можно позаимствовать у уже написанного `BuildingMemento`.

---

### 2.5 Задекларированные, но не проверяемые практики

**(а) `@NullMarked`/`@Nullable` — контракт есть, инструмента для его проверки нет.**
Каждый `package-info.java` помечен `@NullMarked` (JSpecify), но в `build.gradle` нет НИ ОДНОГО
инструмента, который бы эти аннотации реально проверял:

```gradle
# build.gradle:44 — зависимость есть...
implementation "org.jspecify:jspecify:$jspecifyVersion"
# ...но во всём файле нет ни NullAway, ни Checker Framework, ни error-prone,
# ни даже плагина вроде net.ltgt.errorprone
```

Сейчас `@Nullable`/`@NullMarked` — это richly-documented комментарий, который IDE может
подсветить, но `./gradlew build` никогда не упадёт из-за нарушенного null-контракта. Это уже
отмечено как задача round 1 (#15 в `PROGRESS.md`) и всё ещё не сделано.

**Что сделать.** Подключить NullAway (через `net.ltgt.gradle:gradle-errorprone-plugin` +
`com.uber.nullaway:nullaway`) в `compileJava` — тогда null-контракт становится частью сборки, а
не пожеланием. Это, наверное, самое дешёвое по объёму работы и самое ценное по эффекту изменение
из всего отчёта: инфраструктура (Gradle + аннотации) уже вся на месте, не хватает одного плагина.

**(б) Логирование: `System.err.println` вместо `java.lang.System.Logger`.**
Раунд 2 вынес `System.err.println` из `JsonSaveRepository` в `InputHandler` (правильное
направление — репозиторий больше не решает, ЧТО делать с ошибкой), но сама печать в `System.err`
осталась:

```java
// src/main/java/com/graphics/input/InputHandler.java:148-150
if (saveRepository.save(world) instanceof SaveResult.Failure failure) {
    System.err.println("Save failed: " + failure.reason());
}
```

С Java 9 в `java.base` есть `System.Logger` (`System.getLogger(name)`) — встроенный фасад
логирования БЕЗ дополнительной зависимости, с уровнями (`INFO`/`WARNING`/`ERROR`), который любой
консольный вывод превращает в единообразные, фильтруемые записи. Для консольной демки
(`com.rustorio.Main`, `Benchmark`) `System.out.println` уместен — это их прямая задача, печатать
в консоль. Но единственный РЕАЛЬНЫЙ путь ошибки в оконной игре (`InputHandler`) заслуживает
`System.Logger`, а не голого `System.err`.

**(в) Нет статического анализа кроме тестового покрытия.**
`build.gradle` подключает `jacoco` (покрытие) и `archunit` (архитектурные инварианты как тесты) —
оба хороши, но не хватает инструмента, ищущего баги/смёлли независимо от тестов: SpotBugs,
PMD или Error Prone (последний — тот же плагин, что нужен для NullAway из пункта (а), т.е. два
улучшения покупаются одной интеграцией).

---

### 2.6 Мелкие модернизации (низкий приоритет, но по существу «современных практик»)

**Ручная упаковка координат вместо record'а.**
```java
// src/main/java/com/rustorio/domain/world/World.java:329-339
private static long key(int x, int y) {
    return ((long) x << 32) | (y & 0xFFFFFFFFL);
}
private static int keyX(long key) { return (int) (key >> 32); }
private static int keyY(long key) { return (int) key; }
```
Ручная битовая арифметика для того, для чего в современной Java есть ровно один правильный
инструмент — маленький `record`. `record Coord(int x, int y) implements Comparable<Coord>` в
качестве ключа `TreeMap` дал бы тот же порядок обхода (через `Comparable`/`Comparator`), без
риска ошибиться в знаке/переполнении и с читаемым выводом при отладке (`Coord[x=3, y=5]` вместо
`12884901893`). Не критично — работает верно, — но ровно тот случай, для которого record'ы были
придуманы, и здесь они не используются, а могли бы.

**Отсутствие `module-info.java`.**
Границы пакетов (`domain` → `domain.building` → `domain.world` → `domain.action` →
`persistence` → `graphics`) задокументированы в javadoc и (видимо) проверяются ArchUnit-тестами —
но это тестовая, а не компиляторная гарантия. Полная модуляризация (JPMS) сделала бы эти границы
частью самой сборки. Для игры, а не библиотеки, это низкий приоритет — ArchUnit обычно
достаточно, — но стоит знать, что это следующий уровень строгости, если он когда-нибудь понадобится.

---

## 3. Что стоит переделать в первую очередь (приоритеты)

**Приоритеты 1–3 выполнены 24.07.2026** — `./gradlew build` зелёный после всех правок ниже,
включая `game`/`benchmark`.

| # | Приоритет | Что | Объём работы | Эффект |
|---|---|---|---|---|
| 1 | ✅ Высокий | Подключить NullAway/Checker Framework к сборке (п. 2.5а) | Малый (один плагин + правки, если найдутся нарушения) | Контракт null-безопасности становится реальным, а не декларативным. **Сделано:** `net.ltgt.errorprone` + NullAway на `compileJava` (не на тестах), нашли и исправили 28 реальных мест — `Belt`/`Furnace`/`Miner`/`Splitter`/`UndergroundBelt` получили честные `@Nullable`, `Belt.segment` — задокументированное как «deferred init» через `Objects.requireNonNull`, `SaveResult.Failure.reason`/`RustorioGame.oreSeed`/`Main.parseSeed`/`DragCollector.poll`/`HudRenderer.nextLocked` тоже оказались нечестно неаннотированы |
| 2 | ✅ Высокий | Закрыть дыру инкапсуляции `World.stats()`/`research()` (п. 2.2) | Малый-средний | Убирает единственный найденный «читерский» вектор в домене. **Сделано:** новые top-level `ResearchView`/`ProductionStatsView` (только чтение), `World.addResearchPoints`/`restoreResearch`/`restoreStats` — единственные двери для мутации; `ProductionStats.restore` сужен до package-private |
| 3 | ✅ Высокий | Разгрузить `InputHandler`: инъекция `SaveRepository`, устранить дублирование драга (п. 2.3) | Средний | Убирает единственный настоящий God Class в проекте. **Сделано:** `InputHandler(GameCamera, SaveRepository)` вместо `new JsonSaveRepository()` внутри; новый `DragCollector` убрал ~40 строк дублирования между build/remove-драгом (322 → 271 строка) |
| 4 | ✅ Средний | Привести `Optional`-как-поле к единому стилю: `RemoveAction`/`UpgradeSpeedAction`/`Appearance` (п. 2.1) | Малый | Проект перестаёт противоречить собственному явно сформулированному принципу. **Сделано:** `@Nullable Building` вместо `Optional<Building>` в обоих действиях; `Appearance.badge` — `int` с сентинелом `-1` вместо `OptionalInt` |
| 5 | ✅ Средний | `System.Logger` вместо `System.err.println` (п. 2.5б) | Малый | Единообразная диагностика без новой зависимости. **Сделано:** `InputHandler` заводит `System.getLogger(...)`, F5/F9-ошибки логируются на уровне WARNING |
| 6 | ✅ Низкий | Sealed-состояние вместо nullable-триплета в `Furnace` (п. 2.4) | Средний-большой | Более сильная типовая гарантия. **Сделано, с уточнением:** `recipe`+`timer` объединены в один nullable `record ActiveRecipe(Recipe, ProcessTimer)` — они действительно всегда null/не-null синхронно. `pendingOutput` НЕ включён в тот же тип — при ближайшем рассмотрении это независимая ось состояния (бывает ненулевым одновременно с активным `ActiveRecipe`, если в буфере есть ещё на партию), а не часть той же машины состояний; исходная формулировка отчёта («nullable-триплет») была неточной в этой детали |
| 7 | ✅ Низкий | `record Coord` вместо ручной упаковки long (п. 2.6) | Малый | Косметика/читаемость, не влияет на корректность. **Сделано:** `World.Coord(int x, int y) implements Comparable<Coord>` заменил `key/keyX/keyY`; порядок обхода (сперва по x) сохранён явным `compareTo`; бенчмарк подтвердил — без регрессии (0,65 мс/тик против 0,70 ранее, в пределах шума) |
| 8 | ❌ Низкий | SpotBugs/PMD в сборку (п. 2.5в) | Малый | **Испробовано и отклонено.** SpotBugs (плагин `com.github.spotbugs` 6.0.26, движок по умолчанию 4.8.6, отдельно проверен движок 4.9.3) не смог проанализировать ни один класс — его версия ASM не понимает байт-код Java 25 (`Unsupported class file major version 69`), это ограничение самого инструмента, не конфигурации. Начатый переход на PMD (не требует бинарного анализа, этой проблемы не имеет) прерван по прямому указанию автора — **не подключать больше инструментов к сборке**. `build.gradle` возвращён к состоянию после пункта 1 (только `jacoco` + `net.ltgt.errorprone`/NullAway) |

---

## 4. Что осознанно НЕ проблема — не трогать

Чтобы отчёт не читался как призыв переписать всё, отдельно фиксирую то, что выглядит как
«недостаток» по общим правилам, но здесь таковым не является:

- **Нет фреймворка DI (Spring/Guice/Dagger).** Ручной constructor injection — правильный выбор
  для одного игрового процесса с небольшим графом зависимостей. Фреймворк добавил бы сложность
  без выгоды.
- **Нет Visitor-паттерна для рендера зданий.** Вместо двойной диспетчеризации через Visitor —
  здания сами описывают внешность (`appearance()`, `outputDirection()`, `heldItem()`), а рендер
  просто читает эти данные. Это осознанно избегает сложности Visitor там, где она не нужна —
  сильная сторона, не пробел.
- **Concurrency-примитивы отсутствуют.** Игра однопоточная (игровой цикл libGDX на одном потоке),
  и это правильно для данного масштаба — вводить virtual threads/executors было бы решением в
  поисках проблемы.
- **`BeltSegment` — O(n), не O(1) за тик.** Задокументированный, осознанный компромисс
  (`BeltSegment.java:16-20`) — «премьера оптимизация без замера» прямо запрещена собственным
  принципом проекта (`ProcessTimer.java` javadoc и `Benchmark.java` существуют именно для того,
  чтобы решение принималось по цифрам, а не по интуиции). Не трогать, пока `Benchmark` не покажет
  проблему.

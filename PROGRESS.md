# Rustorio — прогресс архитектурного рефакторинга

> Этот файл — точка входа для продолжения работы в новой сессии. Актуален на: ветка `temp`,
> последний коммит `dab73d8 step 3`, поверх него — **60 незакоммиченных файлов** (весь рефакторинг
> ниже нигде не закоммичен, лежит только в рабочем дереве).

## Что произошло (контекст для новой сессии)

Был запрошен **полный архитектурный рефакторинг** игры Rustorio (Factorio-подобная игра на
libGDX) под современные Java-практики и осознанное применение шаблонов проектирования — не
косметика, а полная переписка `com.rustorio` и адаптация `com.graphics` под новый API. Решение
было принято ПОСЛЕ анализа: изначальный код был качественной, пошагово выстроенной учебной
кодовой базой (лекции с якорями "урок N"), и вывод анализа был "рефакторинг не оправдан" — но
автор явно попросил переписать несмотря на это (см. историю чата: выбор "Full rewrite anyway").

Рефакторинг выполнен и **собирается и проходит все тесты** (см. ниже). После этого проведено
**два раунда независимого код-ревью** результата — все находки оформлены как задачи в системе
задач (TaskCreate/TaskList), НЕ в этом файле как текст: этот файл — только сводка с ID.

## Текущее состояние сборки

```
$ ./gradlew build
BUILD SUCCESSFUL
```
- Компиляция: чистая (main + test).
- Тесты: 52 теста, 10 классов, 0 failures (на момент последнего прогона).
- `./gradlew game` (headless demo) — работает, печатает прогресс тика.
- `./gradlew benchmark` — работает, ~0.015 мс/тик на сцене из 420 зданий.
- `./gradlew run` (оконный GUI) — **НЕ проверялся визуально ни разу** (см. задачу #21).
- Ничего не закоммичено.

## Новая архитектура

```
com.rustorio.domain            — чистые типы-значения и стратегии, ни от чего не зависит
    Item, Direction, Tech, Research, SortRule, BuildingType, Sprite, Appearance,
    Recipe, RecipeBook, OreLayout (+ PatchOreLayout impl)

com.rustorio.domain.building    — sealed-иерархия Building + BuildingFactory
    Building, BuildingMemento (sealed, 7 record-подтипов), BuildingFactory,
    Miner, Chest, Furnace, Belt, BeltSegment, Splitter, Lab, UndergroundBelt, SpeedModule, ProcessTimer

com.rustorio.domain.world       — World (aggregate root), учёт продакшена
    World, ProductionStats, ProductionLog, ProductionListener

com.rustorio.domain.action      — Command-паттерн
    PlayerAction, PlaceAction, RemoveAction, CompositeAction, UpgradeSpeedAction, ActionHistory

com.rustorio.persistence        — ЕДИНСТВЕННЫЙ пакет, которому разрешено импортировать Jackson
    SaveRepository, JsonSaveRepository, BuildingMementoMixin, WorldSnapshot, PlacedBuilding

com.rustorio (корень)           — точки входа: Main.java (headless demo), Benchmark.java

com.graphics.*                  — адаptировано под новый API (Optional-сигнатуры и т.д.),
                                   структурно НЕ переписывалось, только правки под domain-слой
```

Зависимости строго внутрь: `domain` ни от чего не зависит → `domain.building` зависит от `domain`
→ `domain.world` зависит от `domain`+`domain.building` → `domain.action` зависит от `domain.world`
→ `persistence` зависит от всего домена → `graphics` зависит от всего. Проверено grep'ом
(см. задачу #14 — хочу закрепить это ArchUnit-тестами, а не только ручной проверкой).

## Применённые паттерны (конкретно, не декларативно)

- **Factory Method** — `BuildingFactory.create()`/`restore()`, единая точка диспетчеризации
  `BuildingType`/`BuildingMemento` → конкретный `Building` (раньше было два разных `switch` в двух
  разных классах).
- **Memento** — `BuildingMemento` (sealed, по record-подтипу на здание) заменил старый ручной
  `save()/load(String)`.
- **Repository** — `SaveRepository`/`JsonSaveRepository`, наконец задействован Jackson,
  который был в `build.gradle` объявлен, но не использовался.
- **Strategy** — `SortRule` (было и раньше), плюс новые `OreLayout` (генерация карты, внедряется в
  `World`/`Miner`) и `RecipeBook` (внедряется в `Furnace`, никакой статики `Recipe.ALL`).
- **Decorator, Command+Composite, Observer** — сохранены и подчищены (`Belt` сам владеет логикой
  присоединения к сегменту, `World` больше не лезет во внутренности `BeltSegment` через границу
  пакета).
- **Jackson mixin** — `BuildingMementoMixin` учит Jackson (де)сериализовать sealed-иерархию
  мементо СНАРУЖИ, домен по-прежнему не импортирует Jackson вообще.

## Ревью — раунд 1 (про тесты, задачи #9–#21)

Автор попросил переделать так, чтобы больше не касаться тестов — но эти задачи остаются валидными,
просто не были предметом второго раунда.

| # | Приоритет | Суть |
|---|---|---|
| #9 | P0 | `RemoveAction`/`UpgradeSpeedAction` — 0% покрытия, ни разу не создавались в тестах |
| #10 | P0 | Нет теста на реальное удвоение скорости `SpeedModule`, только на делегирование |
| #11 | P0 | `MinerTest` — 45.8% покрытия, таймер добычи и FAST_MINING не проверены |
| #12 | P0 | `ProductionLog`/`Chest`/`appearance()` — 0% и 0 вызовов вообще |
| #13 | P1 | Strategy `OreLayout` заявлена как подменяемая, но ни разу не подменена в тестах |
| #14 | P1 | ArchUnit-зависимость объявлена, тестов на границы пакетов нет |
| #15 | P1 | `@NullMarked` (JSpecify) декларирован, но не enforced (нет NullAway/Checker Framework) |
| #16 | P1 | Jacoco даёт отчёт, но нет порога покрытия (`violationRules`) |
| #17 | P1 | `World.stats()/research()` отдают мутабельные объекты напрямую — дыра в инкапсуляции |
| #18 | P2 | Нет `package-info.java` у корневого `com.rustorio` |
| #19 | P2 | Языковой раскол: `domain.*` — английский, `com.graphics.*` — всё ещё русский (20 файлов) |
| #20 | P2 | У Benchmark нет зафиксированного baseline для отслеживания регрессий |
| #21 | P2 | Оконный GUI не запускался и не проверялся визуально ни разу |

## Ревью — раунд 2 (про сам код, без тестов, задачи #22–#29) — ЗАКРЫТ 24.07.2026

Все восемь задач исправлены, `./gradlew build` зелёный (компиляция + все тесты).

| # | Приоритет | Суть | Как исправлено |
|---|---|---|---|
| #22 | P0 | `Belt.attachToNeighbors(Optional<Belt>, Optional<Belt>)` — Optional как параметр | Заменено на `@Nullable Belt behind, @Nullable Belt ahead` (jspecify), вызывающий код в `World.attachToSegment` разворачивает `Optional` через `.orElse(null)` |
| #23 | P0 | `BuildingFactory.restore(type, memento, speedLevel)` — параметр `type` используется в 1 из 7 веток | `BuildingMemento.FurnaceState` теперь сам несёт `kind`; `restore()` больше не принимает `type` вообще; попутно убран теперь-бессмысленный дублирующий `type` из `PlacedBuilding` |
| #24 | P0 | `JsonSaveRepository` — `boolean` + `System.err.println` | Новый sealed `SaveResult` (`Success`/`Failure(reason)`) в `com.rustorio.persistence`; `SaveRepository.save/load` возвращают его; `System.err.println` перенесён из репозитория в вызывающий код (`InputHandler`, F5/F9) |
| #25 | P1 | `Furnace` — 8-параметровый package-private конструктор восстановления | Конструктор теперь принимает сам `BuildingMemento.FurnaceState` + `RecipeBook` (2 параметра вместо 8) |
| #26 | P1 | DI-возможности нигде не используются кроме `.standard()` | **Решение автора: добавить реальное использование.** Новый `RandomOreLayout` (второй `OreLayout`, карта руды из зерна) + `--seed=<N>` у `com.graphics.Main` → `RustorioGame` → `GameScreen(long)`, который строит `new BuildingFactory(new RandomOreLayout(seed, w, h), RecipeBook.standard())`. Без флага — прежняя `PatchOreLayout.standard()`. Новый `RandomOreLayoutTest` (детерминизм, покрытие, границы) |
| #27 | P1 | Пропало предупреждение про `SortRule` сплиттера | Javadoc-предупреждение восстановлено на `Splitter` (класс) и на ветке `SplitterState` в `BuildingFactory.restore` |
| #28 | P2 | `World` копит ответственности | Не рефакторено (осознанно, P2) — добавлена явная заметка в javadoc `World`, что это известный, принятый технический долг, а не незамеченный дрейф |
| #29 | P2 | `Miner` падает `IllegalStateException` без руды | **Решение автора: мягкая деградация.** `Miner.tick` теперь просто простаивает и повторяет попытку каждые `effectiveTime` тиков вместо падения; javadoc класса обновлён |

Оконный GUI (`--seed=`, F5/F9 → `SaveResult`) по-прежнему **не проверялся визуально** — это тот же старый пункт #21 из раунда 1, средой для этой сессии не подтверждён (нет дисплея).

## Полное архитектурное ревью (без тестов) — ЗАКРЫТ 25.07.2026, приоритеты 1–7 исправлены, 8 отклонён

Отдельный, более широкий заход, чем раунды 1/2 — полный отчёт в `ARCHITECTURE_REVIEW.md`
(корень репозитория): каких современных практик не хватает, где нужны паттерны, что переделать.
Все восемь приоритетов из отчёта разобраны:

1. **NullAway включён на сборку** (`net.ltgt.errorprone` плагин + `com.uber.nullaway:nullaway` в
   `build.gradle`, только на `compileJava`, не на тестах) — `@NullMarked`/`@Nullable` (JSpecify)
   были задекларированы во всех `package-info.java`, но никем не проверялись; нашлось и исправлено
   28 реальных нарушений (честные `@Nullable` на `Belt.held`/`Furnace.recipe,timer,pendingOutput`/
   `Miner.held`/`Splitter.held`/`UndergroundBelt.held`/`SaveResult.Failure.reason`/
   `RustorioGame.oreSeed`/`Main.parseSeed`/`DragCollector.poll`/`HudRenderer.nextLocked`;
   `Belt.segment` — задокументированный «deferred init», читается через `Objects.requireNonNull`).
2. **Закрыта утечка инкапсуляции `World.stats()`/`research()`** — новые top-level
   `ResearchView`/`ProductionStatsView` (только методы чтения), `World.addResearchPoints`/
   `restoreResearch`/`restoreStats` — единственные легальные двери для мутации; `Lab`/
   `JsonSaveRepository` переведены на них.
3. **`InputHandler` разгружен** — конструктор теперь принимает `SaveRepository` явно (раньше
   `new JsonSaveRepository()` был захардкожен внутри), новый `DragCollector` убрал дублирование
   между build-драгом (ЛКМ) и remove-драгом (ПКМ) (`InputHandler` 322 → 271 строка).
4. **`Optional`-как-поле убран** — `@Nullable Building` вместо `Optional<Building>` в
   `RemoveAction`/`UpgradeSpeedAction`; `Appearance.badge` — `int` с сентинелом `-1` вместо
   `OptionalInt`.
5. **`System.Logger` вместо `System.err.println`** в `InputHandler` (F5/F9-ошибки → WARNING).
6. **`Furnace.recipe`+`timer` объединены** в один nullable `record ActiveRecipe(Recipe,
   ProcessTimer)` — они действительно всегда null/не-null синхронно. `pendingOutput` НЕ включён
   в тот же тип: при реализации выяснилось, что это независимая ось состояния (может быть
   ненулевым одновременно с активным `ActiveRecipe`), а не часть той же машины состояний —
   исходная формулировка в отчёте («nullable-триплет») была неточна в этой детали.
7. **`World.Coord`** (`record`, `Comparable`) заменил ручную упаковку координат в `long`
   (`key`/`keyX`/`keyY`); порядок обхода сохранён явным `compareTo`. Бенчмарк подтвердил — без
   регрессии.
8. **SpotBugs — испробовано и отклонено.** Плагин `com.github.spotbugs` (проверены движки 4.8.6 и
   4.9.3) не смог проанализировать ни одного класса — его версия ASM не понимает байт-код Java 25
   (`Unsupported class file major version 69`), это ограничение самого инструмента. Начатый переход
   на PMD прерван по прямому указанию Максима: **больше не подключать инструменты к сборке**.
   `build.gradle` возвращён к состоянию после пункта 1 (только `jacoco` + NullAway).

`./gradlew build` (компиляция + все тесты + NullAway) и `./gradlew game`/`benchmark` проверены
зелёными после каждого шага.

## Как продолжить в новой сессии

1. Прочитать этот файл (или он подхватится автоматически, если сессия в этой же папке —
   см. также project-память ассистента). Для архитектурного ревью — читать также
   `ARCHITECTURE_REVIEW.md`.
2. `git status` — всё ещё незакоммиченные файлы, ничего не потеряно; раунд 2 + всё архитектурное
   ревью (приоритеты 1–8) разобраны, но не закоммичены.
3. `TaskList` — раунд 2 (#22–#29) и всё архитектурное ревью (приоритеты 1–8) `completed`. Раунд 1
   (#9–#21) в системе задач не пересоздан в этой сессии — при следующем заходе на него нужно
   создать задачи заново по таблице выше в разделе «Ревью — раунд 1». Из архитектурного ревью
   больше ничего не осталось — правило сессии «не подключать новые инструменты к сборке» остаётся
   в силе, если Максим явно не попросит иначе.
4. Порядок разбора раунда 1 — по приоритету: сначала P0 (#9, #10, #11, #12), потом P1, потом P2.
   Порядок разбора архитектурного ревью — приоритеты 4–8 из `ARCHITECTURE_REVIEW.md`.
5. Ничего не коммитить без явной просьбы — таково правило в этой сессии.

# Rustorio → «Собери свой ПК»: дорожная карта

> Составлено: 2026-07-30, ветка `engine`. Живой документ — правьте по ходу разработки,
> отмечайте `[x]` в файлах этапов вместо переписывания текста.

## 0. Контекст

Цель: сохранить текущий визуальный слой и цикл «добыча → лента → переработка → ящик»
(мини-Factorio на Java 25 + libGDX, домен в `com.rustorio.*`), но заменить содержание
производственной цепочки на путь **Песок/Медь/Олово → Кремний → Транзистор →
Микросхема → Процессор → Компьютер**.

Три решения приняты в обсуждении (2026-07-30) и фиксируют объём:

| Вопрос | Решение |
|---|---|
| Как игрок создаёт логику для чипов | **Только рецептный крафт** — никакого интерактивного редактора вентилей/проводов в MVP |
| Где могло бы жить проектирование схем, если понадобится позже | Отдельный экран-верстак (не на карте завода) — но **не строим сейчас**, см. [06-out-of-scope.md](06-out-of-scope.md) |
| Финал игры («запуск» ПК) | **Скриптовая кат-сцена** по факту сборки нужных предметов — не настоящий эмулятор |

Это резко упрощает объём: вся работа сводится к «добавить новый контент в
Factorio-подобный движок», а не «написать вторую игру внутри игры».

## 1. Что переиспользуется без изменений

Код уже 2026-07-24…25 прошёл архитектурное ревью ([ARCHITECTURE_REVIEW.md](../../ARCHITECTURE_REVIEW.md))
и специально спроектирован как контент-движок, а не хардкод:

| Механизм | Файл | Роль в новой игре |
|---|---|---|
| `Registry<T>` + `ContentId` | [Registry.java](../../src/main/java/com/rustorio/api/registry/Registry.java) | Новые предметы/здания — регистрация, не код |
| `ItemType` (record, данные) | [ItemType.java](../../src/main/java/com/rustorio/domain/ItemType.java) | Кремний, транзисторы, платы — просто новые записи |
| `RecipeBook` | [RecipeBook.java](../../src/main/java/com/rustorio/domain/RecipeBook.java) | Новые рецепты (в пределах 2 ингредиентов — см. §3) |
| `BuildingFactory` — один `Furnace` на три роли | [BuildingFactory.java:88-110](../../src/main/java/com/rustorio/domain/building/BuildingFactory.java) | `FURNACE`/`PRESS`/`ASSEMBLER` — уже один Java-класс, три `BuildingType`. Новые здания (печь кремния, литография) — тот же приём |
| `OreLayout` (Strategy) | [OreLayout.java](../../src/main/java/com/rustorio/domain/OreLayout.java), [PatchOreLayout.java](../../src/main/java/com/rustorio/domain/PatchOreLayout.java) | Новые жилы (песок, олово, нефть, золото) — новые `OrePatch` |
| `Tech` (enum, дерево, компилятор гарантирует ацикличность) | [Tech.java](../../src/main/java/com/rustorio/domain/Tech.java) | Новая ветка `SILICON_PURIFICATION → … → MICROPROCESSORS` |
| `Lab` | [Lab.java](../../src/main/java/com/rustorio/domain/building/Lab.java) | Переименовать в «тестовый стенд», механика не меняется |
| `ProductionListener`/`notifyProduced` | [TickContext.java](../../src/main/java/com/rustorio/domain/building/TickContext.java) | Готовый крючок для финальной кат-сцены — подписаться на выпуск предмета «Компьютер» |
| Save/load (Jackson, Memento) | `persistence/*` | Не трогаем — новые предметы/здания проходят тот же путь автоматически |

## 2. Что действительно новое

- **Bronze → Copper**: сегодня в игре Iron+Bronze, а не Iron+Copper — это переименование
  и частичное ветвление (медь идёт и в старую цепочку Mechanism/Engine/Chassis, и в новую
  электронику). См. [01-phase0-foundation.md](01-phase0-foundation.md).
- **Новые сырьевые ресурсы**: кварцевый песок, олово, свинец, нефть/пластик, золото (редкое).
- **~25-30 новых `ItemType`** по 4 эпохам (см. файлы эпох).
- **Несколько новых `BuildingType`**, все — переиспользование `Furnace`/`Lab` классов с новым
  `kind`, как уже сделано для `ASSEMBLER` — не новая Java-логика, а новая запись в
  `BuildingFactory.create` + `VanillaBuildings.registerAll`.
- **Новая ветка `Tech`**.
- **Триггер финальной кат-сцены** в `com.graphics` — новый код, но маленький: подписка на
  `ProductionListener` уровня существующего механизма.

## 3. Главная техническая находка: предел в 2 ингредиента на рецепт

`Recipe` ([Recipe.java:15](../../src/main/java/com/rustorio/domain/Recipe.java)) — ровно
`input` + необязательный `input2`. `Furnace` ([Furnace.java:74-75](../../src/main/java/com/rustorio/domain/building/Furnace.java))
хранит их в буквально двух полях `bufferA`/`bufferB`. Рецепты вида «плата + 4 транзистора +
2 резистора + припой = микросхема» (as-is из документа) в эту модель не влезают — сегодня
это физически невозможно, не «не сделано», а «не работает при попытке».

Два пути, оба расписаны в [04-era3-integrated-circuits.md](04-era3-integrated-circuits.md):

1. **Без правки движка** — раскладывать сложную сборку на цепочку 2-компонентных шагов
   через промежуточные «наборы» (`Logic Core Kit`, `Board Kit`, …). Быстрее, нулевой риск для
   персистентности, но плодит промежуточные предметы и меньше похоже на реальные рецепты.
2. **С правкой движка** — обобщить `Recipe` на список ингредиентов (рекомендую потолок в 4),
   завести map-буфер вместо `bufferA`/`bufferB` в новом классе сборки. Больше похоже на
   настоящий Factorio (там тоже N-арные рецепты), но это единственная по-настоящему новая
   инженерная задача во всём плане — трогает `Recipe`, `Furnace`/новый building-класс,
   `BuildingMemento.FurnaceState`, `JsonSaveRepository`, добрый десяток тестов.

**Рекомендация**: начать с варианта (1) для эпох 1-2 (там и так максимум 2 ингредиента),
принять решение по (2) осознанно перед стартом эпохи 3 — это единственная точка плана,
где стоит остановиться и решить, а не просто пойти по списку задач.

## 4. Карта эпох

| Эпоха | Тема | Ключевые новые предметы | Ключевые новые здания | Трофей эпохи |
|---|---|---|---|---|
| 0 | Фундамент | Copper (вместо Bronze), Sand, Tin, Lead, Oil, Gold | — | — |
| 1 | Вакуум и медь | Copper Cable, Vacuum Tube | — (существующие Furnace/Press) | Простое радио/арифмометр |
| 2 | Дискретные транзисторы | Silicon Wafer, Solder, Textolite, Transistor, PCB | Silicon Furnace | Logic Block |
| 3 | Интегральные схемы | Resistor/Capacitor/Inductor Pack, Crystal Oscillator, Logic IC | Lithography (или переиспользуем Assembler) | Memory Module, ALU Module |
| 4 | Микропроцессоры и ПК | VLSI Chip, Motherboard, CPU, RAM, GPU, Case | Final Assembly (переиспользуем Assembler) | **Компьютер** → кат-сцена |

## 5. Файлы этапов

1. [01-phase0-foundation.md](01-phase0-foundation.md) — Bronze→Copper, новое сырьё, инфраструктура
2. [02-era1-vacuum-copper.md](02-era1-vacuum-copper.md)
3. [03-era2-discrete-transistors.md](03-era2-discrete-transistors.md)
4. [04-era3-integrated-circuits.md](04-era3-integrated-circuits.md) — включает решение по §3
5. [05-era4-microprocessor-pc.md](05-era4-microprocessor-pc.md) — финал и кат-сцена
6. [06-out-of-scope.md](06-out-of-scope.md) — сознательно отложено, не в этом плане

## 6. Вторая находка: сколько на самом деле кода, а не данных

Прочитаны `Palette.java`, `WorldRenderer.java`, `ItemRenderer.java`, `BuildingRenderer.java`,
`RecipeBookRenderer.java`, `TechTreeRenderer.java`, `HotbarLayout.java`, `InputHandler.java` —
чтобы файлы этапов давали не «добавь предмет» общими словами, а точный список файлов и готовый
код. Итог оказался даже лучше, чем предполагалось:

| Что добавляем | Нужно ли трогать рендер/хэндлер | Почему |
|---|---|---|
| Новый `ItemType` (сырьё, плита, деталь) | **Нет** для крафта/HUD/книги рецептов/ленты | `ItemRenderer`/`Palette.itemColor`/`RecipeBookRenderer` читают `ItemType.colorRgb()/shape()`/`RecipeBook.all()` напрямую, без `switch` по конкретным предметам |
| Новый `ItemType`, который лежит **на земле** как руда | **Да, 1 маленькая правка** | `WorldRenderer.oreColor()` — не `switch`, а цепочка `if (ore == …)` с дефолтом на цвет железа; без явной ветки новая руда просто визуально сольётся с железом |
| Новый `Tech` | **Нет** | `TechTreeRenderer` итерирует `Tech.values()` |
| Новый `BuildingType`, использующий существующий `Furnace`/`Lab` класс | **Нет** в `InputHandler`/`HotbarLayout` | Хотбар и клавиши 1-9 читают `BuildingType.values()`, ничего не хардкодят по конкретным сортам |
| Тот же новый `BuildingType` — спрайт | **Да, 2 маленькие правки, без правки логики** | Новая константа в `VanillaSprites`, новая строка в `TextureIndex.vanilla()` + сам PNG-файл |

Практический вывод: почти весь план — это данные (регистрация в `Registry`) плюс несколько
однострочных правок в списке выше. Единственная НАСТОЯЩАЯ новая логика во всём плане —
N-арные рецепты (см. §3) и триггер финальной кат-сцены (Эпоха 4). Всё остальное готовым
кодом расписано в файлах этапов ниже — менять алгоритмы нигде, кроме этих двух мест, не
придётся.

## 7. Побочная находка (не блокирует план)

`README.md`/`Cargo.toml` в корне репозитория всё ещё описывают старый крошечный
Rust+macroquad прототип — реальный код давно Java/libGDX. Не имеет отношения к этому плану,
но стоит поправить отдельной мелкой задачей, чтобы новый контрибьютор не путался.

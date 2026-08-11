# Дизайн: Rustorio API v2 (удобный публичный контракт для модов)

Статус: **принят владельцем 10.08.2026** (§11 закрыт). Фазы 1, A/B/C, **3** и сужение
`domain.building` до allowlist (без сетей/concretes/`VanillaBuildings`) — в коде.
Онбординг: `docs/start-here.md`. Фаза 4 (MapCodec / physical move content types) — отдельно.

Решения §11:
1. Цель L0–L3 + узкий api — **да**.
2. Стартовать фазу 1 сейчас — **да**.
3. `SimpleCrafter` — **в публичном API** (доступен модам через apiJar).
4. Окно deprecation толстого domain — **да** (предупреждения в минорах, сужение classloader к 1.0).
5. Tech effects — **в этом эпике** (минимальный реестр + `TechType.effects` + `ResearchView.hasEffect`).

Документ отвечает на «как сделать API понятным и современным», сверяясь с `AGENTS.md`:
расширяемость данными, детерминизм, совместимость сейва, узкие порты, без лишних зависимостей.

Связанные факты уже в коде:

- `apiJar` = `api/**` + `domain/**` **минус** world/action и минус кухня `domain.building`
  (только allowlist контрактов; сети и concretes снаружи);
- `ModClassLoader` + `ModBuildingApiAllowlist` стерегут то же;
- `BeltSegment` ещё в поверхности (нужен `TransportNode`); типы контента ещё в `domain`;
- поведение и tech-эффекты осознанно не data-only (владелец отклонил Lua/JS/WASM).

---

## 1. Зачем

Сейчас мод компилируется против симуляции целиком. Новичок видит `World`, сети, 12-аргументный
`BuildingPrototype` и обязан понять `peek` vs `get` раньше, чем поставит янтарь на карту.

Цель v2: **Factorio-уровень данных + Fabric-уровень jar API** — мало обязательных понятий,
готовые «батарейки», старый код модов живёт рядом до явного deprecation-окна.

### Столпы GDD

- **Просто и предсказуемо** — один способ сделать обычную вещь.
- **Автоматизируй всё** — мод как контент, не как форк движка.
- **Виден результат** — `./gradlew initMod` + пример на 40 строк, играбельно сразу.

---

## 2. Нецели

- Не ECS и не новый язык скриптов (уже отклонено).
- Не песочница / безопасность недоверенных jar.
- Не ломать сейвы v9 ради красоты API.
- Не переписывать JSON-схемы с нуля в той же карточке (они остаются Level 0).
- Не тащить DI-фреймворк или codegen-зависимости без отдельного решения владельца.

---

## 3. Слои моддинга (официальная лестница)

| Уровень | Кто | Чем пользуется | Пример |
|--------|-----|----------------|--------|
| **L0 Data** | автор контента | только `content/**/*.json` | титан из `first-mod.md` |
| **L1 Tune** | баланс / рескин | JSON + traits / `modifyContent` | дороже печь, медленнее рецепт |
| **L2 Behavior** | программист мода | `rustorio-api` + builder + `SimpleCrafter` / свой `Building` | полировщик янтаря |
| **L3 Engine-touch** | опытный | capabilities, `ServiceKey`, `ViewableBuilding` | webminer, свои сети |

Правило продукта: **документация и примеры ведут с L0 → L2**. L3 не прячем, но не ставим на первую страницу.

---

## 4. Целевая поверхность `rustorio-api`

### 4.1 Пакеты (что попадает в jar и в `PARENT_DELEGATED_PREFIXES`)

```
com.rustorio.api.
  content/          ContentId (уже есть)
  registry/         Registry, RegistryKey (уже есть)
  mod/              RustorioMod, RegistrationContext, EventBus, события
  building/         НОВОЕ: контракты здания
  content.model/    НОВОЕ: ItemType, Recipe, FluidType, TechType, AuthoredMap, …
  dsl/              НОВОЕ: ContentDsl, BuildingDsl (фаза 1–2)
```

**Уходит из делегирования для модов (после окна миграции):**

- `com.rustorio.domain.world.**` (`World`, `TickScheduler`, …)
- внутренние сети: `FluidNetwork`, `PowerNetwork`, `BeltSegment`, …
- прочая «кухня» симуляции, не нужная для `Building` / регистрации

**Остаётся доступным как контракт (переезд или re-export):**

| Тип сегодня | Куда в v2 | Почему |
|-------------|-----------|--------|
| `Building`, `TickContext`, `Codec`, `BuildingPrototype` | `api.building` | мод обязан их видеть |
| capabilities (`SettlesEachTick`, `TransportNode`, `InspectableBuilding`, …) | `api.building` | open membership |
| `ItemType`, `Recipe`, `FluidType`, `TechType`, `AuthoredMap`, `OrePatch` | `api.content.model` | регистрация контента |
| `ServiceKey` | `api.mod` или `api.building` | webminer-паттерн |
| `PlacementRule` (функциональный тип) | `api.building` | правило размещения |
| `VanillaSprites`, id ванильных предметов | `api.content.vanilla` или allowlist | удобные константы |

Физический переезд классов — поэтапно: сначала re-export из `api.*`, потом сужение classloader.

### 4.2 `RegistrationContext` v2 — те же реестры + DSL-дверь

Существующие `items()`, `buildings()`, `recipes()`, … **не удаляются**.

Добавляется:

```java
public interface RegistrationContext {
    // …как сейчас…

    /** Namespace этого мода из mod.json. */
    String modNamespace();

    /** Fluent регистрация L0/L1/L2. */
    ContentDsl content();

    /**
     * До freeze: предмет обязан существовать, иначе понятная ошибка.
     * Заменяет загадку peek vs get для большинства call sites в registerContent.
     */
    ItemType requireItem(ContentId id);

    default ItemType requireItem(String path) {
        return requireItem(ContentId.of(modNamespace() + ":" + path));
    }
}
```

`peek` / `get` остаются для продвинутых сценариев и чужих раундов.

---

## 5. Конкретные интерфейсы DSL

### 5.1 ContentDsl

```java
public interface ContentDsl {
    ItemDraft item(String path);
    RecipeDraft recipe(String path);
    MapDraft map(String path);
    FluidDraft fluid(String path);
    BuildingDsl building(String path);
}

public interface ItemDraft {
    ItemDraft label(String label);
    ItemDraft color(int rgb);
    ItemDraft shape(ItemShape shape);
    ItemDraft researchGrade();
    ContentId register();
}

public interface RecipeDraft {
    RecipeDraft input(String itemPathOrId);
    RecipeDraft inputs(String... itemPathOrId);
    RecipeDraft output(String itemPathOrId);
    RecipeDraft time(int ticks);
    RecipeDraft inFurnace();
    RecipeDraft inPress();
    RecipeDraft inAssembler();
    RecipeDraft kind(ContentId recipeKind);
    ContentId register();
}

public interface MapDraft {
    MapDraft label(String label);
    MapDraft ore(String itemPathOrId, int cx, int cy, int radius);
    MapDraft terrain(String itemPathOrId, int cx, int cy, int radius);
    ContentId register();
}
```

Имена без `:` → `modNamespace() + ":" + path`. Полный id (`rustorio:coal`) принимается как есть.

### 5.2 BuildingDsl

```java
public interface BuildingDsl {
    BuildingDsl label(String label);
    BuildingDsl cost(String itemPathOrId, int amount);
    BuildingDsl placement(String rulePathOrId);
    BuildingDsl texture(ContentId sprite);
    BuildingDsl size(int w, int h);
    BuildingDsl category(ContentId category);

    BuildingDsl powerDemand(int demand);
    BuildingDsl powerOutput(int output);
    BuildingDsl poleRadius(int radius);
    BuildingDsl fluidInput(String fluidPathOrId);
    BuildingDsl fluidOutput(String fluidPathOrId);

    /** L1: чужое поведение (архетип), свои цифры. */
    BuildingDsl archetype(BuildingType type);

    /** L2: своё поведение. */
    BuildingDsl behavior(BehaviorFactory create);
    BuildingDsl restore(RestoreFactory restore);
    BuildingDsl codec(Codec<?> codec);

    /** L2 с батарейкой — §6. */
    BuildingDsl simpleCrafter(SimpleCrafterSpec spec);

    ContentId register();
}
```

Под капотом — сегодняшний `BuildingPrototype` + `Traits`. Телескопный конструктор остаётся
engine-facing; моды в гайде на него больше не ссылаются.

### 5.3 Целевой пример L2

```java
public final class AmberworksMod implements RustorioMod {
    @Override
    public void registerContent(RegistrationContext ctx) {
        ctx.content().item("amber_ore").label("Amber Ore").color(0xC9882A).shape(CIRCLE).register();
        ctx.content().item("amber_ingot").label("Amber Ingot").color(0xE8B84A).shape(SQUARE).register();
        ctx.content().item("amber_polished").label("Polished Amber").color(0xFFD27A)
                .shape(TRIANGLE).researchGrade().register();

        ctx.content().recipe("smelt_amber")
                .input("amber_ore").output("amber_ingot").time(8).inFurnace().register();

        ctx.content().map("amber_cove")
                .ore("amber_ore", 18, 18, 5)
                .ore("rustorio:coal", 26, 18, 3)
                .register();

        ctx.content().building("polisher")
                .label("Amber Polisher")
                .cost("rustorio:iron_plate", 8)
                .placement("needs_passable_terrain")
                .texture(VanillaSprites.ASSEMBLER)
                .simpleCrafter(SimpleCrafterSpec.of("amber_ingot", "amber_polished", 15))
                .register();
    }
}
```

---

## 6. Батарейка `SimpleCrafter`

```java
/** Готовая машина: буфер входа → N тиков → held выход → offerForward. */
public final class SimpleCrafter implements Building, InspectableBuilding {
    public SimpleCrafter(BuildingPrototype self, Direction dir,
            ItemType input, ItemType output, int workTicks, int inputMax) { /* … */ }
}
```

В комплекте: стандартный `Codec`, чтение `power.demand` из traits, `inspectionDetails`,
выдача по `direction`. Свой `Building` с нуля — только если правило сложнее (webminer, multi-out).

---

## 7. Codec v2 (фаза 2–3)

Минимум без новых зависимостей — `MapCodec` builder (`direction` / `intField` / `item`).

Позже (отдельное решение): `RecordCodec` для record из разрешённых типов.

`StateMigration` + `stateVersion` становятся полями прототипа / методами builder **в той же фазе,
что первый реальный hop** — не раньше.

---

## 8. Tech effects (фаза 1 + C)

В эпике фазы 1: реестр, `TechType.effects`, JSON, `ResearchView.hasEffect`, ванильные маркеры.

Карточка C: {@code Chest} / {@code Furnace} buffer / {@code UndergroundBelt} читают
`hasEffect(VanillaTechEffects.*)` вместо хардкода tech id — JSON-мод может выдать тот же named
effect со своей технологии.

---

## 9. Deprecation и совместимость

### 9.1 Без breaking (v2.0 окно)

- `RustorioMod` три раунда + `subscribeEvents`
- контракт `Registry`
- JSON `content/**` (additive fields ок)
- `TickContext` (только additive methods)
- capabilities
- сейв: `prototypeId` + codec map, soft-fail missing prototype

### 9.2 Deprecated для авторов модов (1–2 минора)

| Было | Стало |
|------|-------|
| `PlacementRule.NEEDS_*` в моде | `placement("…")` / peek id |
| `new BuildingPrototype(…14 args…)` | `BuildingDsl` |
| импорт `domain.world.World` | запрещён classloader’ом |
| `VanillaBuildings.frozen()` из мода | `context.buildings().peek` / require |

### 9.3 petrochem / webminer

1. **Фаза 0:** документ; код не ломает моды.
2. **Фаза 1:** DSL + SimpleCrafter + `requireItem` — **additive**.
3. **Фаза 2:** типы контента из `api.*` (re-export); постепенная смена imports.
4. **Фаза 3:** apiJar/classloader без `domain.world` и `domain.action` (**сделано**).
   Сети и `BeltSegment` оставлены под node-сигнатуры; полнота jar — `ModApiSurfaceCompletenessTest`.
5. **Фаза 4:** примеры на DSL; ElectroCracker остаётся L3-образцом; MapCodec / physical move;
   отдельный срез сетей после редизайна `FluidNode`/`PowerNode`.

Acceptance на каждой фазе: `PetrochemGuideAcceptanceTest`, `WebMinerChainTest`,
`ModApiSurfaceTest`, `ModBoundaryRulesTest`.

---

## 10. Доказательства

| Утверждение | Как доказать |
|-------------|--------------|
| api jar без World / action | `ModApiSurfaceTest` + `ModApiSurfaceCompletenessTest` |
| api jar сигнатуры замкнуты | `ModApiSurfaceCompletenessTest` |
| мод не линкует World | `ModBoundaryRulesTest` + compile petrochem/webminer |
| DSL ≡ ручной BuildingPrototype | parity-тест |
| SimpleCrafter варит и сейвится | unit + save round-trip |
| старый путь жив | VanillaAsModParity / BuildingJsonLoader |

---

## 11. Решения владельца (закрыть до кода фаз 2–3)

1. Принимаем лестницу L0–L3 и узкий api как цель?
2. Имена пакетов: `api.building` + `api.content.model` ок?
3. `SimpleCrafter` в публичном api или только в examples?
4. Codec: `MapCodec` сейчас / RecordCodec позже / пока ничего?
5. Tech effects в этом эпике или отдельной карточкой?
6. Окно deprecation толстого domain: 1 минор / 2 минора / до 1.0?
7. Делать `./gradlew initMod` в фазе 1?
8. Физический переезд типов vs facade-reexport — что предпочтительнее?

Фаза 1 (additive DSL) можно согласовать отдельно как низкий риск.

---

## 12. Фазы внедрения

| Фаза | Содержание | Риск для модов |
|------|------------|----------------|
| **0** | Этот документ + Hello Building 40 строк | нет |
| **1** | DSL, requireItem, SimpleCrafter, tech effects, initMod | нет (additive) |
| **2** | Re-export facades в `api.building` + каталоги `api.content.model` / `vanilla`; гайд DSL | низкий |
| **3** | Сужение apiJar/classloader (world + action; сети пока остаются) | средний · **сделано** |
| **4** | MapCodec; deprecate PlacementRule constants; physical move records | отд. карточки |

---

## 13. Отброшенные альтернативы

| Альтернатива | Почему нет |
|--------------|------------|
| Сразу выкинуть domain из apiJar | Ломает все внешние моды в один день |
| Lua/JS | Отклонено владельцем |
| Полный ECS | Capabilities уже есть; цена миграции огромна |
| Spring/Guice | Не стиль репозитория |
| «Просто улучшить гайд» | Не убирает World из classpath мода |

---

## 14. Проектная записка

```
ПРОЕКТНАЯ ЗАПИСКА · API v2 design
Решение: узкий rustorio-api + DSL + SimpleCrafter; старый путь рядом; сужение classloader позже
Столп GDD: Просто и предсказуемо + расширяемость данными
Границы: не Lua/ECS/sandbox; сейв v9 не ломаем; §11 ждут владельца
Новая руда → 0 файлов движка · Новое здание-поведение → SimpleCrafter или 1 класс
Детерминизм: тик не трогаем; freeze/rawId как сейчас
Совместимость: additive 1–2; breaking только фаза 3 с acceptance модов
Тест-доказательство: ModApiSurface + DSL parity + petrochem/webminer
Риск: рано сузить jar — поэтому deprecation-окно
Отброшенная альтернатива: только документация без сужения classpath
```

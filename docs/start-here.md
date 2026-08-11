# С чего начать — карта входа в Rustorio

Стабильный индекс для людей и агентов. Не статус-эпик и не одноразовая записка: правится, когда
меняется **контракт**, а не WIP-ветка.

Правила кода и границы пакетов — в [`AGENTS.md`](../AGENTS.md). Видение игры — в [`GDD.md`](../GDD.md).

## Текущий контракт моддинга (кратко)

| Уровень | Чем пользоваться | Канонический вход |
|--------|------------------|-------------------|
| **L0** Data | только `content/**/*.json` | [`first-mod.md`](first-mod.md) |
| **L1** Tune | JSON + `modifyContent` | [`modding-guide.md`](modding-guide.md) ч. 3 |
| **L2** Behavior | `ContentDsl` + `SimpleCrafter` | [`hello-building.md`](hello-building.md) + [`examples/external-mod-template`](../examples/external-mod-template) / `./gradlew initMod` |
| **L3** Engine-touch | свой `Building`, `ServiceKey`, сети | petrochem в гайде; in-repo `webminer`; [`examples/servicemod`](../examples/servicemod) |

**Одна правда про L2:** шаблон и `initMod` — это DSL + SimpleCrafter. Свой `BuildingPrototype` +
`Codec` — только L3.

Поверхность `rustorio-api` по `domain.building` — **allowlist контрактов** (Building,
TickContext, SimpleCrafter, BeltSegment, FluidPort, BuildingServices, …). Сети, ванильные
concretes (`Pipe`, `Furnace`, …), `VanillaBuildings`, `BuildingFactory` и `NetworkWiring` модам
**не видны**. Каталожные модели (`FluidType`, `ItemType`, `ItemShape`, `Recipe`, `RecipeKind`,
`TechType`, `AuthoredMap`, `OrePatch`, `TerrainPatch`) уже в `api.content.model`; `Vanilla*`
(`VanillaItems`, `VanillaSprites`, `VanillaTechs`, `VanillaTechEffects`, `VanillaFluids`) — в
`api.content.vanilla`.

Из apiJar / classloader также убраны `domain.world` и `domain.action`.

## Куда смотреть при задаче

| Задача | Смотри сюда |
|--------|-------------|
| Новая руда / рецепт / JSON-здание | `docs/first-mod.md`, схемы `docs/schemas/`, ваниль `resources/mods/rustorio/content/` |
| Станок «вход → N тиков → выход» | `docs/hello-building.md`, `SimpleCrafter`, шаблон выше |
| Свой тик здания / сейв состояния | `Building`, `Codec`, `TickContext` — `domain.building` (+ facade в `api.building`); образец L3: `src/mods/webminer` |
| Соседняя клетка, жидкость, энергия в тике | **только** `TickContext` / порты — не `World`, не сети напрямую |
| Регистрация мода, раунды, события | `RustorioMod`, `RegistrationContext` — `api.mod`; жизненный цикл в `modding-guide.md` §3.4 |
| Tech-эффекты | `TechEffect`, `ResearchView.hasEffect`, JSON `effects` — `modding-guide.md` |
| Сейв / загрузка мира | `com.rustorio.persistence`, правило `.claude/rules/persistence.md` |
| Рендер, HUD, ввод | `com.graphics`, правило `.claude/rules/graphics.md` |
| Тик мира, размещение | `com.rustorio.domain.world` — **движок**, модам недоступен |
| Undo/redo игрока | `com.rustorio.domain.action` — **движок**, модам недоступен |
| Границы пакетов / Jackson / NullAway | `AGENTS.md` + ArchUnit-тесты |
| Перед кодом карточки | skill `/design-note` → записка ≤15 строк |
| Ревью перед «готово» | субагент `rustorio-reviewer` |

## Что импортировать (мод)

```
com.rustorio.api.mod.*          // RustorioMod, RegistrationContext, события, TechEffect
com.rustorio.api.dsl.*          // ContentDsl (через ctx.content())
com.rustorio.api.building.*     // facade Building / Codec / capabilities (новый код)
com.rustorio.api.content.ContentId
com.rustorio.api.registry.*

com.rustorio.api.content.model.*  // FluidType, ItemType, ItemShape, Recipe, TechType, …
com.rustorio.api.content.vanilla.* // VanillaSprites, VanillaItems, VanillaTechs, …
com.rustorio.domain.building.TickContext   // ВСЕГДА отсюда, не facade
com.rustorio.domain.building.SimpleCrafter // батарейка L2
```

Не импортировать из мода: `domain.world`, `domain.action`, `FluidNetwork`/`PowerNetwork`,
`VanillaBuildings`, `BuildingFactory`, `NetworkWiring`, concrete `Pipe`/`Furnace`/…,
`com.rustorio.mod` (загрузчик), `com.graphics`, `persistence`.

В лямбдах `behavior`/`restore` параметр — `BuildingServices` (не `BuildingFactory`).
`BeltSegment` пока нужен для L3 `TransportNode`. Ванильный прототип бери через
`context.buildings().peek(BuildingType.BELT.contentId())`, не через `VanillaBuildings.frozen()`.

## Примеры в репозитории

См. [`examples/README.md`](../examples/README.md).

## Длинные гайды

- Полный моддинг: [`modding-guide.md`](modding-guide.md)
- Зачем реестры: [`why-registries.md`](why-registries.md)
- Справочник типов: [`reference.md`](reference.md)
- Дизайн миграции API (не контракт дня): [`design/api-v2.md`](design/api-v2.md)

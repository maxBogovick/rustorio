# examples/ — роли образцов

| Путь | Роль | Уровень |
|------|------|---------|
| **[`external-mod-template/`](external-mod-template/)** | **Канонический L2** для внешних модов. ContentDsl + SimpleCrafter. То же, что копирует `./gradlew initMod`. | **L2** |
| [`examplemod/`](examplemod/) | Фикстура acceptance-тестов: JSON + свой `TransportNode` (конвейер). Не стартовая точка. | L3 fixture |
| [`servicemod/`](servicemod/) | Фикстура: свой `Building` + `ServiceKey`. Образец сервиса, не L2. | L3 fixture |

In-repo игровые моды с кодом (не под `examples/`):

| Путь | Роль |
|------|------|
| `src/mods/webminer/` | L3: HTTP/`TickContext.service` |
| `resources/mods/petrochem/` (+ javasrc в гайде) | L3: нефтехимия / ElectroCracker |
| `resources/mods/rustorio/` | ванильный контент как мод |

Новый мод с поведением станка: **только** `external-mod-template` или `initMod`, плюс
[`docs/hello-building.md`](../docs/hello-building.md). Карта задач движка:
[`docs/start-here.md`](../docs/start-here.md).

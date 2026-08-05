# Справочник: поля файлов мода

Справочник не переписан прозой — он **генерируется из того же, что читает игра**, иначе он
устареет молча. Здесь только указатель, где что смотреть.

## Схемы JSON

Каждый тип контента описан схемой в [schemas/](schemas/):

| Файл мода | Схема |
|---|---|
| `mod.json` | [mod.schema.json](schemas/mod.schema.json) |
| `content/items/*.json` | [item.schema.json](schemas/item.schema.json) |
| `content/recipes/*.json` | [recipe.schema.json](schemas/recipe.schema.json) |
| `content/buildings/*.json` | [building.schema.json](schemas/building.schema.json) |
| `content/kinds/*.json` | [kind.schema.json](schemas/kind.schema.json) |
| `content/techs/*.json` | [tech.schema.json](schemas/tech.schema.json) |
| `content/maps/*.json` | [map.schema.json](schemas/map.schema.json) |

Схемы сверяются с реальным контентом тестом `ContentSchemaTest`: поле, которого нет в схеме, и
обязательное поле, которого нет в файлах, валят сборку. Поэтому расхождение справочника с кодом —
красный тест, а не тихо устаревший документ.

## Автодополнение в редакторе

VS Code умеет подсказывать поля и подчёркивать опечатки прямо в файле мода. Добавь в
`.vscode/settings.json` своего проекта:

```json
{
  "json.schemas": [
    { "fileMatch": ["**/content/items/*.json"],     "url": "./docs/schemas/item.schema.json" },
    { "fileMatch": ["**/content/recipes/*.json"],   "url": "./docs/schemas/recipe.schema.json" },
    { "fileMatch": ["**/content/buildings/*.json"], "url": "./docs/schemas/building.schema.json" },
    { "fileMatch": ["**/content/kinds/*.json"],     "url": "./docs/schemas/kind.schema.json" },
    { "fileMatch": ["**/content/techs/*.json"],     "url": "./docs/schemas/tech.schema.json" },
    { "fileMatch": ["**/content/maps/*.json"],      "url": "./docs/schemas/map.schema.json" },
    { "fileMatch": ["**/mod.json"],                 "url": "./docs/schemas/mod.schema.json" }
  ]
}
```

То же самое умеют IntelliJ IDEA (Settings → Languages & Frameworks → Schemas and DTDs → JSON
Schema Mappings) и любой редактор с поддержкой JSON Schema.

## Java API для jar-мода

Точка входа — `com.rustorio.api.mod.RustorioMod`. Смысл каждого метода и каждого события описан в
javadoc самих файлов; это и есть справочник по API:

- `com.rustorio.api.mod` — `RustorioMod`, `RegistrationContext`, `EventBus` и события;
- `com.rustorio.api.registry.Registry` — `register` / `update` / `remove` / `peek`;
- `com.rustorio.api.content.ContentId` — формат идентификатора.

Собрать HTML-версию:

```bash
./gradlew javadoc
```

## Рабочий пример

`examples/examplemod` — мод с данными **и** кодом: новая руда, рецепт, здание из JSON плюс
транспортный узел на Java. Он не музейный экспонат: `PhaseSevenAcceptanceTest` собирает его в
настоящий jar и гоняет в каждой сборке, поэтому он не может устареть незаметно.

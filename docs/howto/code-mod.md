# Как написать код-мод (jar)

Данными описывается новый **контент** (предмет, рецепт, здание на готовом архетипе). Новое
**поведение** — своя механика здания, реакция на события, правка чужого рецепта кодом — данными не
выражается: для него нужен мод с кодом, то есть `.jar` рядом с `mod.json`.

Весь путь ниже — не выдумка: он повторяет `examples/examplemod`, который компилируется в настоящий
jar и прогоняется через реальный загрузчик тестом `PhaseSevenAcceptanceTest` в **каждой сборке**.
Сломается пример — покраснеет `./gradlew build`, поэтому этот рецепт не может тихо устареть.

## Из чего состоит код-мод

Папка мода — та же, что у data-мода (`mod.json` + `content/`), плюс один `.jar`. Чтобы jar
подхватился, нужны **три** совпадающие вещи:

1. в `mod.json` — поле `entryPoint` с полным именем твоего класса;
2. класс, реализующий `com.rustorio.api.mod.RustorioMod`;
3. внутри jar — файл `META-INF/services/com.rustorio.api.mod.RustorioMod`, в котором лежит то же
   самое полное имя класса.

Jar грузится не по имени из `mod.json`, а через `ServiceLoader` — по этому service-файлу. Имя из
`mod.json` только **сверяется** с найденным, чтобы поймать рассинхрон. Забыть service-файл —
типичная первая ошибка (см. «Ловушки»).

## Шаг 1. `mod.json` с точкой входа

`examples/examplemod/mod.json`:

```json
{
  "id": "examplemod",
  "version": "1.0.0",
  "entryPoint": "com.examplemod.jarmod.ExampleModEntryPoint",
  "dependencies": [
    { "modId": "rustorio", "range": ">=1.0.0" }
  ]
}
```

Зависимость от `rustorio` здесь не для галочки: ниже мы ссылаемся на ванильный `iron_plate`, а
зависимость — единственное, что гарантирует, что `rustorio` загрузится **раньше** и его контент
будет виден.

## Шаг 2. Класс-точка входа

`RustorioMod` — интерфейс с четырьмя методами, все с пустой реализацией по умолчанию: переопределяй
только те, что нужны.

```java
package com.examplemod.jarmod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.domain.ItemType;

public final class ExampleModEntryPoint implements RustorioMod {

    @Override
    public void registerContent(RegistrationContext ctx) {
        ItemType ironPlate = ctx.items().peek(ContentId.of("rustorio:iron_plate")).orElseThrow(
                () -> new IllegalStateException("rustorio:iron_plate ещё не виден — порядок загрузки не тот"));
        // ... регистрируем своё здание/предмет/рецепт через ctx, используя ironPlate ...
    }
}
```

Полный рабочий вариант, который регистрирует настоящее новое здание с собственной Java-логикой, —
в `examples/examplemod/javasrc/ExampleModEntryPoint.java`.

Четыре метода, и все моды проходят их **раундами по всем сразу** (сначала `registerContent` у
всех, потом `modifyContent` у всех, потом `finalFixes`):

| Метод | Раунд | Для чего |
|---|---|---|
| `registerContent` | 1 | зарегистрировать свой новый контент |
| `modifyContent` | 2 | править **чужой** уже зарегистрированный контент — он к этому раунду точно есть |
| `finalFixes` | 3 | последние поправки перед заморозкой реестров |
| `subscribeEvents` | после заморозки | подписаться на игровые события |

Отсюда правило: ссылаешься на чужой контент — делай это в `modifyContent`, а не в `registerContent`,
где чужого ещё может не быть. Правку чужого рецепта см. в [patch-recipe.md](patch-recipe.md).

Через `RegistrationContext` доступны реестры `items()`, `buildings()`, `techs()`, `kinds()`,
`maps()`, `recipes()` — у каждого `register` / `update` / `remove`.

## Шаг 3. Собрать jar

Движок не собирает мод за тебя — jar делается своим инструментом. Минимум — `javac` + `jar` из JDK:

```bash
# 1. скомпилировать против классов движка (например, build/classes/java/main после ./gradlew build)
javac -cp build/classes/java/main -d build/mod javasrc/*.java

# 2. положить рядом объявление сервиса — с полным именем класса из mod.json
mkdir -p build/mod/META-INF/services
echo 'com.examplemod.jarmod.ExampleModEntryPoint' > build/mod/META-INF/services/com.rustorio.api.mod.RustorioMod

# 3. упаковать в <id>.jar — имя строго по id мода, и класть в папку мода
jar --create --file examplemod.jar -C build/mod .
```

Ровно эти три шага (компиляция → service-файл → упаковка) делает в коде `TestModJarBuilder` — если
захочешь автоматизировать сборку своего мода, это готовый образец.

## Шаг 4. Положить и проверить

Итоговая папка мода — внутри `resources/mods/`:

```
resources/mods/examplemod/
├── mod.json
├── examplemod.jar      ← имя строго <id>.jar
└── content/            ← JSON-контент код-мод тоже может нести
```

**Проверка.** Безоконный прогон:

```bash
./gradlew game
```

В сводке загрузки мод должен появиться (`Loaded N mod(s) [... examplemod 1.0.0 ...]`), а число
зданий/предметов — вырасти на то, что ты зарегистрировал. Если мода нет — ищи строку
`WARNING ... was skipped:`, в ней причина.

## Ловушки

Все сообщения ниже — реальные, их печатает загрузчик.

- **Нет service-файла.** `no META-INF/services/com.rustorio.api.mod.RustorioMod entry found`. Самая
  частая ошибка: класс есть, а объявления сервиса в jar нет.
- **`entryPoint` не совпал с классом.** `mod.json declares entryPoint '...' but ServiceLoader found
  '...'`. Полное имя в `mod.json` и в service-файле должно быть буквально одинаковым.
- **Две реализации `RustorioMod` в одном jar.** Отказ: мод объявляет ровно одну точку входа.
- **Jar назван не так или лежит не там.** Загрузчик ищет строго `<папка>/<id>.jar` — `mymod.jar` в
  папке `mymod/`. Иначе jar просто не найдётся.
- **Ссылка на чужой контент в `registerContent`.** Чужое к этому раунду может быть не
  зарегистрировано — `peek` вернёт пустой `Optional`. Объяви зависимость в `mod.json` и переноси
  такие ссылки в `modifyContent`.
- **Изоляция классов.** Каждый jar грузится своим загрузчиком (parent-last): класс другого мода не
  виден, даже с тем же именем. Наружу отдаются только `com.rustorio.api.*` и `com.rustorio.domain.*`
  — почему так, см. [why-registries.md](../why-registries.md#изоляция-модов).

## Подписка на события

События мод получает в `subscribeEvents` — после заморозки реестров. Шина адресуется по типу
события:

```java
@Override
public void subscribeEvents(EventBus events) {
    events.subscribe(BuildingPlaceEvent.class, e -> {
        if (isReserved(e.x(), e.y())) {
            e.cancel();   // отменить постановку — клик игрока ничего не сделает
        }
    });
}
```

`BuildingPlaceEvent` — единственное отменяемое событие: оно спрашивается только при действии
игрока, до коммита, и никогда при загрузке сейва. Полный список из шести событий — в README, раздел
«Что можно замоддить».

## Новое поведение здания

Здание с собственной механикой — это класс, реализующий `Building` и нужные capability-интерфейсы
(`TransportNode`, `SettlesEachTick` и т.п.), целиком **вне** `com.rustorio.domain.building`.
Образец — `examples/examplemod/javasrc/ExampleModConveyor.java`: транспортный узел, который
встраивается в ту же цепочку лент, что и ванильная лента, и переживает сохранение/загрузку. Его
прототип регистрируется в `registerContent` через `ctx.buildings().register(...)`.

## Что дальше

- Изменить или убрать чужой рецепт кодом — [patch-recipe.md](patch-recipe.md).
- Здание без Java, на готовом архетипе — [building.md](building.md).
- Полный список полей файлов — [../reference.md](../reference.md) и [../schemas/](../schemas/).
- Почему изоляция, реестры и три раунда устроены именно так — [../why-registries.md](../why-registries.md).

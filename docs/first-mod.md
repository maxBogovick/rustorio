# Свой первый мод за 20 минут

Здесь мы добавим в игру новую руду, плавящуюся в новый слиток. Ни строчки Java: только два
JSON-файла и один рецепт. Каждый шаг проверяется — если после шага ничего не изменилось,
дальше идти незачем.

Нужен только склонированный репозиторий и JDK не нужен: Gradle скачает его сам.

## Шаг 1. Папка мода

Мод — это папка внутри `resources/mods/`. Создай `resources/mods/mymod/` и положи туда `mod.json`:

```json
{
  "id": "mymod",
  "version": "1.0.0",
  "dependencies": [{ "modId": "rustorio", "range": ">=1.0.0" }]
}
```

`id` — это ещё и пространство имён всего твоего контента: предмет `ore` станет `mymod:ore`.
Зависимость от `rustorio` нужна, потому что ниже мы сошлёмся на ванильную печь.

**Проверка.** Запусти безоконный прогон:

```bash
./gradlew game
```

В первой строке лога должно появиться `Loaded 3 mod(s) [mymod 1.0.0, rustorio 1.0.0, sandbox 1.0.0]`.
Если мода нет в списке — смотри строку `WARNING ... was skipped:`, там написана причина.

## Шаг 2. Два предмета

`resources/mods/mymod/content/items/titanium_ore.json`:

```json
{
  "path": "titanium_ore",
  "label": "Titanium Ore",
  "colorRgb": "#8899aa",
  "shape": "CIRCLE"
}
```

`resources/mods/mymod/content/items/titanium_plate.json`:

```json
{
  "path": "titanium_plate",
  "label": "Titanium Plate",
  "colorRgb": "#ccd5e0",
  "shape": "SQUARE"
}
```

**Проверка.** `./gradlew game` — в сводке загрузки число предметов должно вырасти на два.

## Шаг 3. Рецепт

`resources/mods/mymod/content/recipes/titanium_plate.json`:

```json
{
  "ingredients": ["titanium_ore"],
  "output": "titanium_plate",
  "time": 7,
  "kind": "FURNACE"
}
```

Имя файла — это идентификатор рецепта: `mymod:titanium_plate`. Именно им другой мод сможет
изменить твой рецепт, не переписывая его.

`"kind": "FURNACE"` кладёт рецепт в общий пул ванильной печи, так что плавить будет обычная печь.
Ссылка `"titanium_ore"` без двоеточия означает «в моём же пространстве имён»; на чужое надо
ссылаться полностью — `"rustorio:iron_plate"`.

**Проверка.** `./gradlew game` — число рецептов выросло на один. Опечатайся нарочно
(`"output": "titanum_plate"`) и убедись, что игра не падает, а печатает, какой файл и какая ссылка
не разрешились.

## Шаг 4. Где взять руду

Предмет есть, а на карте его нет. Добавь карту с залежами — `content/maps/titanium_field.json`:

```json
{
  "path": "titanium_field",
  "label": "Titanium field",
  "orePatches": [
    { "ore": "titanium_ore", "cx": 20, "cy": 20, "radius": 4 },
    { "ore": "rustorio:coal", "cx": 26, "cy": 20, "radius": 3 }
  ]
}
```

Уголь рядом нужен потому, что ванильная печь топится углём.

**Проверка.** В окне игры новая карта появится в меню «New Game». Окно запускается так:

```bash
./gradlew run
```

## Что дальше

- Своя картинка предмета или здания — [howto/textures.md](howto/textures.md).
- Своё здание без Java — [howto/building.md](howto/building.md).
- Своя жидкость и жидкостная/электрическая машина — [howto/fluid.md](howto/fluid.md).
- Мод с кодом (свой jar, новое поведение, события) — [howto/code-mod.md](howto/code-mod.md).
- Изменить чужой рецепт — [howto/patch-recipe.md](howto/patch-recipe.md).
- Полный список полей каждого файла — [schemas/](schemas/), см. [reference.md](reference.md).
- Почему всё устроено через реестры и идентификаторы — [why-registries.md](why-registries.md).

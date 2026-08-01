---
paths:
  - "resources/mods/**"
  - "src/main/java/com/rustorio/mod/**"
  - "src/main/java/com/rustorio/api/**"
---

# Контент модов и загрузчик

## Ванильный мод — не свалка для проб

`resources/mods/rustorio` — это **ванильная игра, выраженная данными**. `VanillaAsModParityTest`
требует, чтобы содержимое этой папки совпадало число-в-число с тем, что регистрируют в Java
`VanillaItems`, `VanillaBuildings` и `RecipeBook.standard()`. Смысл теста: доказать, что API модов
достаточно выразителен, чтобы описать саму игру.

Отсюда правило: **добавить сюда предмет/здание/рецепт — значит одновременно добавить его в Java**,
иначе три теста паритета краснеют. Пробный контент («посмотреть, как работает редактор») кладётся в
отдельный мод, а не в `rustorio`.

Проверка после любой правки контента:

```bash
./gradlew test --tests '*VanillaAsModParityTest*'
```

## Идентификаторы и совместимость

- Всё именуется `namespace:id` (`rustorio:iron_ore`). Namespace — id мода, не выдумка.
- Id попадает в сейв игрока. Переименование = ломающее изменение; путь для него —
  `prototypeRenames` в `JsonSaveRepository`, а не молчаливая правка JSON.
- Подпись (`label`) — либо строка, либо `{en, ru}`. Резолв по локали на загрузке, fallback на id.

## Ошибки загрузчика адресуются мододелу

Сообщение об ошибке называет **мод, файл и поле**. «Invalid recipe» — плохо; «mod `foo`,
`content/recipes/bar.json`: field `output` refers to unknown item `foo:baz`» — то, что нужно.
Мододел не видит стек-трейса ядра и не читает его исходники.

## Границы

- Jackson в домене запрещён; JSON живёт в `com.rustorio.persistence` и `com.rustorio.mod`
  (проверяется `PackageBoundaryRulesTest`).
- Публичный API (`com.rustorio.api`) переименовать позже нельзя — имена обсуждаются с владельцем
  до, а не после.

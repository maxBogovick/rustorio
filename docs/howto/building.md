# Как добавить здание без Java

Здание описывается данными, если его поведение совпадает с одним из существующих архетипов.
Новое **поведение** данными выразить нельзя — для него нужен jar-мод.

## Двенадцать архетипов

`MINER`, `CHEST`, `FURNACE`, `BELT`, `SPLITTER`, `PRESS`, `UNDERGROUND_IN`, `UNDERGROUND_OUT`,
`LAB`, `FILTER`, `INSERTER`, `ASSEMBLER`.

Поле `archetype` называет, чью Java-логику здание одалживает. Всё остальное — цена, картинка,
размер, скорость, пул рецептов — твоё.

## Печь со своим пулом рецептов

`content/buildings/titanium_furnace.json`:

```json
{
  "path": "titanium_furnace",
  "label": { "en": "Titanium furnace", "ru": "Титановая печь" },
  "archetype": "FURNACE",
  "cost": { "item": "rustorio:iron_plate", "amount": 10 },
  "placement": "NEEDS_PASSABLE_TERRAIN",
  "texture": "mymod:titanium_furnace",
  "fuel": "rustorio:coal",
  "bufferMax": 20,
  "acceptsSpeedEffects": true
}
```

Поле `kind` не указано — значит у здания **свой приватный пул**, равный его собственному id
(`mymod:titanium_furnace`). Чтобы рецепт попал в этот пул, напиши в рецепте `"kind": "titanium_furnace"`.

Хочешь, наоборот, чтобы твоё здание варило ванильные рецепты печи — напиши `"kind": "FURNACE"`.

## Размер больше одной клетки

```json
"footprintWidth": 2,
"footprintHeight": 2
```

## Проверка

```bash
./gradlew game
```

Число зданий в сводке загрузки должно вырасти. Ошибку в поле `archetype` загрузчик назовёт вместе
с полным списком допустимых значений.

## Ограничение, о котором стоит знать заранее

Здание, которому нужно **новое поведение** (свои правила приёма предметов, своя механика), пишется
на Java и приезжает в jar-моде. Смотри `examples/examplemod` — там ровно такой случай: новый
транспортный узел, встраивающийся в цепочку лент.

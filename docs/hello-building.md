# Hello Building — своё здание на DSL за 15 минут

JSON даёт L0 (руда, рецепт, карта) — см. [first-mod.md](first-mod.md). Здесь **L2**:
станок «вход → N тиков → выход» без телескопного `BuildingPrototype` и без своего `Codec`.

Нужны JDK и склонированный репозиторий. Окно игры в этой инструкции не обязательно —
логику проверяет `./gradlew game` и тесты.

## Лестница моддинга (куда вы сейчас)

| Уровень | Чем | Этот гайд |
|--------|-----|-----------|
| L0 Data | только `content/**/*.json` | [first-mod.md](first-mod.md) |
| L1 Tune | JSON + `modifyContent` | часть 3 в [modding-guide.md](modding-guide.md) |
| **L2 Behavior** | **DSL + `SimpleCrafter`** | **вы здесь** |
| L3 Engine-touch | свой `Building`, сервисы | petrochem / webminer |

## Шаг 0. Каркас мода

Самый короткий путь — из корня движка:

```bash
./gradlew initMod -PmodId=amberworks
./gradlew publishToMavenLocal
cd examples/amberworks
# шаблон без gradlew — используйте системный Gradle или обёртку движка:
gradle installMod -PgameDir=../..
# либо: ../../gradlew -p . installMod -PgameDir=../..
```

Либо скопируйте [`examples/external-mod-template`](../examples/external-mod-template) вручную и
переименуйте `modId` — шаблон **тот же** L2 (DSL + SimpleCrafter), что пишет `initMod`.
Карта задач движка: [start-here.md](start-here.md).

## Шаг 1. Предметы и рецепт через `content()`

```java
@Override
public void registerContent(RegistrationContext ctx) {
    ctx.content().item("amber_ore")
            .label("Amber Ore").color(0xC9882A).shape(ItemShape.CIRCLE).register();
    ctx.content().item("amber_ingot")
            .label("Amber Ingot").color(0xE8B84A).shape(ItemShape.SQUARE).register();
    ctx.content().item("amber_polished")
            .label("Polished Amber").color(0xFFD27A)
            .shape(ItemShape.TRIANGLE).researchGrade().register();

    ctx.content().recipe("smelt_amber")
            .input("amber_ore").output("amber_ingot").time(8).inFurnace().register();
}
```

Имена без `:` получают namespace из `mod.json` (`amberworks:amber_ore`). На чужое —
полный id: `"rustorio:iron_plate"`.

`requireItem("amber_ore")` внутри того же раунда даёт понятную ошибку, если предмета ещё нет
(вместо загадки `peek` vs `get`).

## Шаг 2. Здание-батарейка

```java
ctx.content().building("polisher")
        .label("Amber Polisher")
        .cost("rustorio:iron_plate", 8)
        .placement("needs_passable_terrain")
        .texture(VanillaSprites.ASSEMBLER)
        .simpleCrafter("amber_ingot", "amber_polished", 15)
        .register();
```

Под капотом — `SimpleCrafter`: буфер входа → 15 тиков → held-выход → `offerForward`.
Сейв идёт через общий `SimpleCrafter.CODEC`; свой codec писать не нужно.

Опционально: `.powerDemand(10)`, `.speedTech(ContentId.of("…"))`, `.category(…)`.

## Шаг 3. Карта с залежами

```java
ctx.content().map("amber_cove")
        .ore("amber_ore", 18, 18, 5)
        .ore("rustorio:coal", 26, 18, 3)
        .register();
```

Или тот же JSON, что в first-mod — оба пути равноправны.

## Шаг 4. Проверка

```bash
./gradlew game
```

В сводке загрузки должны появиться ваши items / buildings / recipes / maps.
Опечатка в id предмета при `simpleCrafter` или `cost` всплывает при загрузке мода, а не
молча в тике.

## Куда импортировать типы (фаза 2)

Контракты здания для нового кода — из `com.rustorio.api.building` (**подтипы**-facade на
`domain.building`, не «те же типы»). Исключение: **`TickContext` всегда из
`com.rustorio.domain.building`** — иначе `tick`/`accept` станут перегрузкой и здание молча
не будет работать.

Модели контента (`ItemType`, `Recipe`, …) пока в `com.rustorio.domain`; каталог будущего
переезда — `com.rustorio.api.content.model`.

```java
import com.rustorio.api.building.Building;                 // facade
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.TickContext;           // НЕ facade
```

## Когда DSL мало

- несколько выходов, сеть, HTTP, свой UI страницы → свой класс `Building` (L3), см. petrochem /
  webminer и [modding-guide.md часть 3](modding-guide.md#часть-3--движок-в-коде-jar-мод);
- только другие цифры у чужого архетипа → `.archetype(BuildingType.FURNACE)` без SimpleCrafter;
- только данные → JSON, без Java.

Дизайн лестницы и фазы миграции (не ежедневный контракт): [design/api-v2.md](design/api-v2.md).
Стабильный индекс: [start-here.md](start-here.md).

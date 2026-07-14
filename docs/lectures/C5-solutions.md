# C5 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [C5-workbook.md](C5-workbook.md).

---

## П1 — Клавиши

- `Input.Keys.F1 + tech.ordinal()` — коды функциональных клавиш идут подряд.
- Никаких проверок «можно ли»: `research()` проверяет сам и возвращает `false`.
- Цикл по `Tech.values()`, а не лесенка `if` — иначе пятая технология окажется без клавиши,
  и компилятор промолчит (ровно та же ошибка, что была с инструментами до спринта 0).

---

## Р1 — Клавиши

```java
        // Открыть технологию: F1..F4 по списку Tech — снова НЕ лесенка из if'ов, а цикл
        // по данным. Добавится пятая технология — клавиша появится сама.
        for (Tech tech : Tech.values()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1 + tech.ordinal())) {
                game.research().research(tech); // сам проверит, можно ли: очки, предпосылки
            }
        }
```

---

## П2 — HUD

- Состояние технологии — три случая: `isUnlocked` → `OK`; `canResearch` → `ready`; иначе —
  показать **цену**.
- Строка собирается циклом по `Tech.values()` и `Technology.of(tech)`. Хардкод текста — это
  второй источник правды, который разойдётся с деревом при первой правке.

---

## Р2 — HUD

```java
        // Строка исследований: очки и состояние каждой технологии. Собирается из ДАННЫХ
        // (Tech.values() + таблица Technology), поэтому новая технология появится тут сама.
        var research = game.research();
        StringBuilder techs = new StringBuilder("Science: " + research.points() + "    ");
        for (Tech tech : Tech.values()) {
            var technology = Technology.of(tech);
            String state;
            if (research.isUnlocked(tech)) {
                state = "OK";
            } else if (research.canResearch(tech)) {
                state = "ready";
            } else {
                state = String.valueOf(technology.cost());
            }
            techs.append('F').append(tech.ordinal() + 1).append(' ')
                    .append(tech.displayName()).append(" [").append(state).append("]   ");
        }
        font.setColor(C_HINT);
        font.draw(batch, techs.toString(), 20, worldHeight - 66);
```

---

## П3 — Сквозной тест

- Меряйте **поведение**, а не поле: сколько тиков печь плавит руду.
- После замера освободите выход печи (`removeOutput()`), иначе второй замер не начнётся:
  ядро не работает, пока продукт не забрали.
- В сообщении об ошибке печатайте **оба числа** — иначе упавший тест ничего не скажет.

---

## Р3 — Сквозной тест

```java
    /** Сколько тиков печь плавит одну руду при данном балансе. */
    private static int ticksToSmelt(GameState game, World world, int x, int y) {
        Furnace furnace = (Furnace) world.tile(x, y).building();
        furnace.accept(Item.IRON_ORE);
        for (int tick = 1; tick <= 200; tick++) {
            game.update(Config.TICK);
            if (furnace.output().isPresent()) {
                furnace.removeOutput(); // освобождаем выход под следующий замер
                return tick;
            }
        }
        throw new AssertionError("печь так и не выдала пластину за 200 тиков");
    }

    @Test
    @DisplayName("«Быстрая печь» РЕАЛЬНО ускоряет плавку, а не просто зажигает галочку")
    void fastFurnaceActuallySpeedsUpSmelting() {
        World world = World.generate(6, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        world.place(2, 0, Building.create(Tool.FURNACE, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();
        for (int i = 0; i < 20; i++) {
            lab.accept(Item.GEAR);
        }
        GameState game = new GameState(world);

        int before = ticksToSmelt(game, world, 2, 0);

        play(game, 300); // лаборатория копит очки
        assertTrue(game.research().research(Tech.FAST_FURNACE), "очков должно хватить");

        int after = ticksToSmelt(game, world, 2, 0);

        assertTrue(after < before,
                "после апгрейда плавка обязана занимать МЕНЬШЕ тиков: было " + before
                        + ", стало " + after + ". Если числа равны — эффект никуда не применился");
    }

    @Test
    @DisplayName("В зданиях ради апгрейда не меняется НИ СТРОЧКИ: они уже читают Balance")
    void upgradeReachesMachinesThroughTickContext() {
        GameState game = gameWithLab(30);
        play(game, 300);

        float speedBefore = game.balance().speed(Tool.FURNACE);
        game.research().research(Tech.FAST_FURNACE);
        float speedAfter = game.balance().speed(Tool.FURNACE);

        assertEquals(1.0f, speedBefore, 1e-6f, "до апгрейда — базовая скорость");
        assertEquals(1.5f, speedAfter, 1e-6f,
                "печь получает Balance через TickContext и ускоряется САМА");
    }
```

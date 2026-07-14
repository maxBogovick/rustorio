# C4 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [C4-workbook.md](C4-workbook.md). Заходите сюда по
> ссылке из конкретного шага, а не читайте подряд.

---

## П1 — `Tech` и `Effect`

- `Tech` — обычный enum с человекочитаемым именем. **Больше в нём ничего нет**: цена и
  эффекты относятся к дереву, а не к имени.
- `Effect` — `sealed interface` с одним методом `applyTo(Balance)`, а его реализации —
  вложенные `record`. Sealed даёт то же, что и у зданий: закрытый набор + компилятор,
  требующий учесть все варианты, если где-то понадобится `switch`.
- Каждый вид эффекта — **одна строка** тела: дёрнуть соответствующий метод `Balance`.
- Эффекты живут в `core` — рядом с `Balance`, который они меняют.

---

## Р1 — `Tech` и `Effect`

```java
package com.rustorio.core;

/**
 * Технологии, которые можно открыть за очки исследований.
 *
 * <p>Это ИМЕНА, а не описания: «сколько стоит», «что требует», «что даёт» — всё это данные,
 * и живут они в таблице Technology.ALL. Добавить технологию = добавить сюда имя и одну строку
 * данных. Ни одного нового switch.
 */
public enum Tech {
    FAST_BELT("Быстрая лента"),
    FAST_FURNACE("Быстрая печь"),
    FAST_MINER("Быстрый бур"),
    LONG_UNDERGROUND("Длинная подземка");

    private final String displayName;

    Tech(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

```java
package com.rustorio.core;

/**
 * Что технология делает с игрой, когда её открыли.
 *
 * <p><b>Почему эффект — отдельный тип, а не «просто код».</b> Соблазн: написать в исследовании
 * switch (tech) { case FAST_BELT -> balance.setBeltSlotsPerTick(3); … }. Работает — ровно до
 * пятой технологии, после чего switch превращается в помойку, и каждая новая технология требует
 * правки КОДА, а не данных.
 *
 * <p>Обратите внимание: эффект меняет только Balance — то есть ИЗМЕНЯЕМЫЙ баланс, а не Config.
 * Ради этого их и разделили в спринте 0.
 */
public sealed interface Effect {

    /** Применить эффект к балансу игры. */
    void applyTo(Balance balance);

    /** Ускорить машину: factor = 1.5 — «работает в полтора раза быстрее». */
    record MachineSpeed(Tool machine, float factor) implements Effect {
        @Override
        public void applyTo(Balance balance) {
            balance.multiplySpeed(machine, factor);
        }
    }

    /** Разогнать ленты: сколько слотов предмет проезжает за тик. */
    record BeltSpeed(int slotsPerTick) implements Effect {
        @Override
        public void applyTo(Balance balance) {
            balance.setBeltSlotsPerTick(slotsPerTick);
        }
    }

    /** Удлинить подземную ленту: на сколько клеток она «ныряет». */
    record UndergroundReach(int tiles) implements Effect {
        @Override
        public void applyTo(Balance balance) {
            balance.setUndergroundReach(tiles);
        }
    }
}
```

---

## П2 — `Technology`

- `record Technology(Tech id, int cost, List<Tech> requires, List<Effect> effects)`.
- **Стоимость — в очках (`int`)**, а не в предметах: предметы уже съедены лабораторией.
- В компактном конструкторе скопируйте списки (`List.copyOf`) — данные не должны меняться.
- `BY_ID` — `EnumMap<Tech, Technology>` для поиска за O(1).
- `isAvailableWith(unlocked)` — это одна строка: `unlocked.containsAll(requires)`.

---

## Р2 — `Technology`

```java
public record Technology(Tech id, int cost, List<Tech> requires, List<Effect> effects) {

    public Technology {
        requires = List.copyOf(requires);
        effects = List.copyOf(effects);
    }

    /**
     * Дерево технологий. Единственное место, где оно описано.
     *
     * <pre>
     *   Быстрая лента (10) ──► Длинная подземка (25)
     *   Быстрая печь  (15) ──► Быстрый бур      (20)
     * </pre>
     */
    private static final List<Technology> ALL = List.of(
            new Technology(Tech.FAST_BELT, 10,
                    List.of(),
                    List.of(new Effect.BeltSpeed(3))),
            new Technology(Tech.FAST_FURNACE, 15,
                    List.of(),
                    List.of(new Effect.MachineSpeed(Tool.FURNACE, 1.5f))),
            new Technology(Tech.FAST_MINER, 20,
                    List.of(Tech.FAST_FURNACE),
                    List.of(new Effect.MachineSpeed(Tool.MINER, 1.5f))),
            new Technology(Tech.LONG_UNDERGROUND, 25,
                    List.of(Tech.FAST_BELT),
                    List.of(new Effect.UndergroundReach(6)))
    );

    private static final Map<Tech, Technology> BY_ID = ALL.stream()
            .collect(Collectors.toMap(
                    Technology::id, t -> t, (a, b) -> a, () -> new EnumMap<>(Tech.class)));

    public static List<Technology> all() {
        return ALL;
    }

    public static Technology of(Tech id) {
        return BY_ID.get(id);
    }

    /** Все ли предпосылки уже открыты? */
    public boolean isAvailableWith(Set<Tech> unlocked) {
        return unlocked.containsAll(requires);
    }
}
```

Javadoc про стоимость — **часть решения**:

```java
 * <p><b>Почему стоимость в ОЧКАХ, а не в предметах.</b> В первой редакции плана было написано
 * «cost: сколько каких предметов». Это ошибка: предметы уже потрачены — их съела Lab, превратив
 * в очки. Брать плату ещё и предметами значило бы взять деньги дважды за одно и то же. Очки —
 * единственная валюта прогрессии.
```

---

## П3 — `Research`

- Живёт в пакете `game`: это состояние **игры**, а не мира.
- Поля: `Balance balance`, `Set<Tech> unlocked = EnumSet.noneOf(Tech.class)`, `int points`.
- `collect(world)`: `world.forEachBuilding(...)`, и у каждой `Lab` — **`drainPoints()`**, а не
  геттер.
- `canResearch`: не открыта **И** предпосылки готовы **И** очков хватает.
- `research(tech)`: **сначала** `if (!canResearch(tech)) return false;` — проверка внутри, а
  не на совести вызывающего. Потом списать очки, добавить в `unlocked`, применить эффекты.
- `unlocked()` наружу отдавайте через `Collections.unmodifiableSet` — чтобы никто не открыл
  технологию, минуя правила.

---

## Р3 — `Research`

```java
public final class Research {

    private final Balance balance;
    private final Set<Tech> unlocked = EnumSet.noneOf(Tech.class);
    private int points;

    public Research(Balance balance) {
        this.balance = balance;
    }

    /** Забрать очки, накопленные всеми лабораториями мира. Зовётся раз в тик. */
    public void collect(World world) {
        world.forEachBuilding((x, y, building) -> {
            if (building instanceof Lab lab) {
                points += lab.drainPoints();
            }
        });
    }

    public int points() {
        return points;
    }

    public Set<Tech> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public boolean isUnlocked(Tech tech) {
        return unlocked.contains(tech);
    }

    /** Не открыта + предпосылки готовы + очков хватает. */
    public boolean canResearch(Tech tech) {
        Technology technology = Technology.of(tech);
        return !unlocked.contains(tech)
                && technology.isAvailableWith(unlocked)
                && points >= technology.cost();
    }

    /** Что игрок может открыть прямо сейчас (для интерфейса). */
    public List<Technology> available() {
        return Technology.all().stream()
                .filter(t -> canResearch(t.id()))
                .toList();
    }

    /**
     * Открыть технологию: списать очки и применить её эффекты.
     *
     * <p>Проверка canResearch стоит ВНУТРИ, а не «пусть вызывающий проверит сам»: иначе однажды
     * кто-то забудет проверить, и очки уйдут в минус — или технология откроется дважды, а её
     * эффект применится дважды (лента поедет вчетверо быстрее вместо вдвое).
     *
     * @return true, если открыли; false, если было нельзя
     */
    public boolean research(Tech tech) {
        if (!canResearch(tech)) {
            return false;
        }
        Technology technology = Technology.of(tech);
        points -= technology.cost();
        unlocked.add(tech);
        for (Effect effect : technology.effects()) {
            effect.applyTo(balance);
        }
        return true;
    }
}
```

---

## Р4 — Сбор очков в `GameState`

```java
    /** Прогресс исследований: очки из лабораторий и открытые технологии. */
    private final Research research = new Research(balance);

    public void update(float deltaTime) {
        if (paused) {
            return;
        }
        accumulator = Math.min(accumulator + deltaTime, Config.MAX_FRAME_TIME);
        TickContext ctx = new TickContext(Config.TICK, balance);
        while (accumulator >= Config.TICK) {
            accumulator -= Config.TICK;
            simulation.step(ctx);
            // Забираем очки из лабораторий сразу после шага мира: они уже начислены.
            research.collect(world);
        }
    }

    /** Прогресс исследований (его читает интерфейс, в нём же открывают технологии). */
    public Research research() {
        return research;
    }
```

---

## Р5 — Тесты

```java
    /** Игра с лабораторией на (0,0), которой скормили gears шестерёнок. */
    private static GameState gameWithLab(int gears) {
        World world = World.generate(4, 1);
        world.place(0, 0, Building.create(Tool.LAB, Direction.EAST));
        Lab lab = (Lab) world.tile(0, 0).building();
        for (int i = 0; i < gears; i++) {
            lab.accept(Item.GEAR);
        }
        return new GameState(world);
    }

    private static void play(GameState game, int ticks) {
        for (int i = 0; i < ticks; i++) {
            game.update(Config.TICK);
        }
    }

    @Test
    @DisplayName("Очки начисляются РОВНО один раз, сколько бы тиков ни прошло")
    void pointsAreNotCountedTwice() {
        GameState game = gameWithLab(1);
        play(game, 40);
        int afterFirst = game.research().points();
        play(game, 40); // ещё сорок тиков, но сырья больше нет

        assertEquals(afterFirst, game.research().points(),
                "лаборатория пуста — очкам взяться неоткуда");
    }

    @Test
    @DisplayName("Технологию с невыполненными предпосылками открыть нельзя")
    void cannotResearchWithoutPrerequisites() {
        // Один цикл лаборатории — 2 секунды, то есть ~12 тиков. Чтобы очков хватило и на
        // печь (15), и на бур (20), даём ей время переработать все шестерёнки.
        GameState game = gameWithLab(60);
        play(game, 800);

        assertFalse(game.research().canResearch(Tech.FAST_MINER),
                "предпосылка (быстрая печь) не открыта — нельзя");
        assertFalse(game.research().research(Tech.FAST_MINER), "и попытка обязана провалиться");

        assertTrue(game.research().research(Tech.FAST_FURNACE), "а вот печь открыть можно");
        assertTrue(game.research().canResearch(Tech.FAST_MINER),
                "теперь предпосылка выполнена — бур доступен");
    }

    @Test
    @DisplayName("Очки списываются РОВНО один раз, а технология не открывается дважды")
    void researchSpendsPointsExactlyOnce() {
        GameState game = gameWithLab(30);
        play(game, 300);
        int before = game.research().points();

        assertTrue(game.research().research(Tech.FAST_BELT), "первый раз — открыли");
        assertEquals(before - 10, game.research().points(), "списалась ровно стоимость");

        assertFalse(game.research().research(Tech.FAST_BELT),
                "второй раз — уже открыта, платить снова нельзя");
        assertEquals(before - 10, game.research().points(), "и очки не тронуты");
    }

    @Test
    @DisplayName("Открытая технология МЕНЯЕТ баланс игры")
    void researchAppliesItsEffect() {
        GameState game = gameWithLab(30);
        play(game, 300);

        Balance balance = game.balance();
        assertEquals(Config.BELT_SLOTS_PER_TICK, balance.beltSlotsPerTick(), "до апгрейда — база");

        assertTrue(game.research().research(Tech.FAST_BELT));

        assertEquals(3, balance.beltSlotsPerTick(),
                "«быстрая лента» обязана РЕАЛЬНО разогнать ленты, а не просто зажечь галочку");
    }
```

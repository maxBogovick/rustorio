# 📄 Решение — Р1 — `Simulation`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B2-workbook.md](../B2-workbook.md)

---

```java
public final class Simulation {

    private final World world;

    /**
     * Переиспользуемый буфер запланированных передач.
     *
     * <p>Живёт между тиками и очищается в начале каждого — вместо new ArrayList<>()
     * на каждом шаге. Одна «тарелка», которую моют, вместо новой одноразовой на каждый
     * обед.
     */
    private final List<Move> moves = new ArrayList<>();

    /** Номер текущего тика: им клетки помечаются как «уже занятые» (штамп версии). */
    private int tick;

    public Simulation(World world) {
        this.world = world;
    }

    /** ОДИН шаг симуляции = конвейер систем по порядку. */
    public void step(TickContext ctx) {
        tick++;
        runMachines(ctx);
        moveBelts(ctx);
        moveItems();
    }

    private void runMachines(TickContext ctx) {
        world.forEachBuilding((x, y, building) -> building.update(ctx));
    }

    private void moveBelts(TickContext ctx) {
        world.belts().step(ctx.balance().beltSlotsPerTick());
    }

    /** Запланированная передача предмета от здания-источника к приёмнику. */
    private record Move(Building source, Building target, Item item) {
    }
}
```

В `GameState`:

```java
    /** Симуляция — объект, а не статические методы: она владеет буферами (задача B2). */
    private final Simulation simulation;

    public GameState(World world) {
        this.world = world;
        this.simulation = new Simulation(world);
    }

    public void update(float deltaTime) {
        if (paused) {
            return;
        }
        accumulator = Math.min(accumulator + deltaTime, Config.MAX_FRAME_TIME);
        // Контекст создаётся ОДИН раз за тик, а не на каждое здание: все его поля
        // одинаковы для всех зданий в пределах шага.
        TickContext ctx = new TickContext(Config.TICK, balance);
        while (accumulator >= Config.TICK) {
            accumulator -= Config.TICK;
            simulation.step(ctx);
        }
    }
```

**Javadoc класса — обязательная часть решения** (размен называем вслух):

```java
/**
 * <p><b>Почему это ОБЪЕКТ, а не набор статических методов, как было раньше.</b>
 * Прошлая версия ({@code Systems}) была красива: класс без состояния, его невозможно
 * испортить. Но фаза передачи предметов каждый тик создавала заново список передач и
 * набор отметок «в эту клетку уже кладут». На большой фабрике это работа и мусор на
 * ровном месте — а главное, набор отметок размером «со весь мир» в чанковом мире просто
 * не построить: там нет общего числа клеток.
 *
 * <p>Поэтому симуляция стала объектом, который создаётся один раз и владеет своими
 * буферами. <b>Это честный размен: мы потеряли красоту «класса без состояния» и получили
 * отсутствие мусора.</b> В учебном проекте такие размены нельзя делать молча.
 */
```

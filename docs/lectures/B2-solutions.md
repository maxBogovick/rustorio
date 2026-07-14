# B2 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [B2-workbook.md](B2-workbook.md). Заходите по ссылке
> из конкретного шага.

---

## П1 — `Simulation`

- Класс перестаёт быть `final class Systems` с приватным конструктором и статическими
  методами. Становится `public final class Simulation` с конструктором
  `Simulation(World world)` — мир он держит полем, а не принимает в каждый метод.
- `GameState` создаёт его **один раз**, в своём конструкторе, и вызывает
  `simulation.step(ctx)` в цикле фиксированного тика.
- Список передач — поле. В начале `moveItems()`: `moves.clear()`.
- Заведите поле `int tick`, увеличивайте его в `step()` — оно понадобится на шаге 2.
- Файл `Systems.java` удаляется. Тесты и бенчмарк переводятся на `Simulation`.

---

## Р1 — `Simulation`

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

---

## П2 — Штамп версии

- Поле `int claimedTick = -1` живёт в `Tile` (это данные клетки).
- Метод `boolean claim(int tick)`: если `claimedTick == tick` — вернуть `false` (сосед
  успел раньше); иначе записать `tick` и вернуть `true`.
- Наружу это выходит через `World`: `claimNeighbor(x, y, dir, tick)` — «застолбить
  соседнюю клетку», с проверкой границ поля.
- **Важный порядок в `moveItems`:** сначала убедитесь, что приёмник существует и
  `canAccept`, и только **потом** столбите клетку. Застолбите раньше — и клетка окажется
  занята впустую, а другой сосед, который мог бы туда отдать, останется ни с чем.

---

## Р2 — Штамп версии

В `Tile`:

```java
    /**
     * Номер тика, на котором клетку уже «застолбил» отдающий сосед.
     *
     * <p><b>Приём «штамп версии».</b> Симуляции нужно за тик отметить: «в эту клетку уже
     * кладут предмет» — иначе две ленты пропихнули бы два предмета в один слот. Наивно
     * это делается массивом отметок, который каждый тик создают заново и обнуляют. Но
     * такого массива в чанковом мире не построить (нет общего числа клеток), да и
     * обнулять миллион ячеек каждый тик — работа на ровном месте.
     *
     * <p>Вместо этого клетка помнит НОМЕР ТИКА, на котором её заняли. Занята ⇔
     * claimedTick == текущий тик. Наступил новый тик — и все прошлые отметки
     * автоматически стали «старыми». Обнуление миллиона ячеек заменилось увеличением
     * одного числа.
     *
     * <p>Цена приёма, которую надо назвать вслух: в данных мира поселилось поле, нужное
     * только симуляции. Это осознанный размен — как «грязный флаг» в графических движках.
     */
    private int claimedTick = -1;

    /**
     * Попытаться застолбить клетку на этот тик.
     *
     * @return true, если удалось (клетку в этом тике ещё не занимали);
     *         false, если сосед успел раньше
     */
    boolean claim(int tick) {
        if (claimedTick == tick) {
            return false;
        }
        claimedTick = tick;
        return true;
    }
```

В `World`:

```java
    /**
     * Застолбить соседнюю клетку на этот тик: «в неё уже кладут предмет».
     *
     * @return true, если клетка досталась вам; false, если сосед успел раньше или
     *         клетки за краем поля не существует
     */
    public boolean claimNeighbor(int x, int y, Direction dir, int tick) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) && tiles[idx(nx, ny)].claim(tick);
    }
```

В `Simulation`:

```java
    private void moveItems() {
        moves.clear(); // очистить, а не создать заново

        // Фаза 1: планирование (только чтение).
        world.forEachBuilding((x, y, source) -> {
            Optional<Handoff> handoff = source.output();
            if (handoff.isEmpty()) {
                return;
            }
            Item item = handoff.get().item();
            Building receiver = world.neighborBuilding(x, y, handoff.get().direction());
            if (receiver == null || !receiver.canAccept(item)) {
                return;
            }
            // Столбим клетку ПОСЛЕДНЕЙ: если сосед успел раньше — передачи не будет,
            // и «занимать» её мы не имеем права.
            if (world.claimNeighbor(x, y, handoff.get().direction(), tick)) {
                moves.add(new Move(source, receiver, item));
            }
        });

        // Фаза 2: применение (можно менять).
        for (Move move : moves) {
            move.source().removeOutput();
            move.target().accept(move.item());
        }
    }
```

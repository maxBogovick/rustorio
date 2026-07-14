# B4 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [B4-workbook.md](B4-workbook.md).

---

## П1 — `Splitter`

- Буфер — одно поле `@Nullable Item item`. `canAccept` = «буфер пуст».
- `output()` — **всегда** `Optional.empty()`. Иначе общий механизм передач начнёт растаскивать
  предмет за спиной у вашей фазы.
- Выходы: `dir`, `right(dir)`, `left(dir)`. Правый — `dir.rotateCw()`; левый — трижды `rotateCw()`
  (обратного поворота у нас нет, и заводить его ради одного места не стоит).
- `outputsInOrder()` возвращает три направления, начиная с `nextOutput`, — это и есть «по кругу».
- `take(taken)`: `nextOutput = (индекс использованного + 1) % 3`. Именно **использованного**, а
  не «прошлого + 1», иначе после пропуска занятого круг собьётся.

---

## Р1 — `Splitter`

```java
public final class Splitter implements Building {

    private final Direction dir;
    /** Единственный предмет внутри (или null). */
    private @Nullable Item item;
    /** С какого выхода начинать в следующий раз — это и есть «по очереди». */
    private int nextOutput;

    /** Всегда пусто: предмет забирает фаза развилок (см. javadoc класса). */
    @Override
    public Optional<Handoff> output() {
        return Optional.empty();
    }

    @Override
    public boolean canAccept(Item incoming) {
        return item == null;
    }

    @Override
    public void accept(Item incoming) {
        this.item = incoming;
    }

    /** Предмет, ждущий отправки. */
    public Optional<Item> held() {
        return Optional.ofNullable(item);
    }

    /** Куда сплиттер пробует отдать — по кругу, начиная с текущего. */
    public List<Direction> outputsInOrder() {
        List<Direction> all = List.of(dir, right(dir), left(dir));
        return List.of(all.get(nextOutput),
                all.get((nextOutput + 1) % 3),
                all.get((nextOutput + 2) % 3));
    }

    /**
     * Предмет ушёл в направлении taken: убрать его и сдвинуть очередь.
     *
     * <p>Очередь сдвигается на выход, СЛЕДУЮЩИЙ за использованным, а не «на один вперёд от
     * прошлого». Иначе после пропуска занятого выхода круг сбивался бы, и поток перестал бы
     * делиться поровну.
     */
    public void take(Direction taken) {
        List<Direction> all = List.of(dir, right(dir), left(dir));
        nextOutput = (all.indexOf(taken) + 1) % 3;
        item = null;
    }

    private static Direction right(Direction d) {
        return d.rotateCw();
    }

    private static Direction left(Direction d) {
        return d.rotateCw().rotateCw().rotateCw();
    }
}
```

---

## П2 — Фаза развилок

- Идёт **после** общих передач (`moveItems`): сперва развилке что-то привозят, потом она
  раздаёт.
- Для каждого направления из `outputsInOrder()`: сосед есть? `canAccept`? застолбить
  (`claimNeighbor`)? — только тогда передать.
- Клетку столбим **последней**, после проверок: иначе она окажется занята впустую.
- Ни один выход не подошёл — **ничего не делаем**. Предмет остаётся, затор виден.

---

## Р2 — Фаза развилок

```java
    /**
     * Система: развилки раздают предметы.
     *
     * <p>Отдельная фаза, потому что общий протокол output() отдаёт ОДНО направление, а
     * сплиттеру нужно перебрать выходы по кругу и пропустить занятые — в одном и том же тике.
     * Здесь у нас есть мир, поэтому мы можем спросить каждого соседа по очереди.
     */
    private void moveSplitters() {
        world.forEachBuilding((x, y, building) -> {
            if (!(building instanceof Splitter splitter)) {
                return;
            }
            Optional<Item> held = splitter.held();
            if (held.isEmpty()) {
                return;
            }
            Item item = held.get();
            for (var dir : splitter.outputsInOrder()) {
                Building receiver = world.neighborBuilding(x, y, dir);
                if (receiver == null || !receiver.canAccept(item)) {
                    continue; // занят или там стена — пробуем следующий выход
                }
                if (world.claimNeighbor(x, y, dir)) {
                    receiver.accept(item);
                    splitter.take(dir);
                    return;
                }
            }
            // Все выходы заняты — предмет остаётся в сплиттере. Так и должно быть: затор виден.
        });
    }
```

---

## П3 — `UndergroundBelt`

- Два хранилища: `Deque<Transit> transit` (едут, у каждого свой `ticksLeft`) и
  `Deque<Item> arrived` (доехали, ждут соседа).
- `canAccept` = «я вход **и** в трубе есть место».
- `output()` отдаёт из `arrived` — то есть только **выход** что-то отдаёт.
- Время в пути: считайте **в слотах**, ровно как проехал бы предмет по земле:
  `slots = distance * SLOTS_PER_TILE + (SLOTS_PER_TILE - 1)` — от входного слота первой клетки
  до последнего слота клетки-выхода. Тики = округление вверх `slots / slotsPerTick`.
- `advance()`: уменьшить все таймеры, снять с головы всё, что доехало.

---

## Р3 — `UndergroundBelt`

```java
    /** Кто я в паре: вход, выход или одиночка (пары нет — значит, ничего не делаю). */
    public enum Role { NONE, ENTRANCE, EXIT }

    private Role role = Role.NONE;
    private int travelTicks = 1;
    private int capacity;

    private final Deque<Transit> transit = new ArrayDeque<>();
    private final Deque<Item> arrived = new ArrayDeque<>();

    /**
     * Симуляция сообщает подземке, кто она и как далеко её пара.
     *
     * @param distance расстояние до пары в клетках (для входа); 0 — если пары нет
     */
    public void setRole(Role role, int distance, int slotsPerTick) {
        this.role = role;
        if (role == Role.ENTRANCE) {
            // Ровно столько же слотов, сколько предмет проехал бы ПО ЗЕМЛЕ на том же отрезке:
            // от входного слота первой клетки до последнего слота клетки-выхода. Возьмёшь
            // меньше — подземка станет быстрее ленты, и вся игра уедет под землю.
            int slots = distance * Config.SLOTS_PER_TILE + (Config.SLOTS_PER_TILE - 1);
            this.travelTicks = Math.max(1, (slots + slotsPerTick - 1) / slotsPerTick);
            this.capacity = Math.max(1, distance);
        } else {
            this.capacity = 0;
        }
    }

    @Override
    public boolean canAccept(Item incoming) {
        return role == Role.ENTRANCE && transit.size() < capacity;
    }

    @Override
    public void accept(Item incoming) {
        transit.add(new Transit(incoming, travelTicks));
    }

    /** Отдаёт наружу только ВЫХОД, и только то, что уже доехало. */
    @Override
    public Optional<Handoff> output() {
        Item item = arrived.peek();
        return item == null ? Optional.empty() : Optional.of(new Handoff(item, dir));
    }

    /**
     * Продвинуть предметы под землёй на тик.
     *
     * @return предметы, доехавшие до конца (их симуляция передаст выходу)
     */
    public List<Item> advance() {
        List<Item> done = new ArrayList<>();
        for (Transit t : transit) {
            t.ticksLeft--;
        }
        while (!transit.isEmpty() && transit.peek().ticksLeft <= 0) {
            done.add(transit.poll().item);
        }
        return done;
    }

    /** Предмет доехал: положить его на выход. */
    public void deliver(Item item) {
        arrived.add(item);
    }
```

---

## П4 — Фаза подземок

- Идёт **до** общих передач: роль должна быть назначена раньше, чем кто-то спросит
  `canAccept`.
- Поиск пары: шагать вперёд до `reach` клеток; первая встреченная подземка **того же
  направления** — это пара.
- Найдена → я `ENTRANCE`, она `EXIT`. Не найдена → `NONE` (и я ничего не принимаю).
- Доехавшие предметы отдать выходу через `deliver`.

---

## Р4 — Фаза подземок

```java
    /**
     * Система: предметы едут ПОД ЗЕМЛЁЙ.
     *
     * <p>Роль подземки (вход/выход) — свойство МЕСТА, а не здания: она зависит от того, стоит
     * ли поблизости пара. Здание своих координат не знает, поэтому пару ищет симуляция — она
     * видит мир — и каждый тик сообщает подземке её роль.
     */
    private void moveUnderground(TickContext ctx) {
        int reach = ctx.balance().undergroundReach();
        int slots = ctx.balance().beltSlotsPerTick();

        world.forEachBuilding((x, y, building) -> {
            if (!(building instanceof UndergroundBelt entry)) {
                return;
            }
            int distance = findPartner(x, y, entry.dir(), reach);
            if (distance == 0) {
                entry.setRole(UndergroundBelt.Role.NONE, 0, slots);
                return;
            }
            entry.setRole(UndergroundBelt.Role.ENTRANCE, distance, slots);

            UndergroundBelt exit = (UndergroundBelt) world.tile(
                    x + entry.dir().dx() * distance, y + entry.dir().dy() * distance).building();
            exit.setRole(UndergroundBelt.Role.EXIT, 0, slots);

            for (Item item : entry.advance()) {
                exit.deliver(item);
            }
        });
    }

    /**
     * Найти пару подземки: ближайшую подземку того же направления впереди.
     *
     * @return расстояние в клетках, либо 0, если пары нет
     */
    private int findPartner(int x, int y, Direction dir, int reach) {
        for (int step = 1; step <= reach; step++) {
            int nx = x + dir.dx() * step;
            int ny = y + dir.dy() * step;
            if (!world.inBounds(nx, ny)) {
                return 0;
            }
            if (world.tile(nx, ny).building() instanceof UndergroundBelt other
                    && other.dir() == dir) {
                return step;
            }
        }
        return 0;
    }
```

Порядок фаз в `step()`:

```java
    public void step(TickContext ctx) {
        world.beginTick();   // счётчик штампов живёт в мире — там же, где сами штампы
        runMachines(ctx);
        moveBelts(ctx);
        moveUnderground(ctx);   // роли назначены ДО передач
        moveItems();
        moveSplitters();        // развилки раздают ПОСЛЕ того, как им привезли
    }
```

---

## Р5 — Исправление дефекта из B2 (счётчик тиков)

Тесты развилки поймали дефект **в задаче B2**: счётчик тиков жил в `Simulation`, а штампы — на
клетках мира. Новая симуляция над тем же миром начинала счёт с единицы и натыкалась на старые
штампы.

```java
// World
    /**
     * Номер текущего тика — им клетки метятся как «уже занятые» (штамп версии, см. Tile).
     *
     * <p><b>Почему счётчик живёт ЗДЕСЬ, а не в симуляции.</b> Сначала он был полем Simulation —
     * и это оказалось ловушкой: новая симуляция над тем же миром начинала счёт заново, с
     * единицы, и натыкалась на штампы, оставшиеся от прошлой. Клетки выглядели «уже занятыми»,
     * передачи молча не происходили. Вывод общий: <b>счётчик и штампы, которые им метятся,
     * обязаны жить в одном месте.</b>
     */
    private int tick;

    /** Начать новый тик: все прошлые «застолблённые» клетки автоматически освобождаются. */
    public void beginTick() {
        tick++;
    }

    public boolean claimNeighbor(int x, int y, Direction dir) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) && tile(nx, ny).claim(tick);
    }
```

---

## Р6 — Тесты

```java
    @Test
    @DisplayName("Развилка раздаёт предметы по очереди, а не валит в один выход")
    void splitterAlternatesBetweenOutputs() {
        World world = World.generate(5, 5);
        world.place(1, 1, Building.create(Tool.SPLITTER, Direction.EAST));
        world.place(2, 1, Building.create(Tool.CHEST, Direction.EAST)); // прямо
        world.place(1, 2, Building.create(Tool.CHEST, Direction.EAST)); // направо (юг)
        world.place(1, 0, Building.create(Tool.CHEST, Direction.EAST)); // налево (север)

        Splitter splitter = (Splitter) world.tile(1, 1).building();
        for (int i = 0; i < 6; i++) {
            splitter.accept(Item.IRON_ORE);
            stepTimes(world, 1);
        }

        int east = ((Chest) world.tile(2, 1).building()).items();
        int south = ((Chest) world.tile(1, 2).building()).items();
        int north = ((Chest) world.tile(1, 0).building()).items();

        assertEquals(6, east + south + north, "ни один предмет не потерялся");
        assertEquals(2, east, "поток разделился поровну между тремя выходами");
        assertEquals(2, south);
        assertEquals(2, north);
    }

    @Test
    @DisplayName("Подземка НЕ быстрее обычной ленты — иначе вся игра уехала бы под землю")
    void undergroundIsNotFasterThanBelt() {
        // Земля: 5 клеток ленты (0..4), ящик на 5.
        World ground = World.generate(10, 1);
        for (int x = 0; x <= 4; x++) {
            ground.place(x, 0, Building.create(Tool.BELT, Direction.EAST));
        }
        ground.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));
        ((Belt) ground.tile(0, 0).building()).accept(Item.IRON_ORE);

        // Подземка: вход на 0, выход на 4, ящик на 5 — тот же отрезок.
        World under = World.generate(10, 1);
        under.place(0, 0, Building.create(Tool.UNDERGROUND, Direction.EAST));
        under.place(4, 0, Building.create(Tool.UNDERGROUND, Direction.EAST));
        under.place(5, 0, Building.create(Tool.CHEST, Direction.EAST));
        stepTimes(under, 1);
        ((UndergroundBelt) under.tile(0, 0).building()).accept(Item.IRON_ORE);

        int groundTicks = ticksUntilDelivered(ground, 5, 0);
        int underTicks = ticksUntilDelivered(under, 5, 0);

        assertTrue(underTicks >= groundTicks,
                "подземка (" + underTicks + " тиков) не должна обгонять ленту ("
                        + groundTicks + " тиков): она удобство, а не читерство");
    }
```

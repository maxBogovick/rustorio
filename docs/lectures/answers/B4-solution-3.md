# 📄 Решение — Р3 — `UndergroundBelt`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B4-workbook.md](../B4-workbook.md)

---

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

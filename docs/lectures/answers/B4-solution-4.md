# 📄 Решение — Р4 — Фаза подземок

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B4-workbook.md](../B4-workbook.md)

---

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

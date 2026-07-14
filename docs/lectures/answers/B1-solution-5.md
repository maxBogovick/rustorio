# 📄 Решение — Р5 — Тесты

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B1-workbook.md](../B1-workbook.md)

---

```java
    @Test
    @DisplayName("Пустой мир не создаёт ни одного куска")
    void emptyWorldAllocatesNothing() {
        World world = World.generate(250, 250);
        assertSame(0, world.chunkCount(), "пока в мир не заглянули — он ничего не занимает");

        world.tile(0, 0);
        assertSame(1, world.chunkCount(), "заглянули в один угол — создался один кусок");

        world.tile(200, 200);
        assertSame(2, world.chunkCount(), "далёкая клетка — ещё один кусок, а не весь мир");
    }

    @Test
    @DisplayName("Клетка далеко от начала координат работает как любая другая")
    void farAwayCellBehavesNormally() {
        World world = World.generate(250, 250);
        world.place(200, 200, Building.create(Tool.CHEST, Direction.EAST));

        assertInstanceOf(Chest.class, world.tile(200, 200).building());
        assertNull(world.tile(199, 200).building(), "соседняя клетка пуста");
    }
```

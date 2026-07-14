# 📄 Решение — Р1 — Сцена

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C1-workbook.md](../C1-workbook.md)

---

```java
    /** Размер поля. 250×250 = 62 500 клеток — хватает под 50 000 зданий. */
    private static final int SIZE = 250;

    private static World buildFactory() {
        World world = World.generate(SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                // Стоков (ящиков) намеренно НЕТ: ленты упираются в тупик и остаются
                // забитыми. Это худший случай — максимум предметов, которые надо
                // двигать каждый тик. Поставь ящики — они бы всё выпили, и замер
                // показал бы почти пустой мир.
                Tool tool = switch (x % 25) {
                    case 0 -> Tool.MINER;      // источник в начале ряда
                    case 12 -> Tool.FURNACE;   // передел посередине
                    default -> Tool.BELT;
                };
                world.place(x, y, Building.create(tool, Direction.EAST));
            }
        }
        // Набиваем ленты грузом. Без этого замер врёт: стоимость движения зависит от
        // ЧИСЛА ПРЕДМЕТОВ, а если ждать, пока буры сами наполнят фабрику, мы измерим
        // почти пустой мир и обрадуемся зря.
        world.forEachBuilding((x, y, building) -> {
            if (building instanceof Belt belt && belt.canAccept(Item.IRON_ORE)) {
                belt.accept(Item.IRON_ORE);
            }
        });
        return world;
    }
```

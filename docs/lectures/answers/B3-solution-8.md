# 📄 Решение — Р8 — Подключение к игре

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

### `World`

```java
    /** Транспортные линии мира: их сборку и разборку ведёт только она. */
    private final BeltNetwork belts = new BeltNetwork(this);

    public BeltNetwork belts() { return belts; }

    public void place(int x, int y, Building building) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tiles[idx(x, y)];
        Building existing = tile.building();
        if (existing != null) {
            if (existing.getClass() != building.getClass()) {
                return;                                  // другой тип — не трогаем
            }
            if (existing.sameKind(building)) {
                return;                                  // ровно такое же — не пересоздаём
            }
            // Тот же тип, другое направление: старую ленту сперва честно вынимаем из её
            // линии (иначе линия осталась бы ссылаться на снесённую клетку).
            if (existing instanceof Belt oldBelt) {
                belts.onBeltRemoved(new Cell(x, y), oldBelt);
            }
        }
        tile.setBuilding(building);
        if (building instanceof Belt newBelt) {
            belts.onBeltPlaced(new Cell(x, y), newBelt);
        }
    }

    public void remove(int x, int y) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tiles[idx(x, y)];
        // Снимаем ленту с линии ДО того, как убрать её с клетки: разрезу нужна и сама
        // лента (её место в линии), и её соседи на своих местах.
        if (tile.building() instanceof Belt belt) {
            belts.onBeltRemoved(new Cell(x, y), belt);
        }
        tile.setBuilding(null);
    }
```

### `Systems`

```java
    public static void step(World world, float dt) {
        runMachines(world, dt);
        moveBelts(world);      // ← новая фаза
        moveItems(world);
    }

    /**
     * Система №1.5: предметы едут ВНУТРИ транспортных линий.
     *
     * <p>Отдельная фаза, потому что лента больше не «здание с одним предметом»:
     * непрерывная цепочка — одна линия, и двигать её надо целиком, с головы. Стык
     * «машина ↔ лента» при этом остался прежним и обслуживается moveItems — поэтому
     * бур, печь и сборщик не знают, что ленты переписаны.
     */
    private static void moveBelts(World world) {
        world.belts().step(Config.BELT_SLOTS_PER_TICK);
    }
```

### `GameState`

```java
    /**
     * Доля прожитого тика (0..1) — нужна ТОЛЬКО отрисовке.
     *
     * <p>Симуляция шагает раз в Config.TICK (пять с половиной раз в секунду), а кадров
     * рисуется шестьдесят. Без этого числа предмет на ленте дёргался бы скачками; с ним
     * рендер показывает его между прошлой и текущей позицией — и движение становится
     * плавным, хотя мир по-прежнему думает целыми тиками.
     */
    public float tickAlpha() {
        return Math.min(accumulator / Config.TICK, 1f);
    }
```

### `Renderer`

В `switch` по зданиям ветка ленты становится пустой:

```java
                    // Предметы на лентах рисуются НЕ здесь: они больше не принадлежат
                    // клетке, а едут внутри линии — см. drawBeltItems().
                    case Belt _ -> { }
```

И появляется отдельный проход:

```java
    /**
     * Предметы, едущие по транспортным линиям.
     *
     * <p><b>Здесь и появляется плавность.</b> Модель считает целыми слотами и шагает пять
     * раз в секунду; alpha — доля прожитого тика — говорит, насколько предмет уже уехал от
     * прошлой позиции к текущей. Дробные координаты существуют только тут, в отрисовке:
     * симуляция остаётся целочисленной и воспроизводимой.
     */
    private void drawBeltItems(World world, float alpha) {
        float size = TILE * 0.42f;
        for (BeltSegment segment : world.belts().segments()) {
            for (BeltItemPos pos : segment.itemPositions(alpha)) {
                // Целая координата — это ЦЕНТР клетки, поэтому +0.5 клетки.
                float cx = Config.OFFSET_X + (pos.x() + 0.5f) * TILE;
                float cy = worldHeight - Config.OFFSET_Y - (pos.y() + 0.5f) * TILE;
                batch.draw(textures.itemTexture(pos.item()),
                        cx - size / 2f, cy - size / 2f, size, size);
            }
        }
    }
```

Вызывается из `drawItemsAndText`, внутри уже открытого `batch.begin()`:

```java
        drawBeltItems(world, game.tickAlpha());
        drawHud(game);
        batch.end();
```

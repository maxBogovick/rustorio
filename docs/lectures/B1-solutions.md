# B1 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [B1-workbook.md](B1-workbook.md).

---

## П1 — `OreMap`

- Залежи те же, что были в `World.generate()`: круги `(центр_x, центр_y, радиус)`.
- Проверка попадания в круг — без корней: `dx*dx + dy*dy <= r*r`.
- Класс пакетно-приватный и без состояния (приватный конструктор).

---

## Р1 — `OreMap`

```java
/**
 * Где в мире лежит руда.
 *
 * <p><b>Почему это ФУНКЦИЯ, а не массив.</b> Раньше руда раскладывалась в массив при создании
 * мира: прошлись по всем клеткам, отметили залежи. С чанками так нельзя — клетки создаются по
 * мере надобности, и «пройтись по всем» уже не получится. Значит, вопрос «есть ли руда в клетке
 * (x, y)?» должен иметь ответ БЕЗ создания мира.
 *
 * <p>Функция детерминирована: одна и та же клетка всегда даёт один и тот же ответ, в каком бы
 * порядке ни создавались куски. Без этого свойства не будет ни сохранений, ни воспроизводимых
 * тестов.
 */
final class OreMap {

    /** Круглые залежи: (центр_x, центр_y, радиус). */
    private static final int[][] PATCHES = {{6, 5, 3}, {9, 14, 3}, {25, 6, 4}, {28, 15, 3}};

    private OreMap() {
    }

    static boolean hasOre(int x, int y) {
        for (int[] patch : PATCHES) {
            int dx = x - patch[0];
            int dy = y - patch[1];
            int r = patch[2];
            if (dx * dx + dy * dy <= r * r) {
                return true;
            }
        }
        return false;
    }
}
```

---

## П2 — `Chunk`

- Конструктор принимает координаты **куска** (не клетки) и сам создаёт свои 1024 клетки,
  спрашивая у `OreMap` руду по **мировым** координатам:
  `worldX = (chunkX << SHIFT) + localX`.
- `tile(localX, localY)` — локальные координаты 0..31, индекс `localY * SIZE + localX`.
- Класс пакетно-приватный: наружу устройство мира не торчит.

---

## Р2 — `Chunk`

```java
final class Chunk {

    /** Сторона куска в клетках. */
    static final int SIZE = 32;
    /** Маска для остатка: x & MASK == x % 32, но быстрее. */
    static final int MASK = SIZE - 1;
    /** Сдвиг для деления: x >> SHIFT == x / 32, но быстрее. */
    static final int SHIFT = 5;

    private final Tile[] tiles = new Tile[SIZE * SIZE];

    Chunk(int chunkX, int chunkY) {
        for (int ly = 0; ly < SIZE; ly++) {
            for (int lx = 0; lx < SIZE; lx++) {
                int worldX = (chunkX << SHIFT) + lx;
                int worldY = (chunkY << SHIFT) + ly;
                tiles[ly * SIZE + lx] = new Tile(OreMap.hasOre(worldX, worldY));
            }
        }
    }

    /** Клетка по ЛОКАЛЬНЫМ координатам внутри куска (0..31). */
    Tile tile(int localX, int localY) {
        return tiles[localY * SIZE + localX];
    }
}
```

---

## П3 — `World`

- `Map<Long, Chunk> chunks = new HashMap<>()`.
- `tile(x, y)` → `chunk(x >> SHIFT, y >> SHIFT).tile(x & MASK, y & MASK)`.
- `chunk(...)` → `chunks.computeIfAbsent(key, _ -> new Chunk(...))` — создание по требованию.
- Ключ: `((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL)`. **Маска обязательна**: без неё
  отрицательный `chunkY` при расширении до `long` зальёт единицами старшую половину и
  испортит ключ.
- `generate()` больше ничего не раскладывает — просто создаёт пустой мир.

---

## Р3 — `World`

```java
    private final Map<Long, Chunk> chunks = new HashMap<>();

    /**
     * Создать поле. Руда больше не «раскладывается» — она задана функцией от координат
     * (OreMap), потому что клетки теперь создаются по мере надобности.
     */
    public static World generate(int width, int height) {
        return new World(width, height);
    }

    /**
     * Прочитать клетку. Вызывающий обязан заранее проверить inBounds.
     *
     * <p>Кусок, в котором лежит клетка, создаётся здесь же, если его ещё нет.
     */
    public Tile tile(int x, int y) {
        return chunk(x >> Chunk.SHIFT, y >> Chunk.SHIFT)
                .tile(x & Chunk.MASK, y & Chunk.MASK);
    }

    private Chunk chunk(int chunkX, int chunkY) {
        return chunks.computeIfAbsent(key(chunkX, chunkY), _ -> new Chunk(chunkX, chunkY));
    }

    /** Упаковать координаты куска в один long — это и есть ключ карты. */
    private static long key(int chunkX, int chunkY) {
        return ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
    }

    /** Сколько кусков реально создано (для тестов: пустой мир не должен их плодить). */
    int chunkCount() {
        return chunks.size();
    }
```

---

## П4 — Обход

- Считаем размер сетки кусков: `chunksX = (width + SIZE - 1) >> SHIFT` (округление вверх).
- Идём `for cy … for cx …` и **спрашиваем** карту: `chunks.get(key(cx, cy))`. Нет куска —
  пропускаем целиком.
- Внутри куска — по локальным координатам; наружу отдаём **мировые**:
  `(cx << SHIFT) + lx`.
- **Не** обходите `chunks.values()`: порядок `HashMap` произволен, а симуляция обязана быть
  воспроизводимой.

---

## Р4 — Обход

```java
    /**
     * Обойти все здания мира.
     *
     * <p><b>Обход идёт по СЕТКЕ кусков, а не по карте.</b> Порядок обхода HashMap произволен и
     * может меняться между запусками — а симуляция обязана быть воспроизводимой. Поэтому мы
     * идём по координатам кусков, а карту только спрашиваем.
     *
     * <p>Пустые куски пропускаются целиком: на пустом мире обход почти бесплатен.
     */
    public void forEachBuilding(BuildingVisitor visitor) {
        int chunksX = (width + Chunk.SIZE - 1) >> Chunk.SHIFT;
        int chunksY = (height + Chunk.SIZE - 1) >> Chunk.SHIFT;
        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {
                Chunk chunk = chunks.get(key(cx, cy));
                if (chunk == null) {
                    continue; // в этот кусок ещё никто не заглядывал — зданий там нет
                }
                for (int ly = 0; ly < Chunk.SIZE; ly++) {
                    for (int lx = 0; lx < Chunk.SIZE; lx++) {
                        Building building = chunk.tile(lx, ly).building();
                        if (building != null) {
                            visitor.visit((cx << Chunk.SHIFT) + lx, (cy << Chunk.SHIFT) + ly,
                                    building);
                        }
                    }
                }
            }
        }
    }
```

---

## Р5 — Тесты

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

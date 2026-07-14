# 📄 Решение — Р2 — `Chunk`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B1-workbook.md](../B1-workbook.md)

---

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

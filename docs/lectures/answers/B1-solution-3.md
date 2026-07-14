# 📄 Решение — Р3 — `World`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B1-workbook.md](../B1-workbook.md)

---

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

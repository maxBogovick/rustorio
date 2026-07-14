# 📄 Решение — Р4 — Обход

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B1-workbook.md](../B1-workbook.md)

---

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

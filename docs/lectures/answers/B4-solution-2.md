# 📄 Решение — Р2 — Фаза развилок

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B4-workbook.md](../B4-workbook.md)

---

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

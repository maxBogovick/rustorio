# 📄 Решение — Р4 — Движение, инварианты, печать

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

```java
    /**
     * Сдвинуть все предметы на один тик.
     *
     * <p>Идём С ГОЛОВЫ: передний едет первым, и его новая позиция становится потолком
     * для следующего. Так предметы физически не могут обогнать друг друга — без единой
     * проверки «а не наехал ли я».
     */
    void step(int slotsPerTick) {
        int limit = lengthSlots() - 1;         // дальше головы не уехать: там линия кончается
        for (BeltItem it : items) {
            it.prevSlot = it.slot;
            it.slot = Math.min(it.slot + slotsPerTick, limit);
            limit = it.slot - 1;               // следующий встанет в лучшем случае впритык
        }
    }

    /** Предмет, доехавший до последнего слота: его и заберёт машина за головой. */
    Optional<Item> headItem() {
        if (items.isEmpty()) {
            return Optional.empty();
        }
        BeltItem front = items.getFirst();
        return front.slot == lengthSlots() - 1 ? Optional.of(front.item) : Optional.empty();
    }

    void removeHeadItem() {
        if (headItem().isPresent()) {
            items.removeFirst();
        }
    }

    /** «Линия не сошла с ума». Вызывать в тестах после КАЖДОЙ операции. */
    void assertInvariants() {
        if (tiles.isEmpty()) {
            throw new IllegalStateException("сегмент без клеток");
        }
        for (int i = 1; i < tiles.size(); i++) {          // клетки идут подряд в направлении dir
            Cell prev = tiles.get(i - 1);
            Cell cur = tiles.get(i);
            if (cur.x() != prev.x() + dir.dx() || cur.y() != prev.y() + dir.dy()) {
                throw new IllegalStateException("клетки сегмента не подряд: " + tiles);
            }
        }
        int last = Integer.MAX_VALUE;
        for (BeltItem it : items) {
            if (it.slot < 0 || it.slot >= lengthSlots()) {
                throw new IllegalStateException("слот вне линии: " + it.slot);
            }
            if (it.slot >= last) {
                throw new IllegalStateException(
                        "порядок нарушен или два предмета в одном слоте: " + this);
            }
            last = it.slot;
        }
    }

    /** Печать вида [.o.O] E — «картинка» линии, незаменима при отладке. */
    @Override
    public String toString() {
        char[] slots = new char[lengthSlots()];
        Arrays.fill(slots, '.');
        for (BeltItem it : items) {
            slots[it.slot] = it.slot == lengthSlots() - 1 ? 'O' : 'o';
        }
        return "[" + new String(slots) + "] " + dir.shortName() + " " + tiles.getFirst();
    }
```

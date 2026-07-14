# 📄 Решение — Р1 — `Splitter`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B4-workbook.md](../B4-workbook.md)

---

```java
public final class Splitter implements Building {

    private final Direction dir;
    /** Единственный предмет внутри (или null). */
    private @Nullable Item item;
    /** С какого выхода начинать в следующий раз — это и есть «по очереди». */
    private int nextOutput;

    /** Всегда пусто: предмет забирает фаза развилок (см. javadoc класса). */
    @Override
    public Optional<Handoff> output() {
        return Optional.empty();
    }

    @Override
    public boolean canAccept(Item incoming) {
        return item == null;
    }

    @Override
    public void accept(Item incoming) {
        this.item = incoming;
    }

    /** Предмет, ждущий отправки. */
    public Optional<Item> held() {
        return Optional.ofNullable(item);
    }

    /** Куда сплиттер пробует отдать — по кругу, начиная с текущего. */
    public List<Direction> outputsInOrder() {
        List<Direction> all = List.of(dir, right(dir), left(dir));
        return List.of(all.get(nextOutput),
                all.get((nextOutput + 1) % 3),
                all.get((nextOutput + 2) % 3));
    }

    /**
     * Предмет ушёл в направлении taken: убрать его и сдвинуть очередь.
     *
     * <p>Очередь сдвигается на выход, СЛЕДУЮЩИЙ за использованным, а не «на один вперёд от
     * прошлого». Иначе после пропуска занятого выхода круг сбивался бы, и поток перестал бы
     * делиться поровну.
     */
    public void take(Direction taken) {
        List<Direction> all = List.of(dir, right(dir), left(dir));
        nextOutput = (all.indexOf(taken) + 1) % 3;
        item = null;
    }

    private static Direction right(Direction d) {
        return d.rotateCw();
    }

    private static Direction left(Direction d) {
        return d.rotateCw().rotateCw().rotateCw();
    }
}
```

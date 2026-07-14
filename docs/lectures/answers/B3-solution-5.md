# 📄 Решение — Р5 — `BeltView`, `BeltItemPos`, `itemPositions`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

```java
public interface BeltView {

    /**
     * Позиции предметов для кадра.
     *
     * @param alpha доля прожитого тика (0..1). Симуляция шагает раз в Config.TICK, а
     *              кадров между тиками много: alpha говорит, насколько предмет уже уехал
     *              от прошлой позиции к текущей. Это и даёт плавность.
     */
    List<BeltItemPos> itemPositions(float alpha);
}
```

```java
/**
 * Где нарисовать предмет, едущий по ленте: непрерывные координаты поля.
 *
 * <p>Целое значение x/y — это ЦЕНТР клетки, дробное — точка между клетками. Это
 * по-прежнему координаты ДОМЕНА (клетки), а не пиксели: перевод в пиксели — забота
 * слоя отрисовки, домен про экран ничего не знает.
 */
public record BeltItemPos(Item item, float x, float y) { }
```

```java
    @Override
    public List<BeltItemPos> itemPositions(float alpha) {
        List<BeltItemPos> out = new ArrayList<>(items.size());
        Cell tail = tiles.getFirst();
        for (BeltItem it : items) {
            // Между тиками показываем предмет между прошлой и текущей позицией.
            float slot = it.prevSlot + (it.slot - it.prevSlot) * alpha;
            // Центр слота в клетках от хвостового КРАЯ линии.
            float dist = (slot + 0.5f) / SLOTS;
            // Клетка-хвост занимает dist ∈ [0,1], её центр — dist = 0.5.
            float x = tail.x() + dir.dx() * (dist - 0.5f);
            float y = tail.y() + dir.dy() * (dist - 0.5f);
            out.add(new BeltItemPos(it.item, x, y));
        }
        return out;
    }
```

# 💡 Подсказка — П7 — `BeltNetwork`

> Это **направление**, а не ответ. Если и после неё не пойдёт — открывайте решение.
>
> ← Назад в тетрадь: [B3-workbook.md](../B3-workbook.md)

---

Каркас постройки:

```java
void onBeltPlaced(Cell cell, Belt belt) {
    Direction dir = belt.dir();
    BeltSegment back  = /* линия ленты того же направления сзади, или null */;
    BeltSegment front = /* ... спереди, или null */;

    List<Cell> tiles = new ArrayList<>();
    List<BeltItem> items = new ArrayList<>();

    if (back != null)  { /* клетки back + его предметы БЕЗ изменений; back убрать из набора */ }

    int newTileIndex = tiles.size();     // ← это номер НАШЕЙ клетки в новой линии
    tiles.add(cell);

    if (front != null) { /* предметы front сдвинуть на (newTileIndex + 1) * SLOTS; клетки добавить */ }

    createSegment(dir, tiles, items);    // одна функция на все случаи
}
```

Заметьте: **отдельных веток «случай 2» и «случай 3» не понадобилось** — они получаются сами, если
`back` или `front` окажется `null`. Четыре случая схлопнулись в один код. Это и есть признак того,
что формулировка найдена верная.

Для сноса: посчитайте `cutFrom = k * SLOTS` и `cutTo = (k+1) * SLOTS`, пройдите по предметам и
разложите их по трём корзинам (левая / правая / уничтожен), потом соберите из непустых половинок линии
той же функцией `createSegment`.

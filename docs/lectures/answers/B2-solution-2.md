# 📄 Решение — Р2 — Штамп версии

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [B2-workbook.md](../B2-workbook.md)

---

В `Tile`:

```java
    /**
     * Номер тика, на котором клетку уже «застолбил» отдающий сосед.
     *
     * <p><b>Приём «штамп версии».</b> Симуляции нужно за тик отметить: «в эту клетку уже
     * кладут предмет» — иначе две ленты пропихнули бы два предмета в один слот. Наивно
     * это делается массивом отметок, который каждый тик создают заново и обнуляют. Но
     * такого массива в чанковом мире не построить (нет общего числа клеток), да и
     * обнулять миллион ячеек каждый тик — работа на ровном месте.
     *
     * <p>Вместо этого клетка помнит НОМЕР ТИКА, на котором её заняли. Занята ⇔
     * claimedTick == текущий тик. Наступил новый тик — и все прошлые отметки
     * автоматически стали «старыми». Обнуление миллиона ячеек заменилось увеличением
     * одного числа.
     *
     * <p>Цена приёма, которую надо назвать вслух: в данных мира поселилось поле, нужное
     * только симуляции. Это осознанный размен — как «грязный флаг» в графических движках.
     */
    private int claimedTick = -1;

    /**
     * Попытаться застолбить клетку на этот тик.
     *
     * @return true, если удалось (клетку в этом тике ещё не занимали);
     *         false, если сосед успел раньше
     */
    boolean claim(int tick) {
        if (claimedTick == tick) {
            return false;
        }
        claimedTick = tick;
        return true;
    }
```

В `World`:

```java
    /**
     * Застолбить соседнюю клетку на этот тик: «в неё уже кладут предмет».
     *
     * @return true, если клетка досталась вам; false, если сосед успел раньше или
     *         клетки за краем поля не существует
     */
    public boolean claimNeighbor(int x, int y, Direction dir, int tick) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) && tiles[idx(nx, ny)].claim(tick);
    }
```

В `Simulation`:

```java
    private void moveItems() {
        moves.clear(); // очистить, а не создать заново

        // Фаза 1: планирование (только чтение).
        world.forEachBuilding((x, y, source) -> {
            Optional<Handoff> handoff = source.output();
            if (handoff.isEmpty()) {
                return;
            }
            Item item = handoff.get().item();
            Building receiver = world.neighborBuilding(x, y, handoff.get().direction());
            if (receiver == null || !receiver.canAccept(item)) {
                return;
            }
            // Столбим клетку ПОСЛЕДНЕЙ: если сосед успел раньше — передачи не будет,
            // и «занимать» её мы не имеем права.
            if (world.claimNeighbor(x, y, handoff.get().direction(), tick)) {
                moves.add(new Move(source, receiver, item));
            }
        });

        // Фаза 2: применение (можно менять).
        for (Move move : moves) {
            move.source().removeOutput();
            move.target().accept(move.item());
        }
    }
```

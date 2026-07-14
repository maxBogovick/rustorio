# 💡 Подсказка — П3 — `Research`

> Это **направление**, а не ответ. Если и после неё не пойдёт — открывайте решение.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

- Живёт в пакете `game`: это состояние **игры**, а не мира.
- Поля: `Balance balance`, `Set<Tech> unlocked = EnumSet.noneOf(Tech.class)`, `int points`.
- `collect(world)`: `world.forEachBuilding(...)`, и у каждой `Lab` — **`drainPoints()`**, а не
  геттер.
- `canResearch`: не открыта **И** предпосылки готовы **И** очков хватает.
- `research(tech)`: **сначала** `if (!canResearch(tech)) return false;` — проверка внутри, а
  не на совести вызывающего. Потом списать очки, добавить в `unlocked`, применить эффекты.
- `unlocked()` наружу отдавайте через `Collections.unmodifiableSet` — чтобы никто не открыл
  технологию, минуя правила.

# 💡 Подсказка — П2 — `Technology`

> Это **направление**, а не ответ. Если и после неё не пойдёт — открывайте решение.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

- `record Technology(Tech id, int cost, List<Tech> requires, List<Effect> effects)`.
- **Стоимость — в очках (`int`)**, а не в предметах: предметы уже съедены лабораторией.
- В компактном конструкторе скопируйте списки (`List.copyOf`) — данные не должны меняться.
- `BY_ID` — `EnumMap<Tech, Technology>` для поиска за O(1).
- `isAvailableWith(unlocked)` — это одна строка: `unlocked.containsAll(requires)`.

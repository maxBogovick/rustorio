# 📄 Решение — Р2 — HUD

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C5-workbook.md](../C5-workbook.md)

---

```java
        // Строка исследований: очки и состояние каждой технологии. Собирается из ДАННЫХ
        // (Tech.values() + таблица Technology), поэтому новая технология появится тут сама.
        var research = game.research();
        StringBuilder techs = new StringBuilder("Science: " + research.points() + "    ");
        for (Tech tech : Tech.values()) {
            var technology = Technology.of(tech);
            String state;
            if (research.isUnlocked(tech)) {
                state = "OK";
            } else if (research.canResearch(tech)) {
                state = "ready";
            } else {
                state = String.valueOf(technology.cost());
            }
            techs.append('F').append(tech.ordinal() + 1).append(' ')
                    .append(tech.displayName()).append(" [").append(state).append("]   ");
        }
        font.setColor(C_HINT);
        font.draw(batch, techs.toString(), 20, worldHeight - 66);
```

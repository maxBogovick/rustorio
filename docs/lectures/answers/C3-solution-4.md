# 📄 Решение — Р4 — Отрисовка

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C3-workbook.md](../C3-workbook.md)

---

В проходе накладок лаборатория получает полоску прогресса (правит **владелец трека A**):

```java
                    case Lab lab -> drawProgressBar(px, py, lab.progressFraction());
                    case Splitter _, UndergroundBelt _ -> { /* заглушки: накладок нет */ }
```

Число очков уже рисуется в проходе предметов и текста:

```java
                    case Lab lab -> {
                        font.getData().setScale(0.9f);
                        font.setColor(Color.WHITE);
                        font.draw(batch, Integer.toString(lab.points()), px + 6, py + 20);
                    }
```

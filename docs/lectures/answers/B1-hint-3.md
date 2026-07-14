# 💡 Подсказка — П3 — `World`

> Это **направление**, а не ответ. Если и после неё не пойдёт — открывайте решение.
>
> ← Назад в тетрадь: [B1-workbook.md](../B1-workbook.md)

---

- `Map<Long, Chunk> chunks = new HashMap<>()`.
- `tile(x, y)` → `chunk(x >> SHIFT, y >> SHIFT).tile(x & MASK, y & MASK)`.
- `chunk(...)` → `chunks.computeIfAbsent(key, _ -> new Chunk(...))` — создание по требованию.
- Ключ: `((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL)`. **Маска обязательна**: без неё
  отрицательный `chunkY` при расширении до `long` зальёт единицами старшую половину и
  испортит ключ.
- `generate()` больше ничего не раскладывает — просто создаёт пустой мир.

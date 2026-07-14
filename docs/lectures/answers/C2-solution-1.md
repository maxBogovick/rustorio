# 📄 Решение — Р1 — Новый предмет

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C2-workbook.md](../C2-workbook.md)

---

```java
// core/Item.java
public enum Item {
    IRON_ORE,
    IRON_PLATE,
    GEAR,
    /** Первый предмет, собираемый из ДВУХ разных ингредиентов (пластина + шестерёнки). */
    MECHANISM
}
```

```java
// core/Config.java
    /** Секунд на сборку механизма (составной рецепт: пластина + 2 шестерёнки). */
    public static final float MECHANISM_TIME = 1.6f;
```

```java
// render/Textures.java — сюда вас привёл компилятор
    /** Спрайт предмета по его типу. Новый предмет — одна ветка здесь. */
    Texture itemTexture(Item item) {
        return switch (item) {
            case IRON_ORE -> ironOre;
            case IRON_PLATE -> ironPlate;
            case GEAR -> gear;
            case MECHANISM -> mechanism;
        };
    }
```

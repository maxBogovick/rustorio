# 📄 Решение — Р1 — `Tech` и `Effect`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

```java
package com.rustorio.core;

/**
 * Технологии, которые можно открыть за очки исследований.
 *
 * <p>Это ИМЕНА, а не описания: «сколько стоит», «что требует», «что даёт» — всё это данные,
 * и живут они в таблице Technology.ALL. Добавить технологию = добавить сюда имя и одну строку
 * данных. Ни одного нового switch.
 */
public enum Tech {
    FAST_BELT("Быстрая лента"),
    FAST_FURNACE("Быстрая печь"),
    FAST_MINER("Быстрый бур"),
    LONG_UNDERGROUND("Длинная подземка");

    private final String displayName;

    Tech(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
```

```java
package com.rustorio.core;

/**
 * Что технология делает с игрой, когда её открыли.
 *
 * <p><b>Почему эффект — отдельный тип, а не «просто код».</b> Соблазн: написать в исследовании
 * switch (tech) { case FAST_BELT -> balance.setBeltSlotsPerTick(3); … }. Работает — ровно до
 * пятой технологии, после чего switch превращается в помойку, и каждая новая технология требует
 * правки КОДА, а не данных.
 *
 * <p>Обратите внимание: эффект меняет только Balance — то есть ИЗМЕНЯЕМЫЙ баланс, а не Config.
 * Ради этого их и разделили в спринте 0.
 */
public sealed interface Effect {

    /** Применить эффект к балансу игры. */
    void applyTo(Balance balance);

    /** Ускорить машину: factor = 1.5 — «работает в полтора раза быстрее». */
    record MachineSpeed(Tool machine, float factor) implements Effect {
        @Override
        public void applyTo(Balance balance) {
            balance.multiplySpeed(machine, factor);
        }
    }

    /** Разогнать ленты: сколько слотов предмет проезжает за тик. */
    record BeltSpeed(int slotsPerTick) implements Effect {
        @Override
        public void applyTo(Balance balance) {
            balance.setBeltSlotsPerTick(slotsPerTick);
        }
    }

    /** Удлинить подземную ленту: на сколько клеток она «ныряет». */
    record UndergroundReach(int tiles) implements Effect {
        @Override
        public void applyTo(Balance balance) {
            balance.setUndergroundReach(tiles);
        }
    }
}
```

package com.rustorio.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Баланс игры — числа, которые МОЖНО менять во время игры.
 *
 * <p><b>Зачем он отдельно от {@link Config}.</b> В {@code Config} всё объявлено как
 * {@code static final} — «зашито в программу навсегда». Это правильно для геометрии
 * (размер клетки, длина тика), но делает невозможной прогрессию: апгрейд «быстрая
 * печь» обязан МЕНЯТЬ время плавки, а константу времени компиляции менять нельзя.
 *
 * <p>Поэтому числа разъехались: в {@code Config} — неизменяемая база, здесь —
 * изменяемое состояние. {@code Balance} живёт в {@code GameState} и приезжает в
 * здания через {@link TickContext}.
 *
 * <p><b>Почему множители, а не сами времена.</b> Базовое время каждого рецепта уже
 * записано в самом рецепте (это данные). Дублировать их здесь — значит завести два
 * источника правды. Вместо этого храним МНОЖИТЕЛЬ СКОРОСТИ машины: 1.0 — как
 * задумано, 2.0 — вдвое быстрее. Апгрейд просто увеличивает множитель, и его
 * действие автоматически распространяется на все рецепты этой машины, включая те,
 * которых ещё не существует.
 */
public final class Balance {

    private final Map<Tool, Float> speed = new EnumMap<>(Tool.class);
    private int beltSlotsPerTick = Config.BELT_SLOTS_PER_TICK;
    private int undergroundReach = Config.UNDERGROUND_REACH;

    public Balance() {
        for (Tool tool : Tool.values()) {
            speed.put(tool, 1f);
        }
    }

    /** Множитель скорости машины: 1.0 — базовая, 2.0 — вдвое быстрее. */
    public float speed(Tool machine) {
        return speed.get(machine);
    }

    /** Ускорить машину (это и есть эффект апгрейда). */
    public void multiplySpeed(Tool machine, float factor) {
        speed.put(machine, speed.get(machine) * factor);
    }

    /**
     * Задать множитель напрямую — используется загрузкой сохранения.
     *
     * <p>Обычная игра множитель только УМНОЖАЕТ (апгрейды); прямая установка нужна лишь
     * снимку, который восстанавливает баланс как есть, не переигрывая историю апгрейдов.
     */
    public void setSpeed(Tool machine, float value) {
        speed.put(machine, value);
    }

    /** Сколько слотов предмет проезжает по ленте за тик. */
    public int beltSlotsPerTick() {
        return beltSlotsPerTick;
    }

    public void setBeltSlotsPerTick(int slots) {
        this.beltSlotsPerTick = slots;
    }

    /** На сколько клеток «ныряет» подземная лента. */
    public int undergroundReach() {
        return undergroundReach;
    }

    public void setUndergroundReach(int reach) {
        this.undergroundReach = reach;
    }
}

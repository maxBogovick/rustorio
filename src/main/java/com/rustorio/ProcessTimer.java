package com.rustorio;

/**
 * Обратный отсчёт до готовности одной порции переработки — общая часть {@link Furnace} и
 * {@link Lab}. Обе крутят один и тот же цикл «есть сырьё → считаем тики → готово», раньше
 * скопированный между классами почти дословно. Тот же приём (композиция вместо копипасты,
 * Effective Java, Item 18), которым в проекте уже обёрнут {@link SpeedModule}, применён здесь
 * второй раз — к самому счётчику готовности, а не к зданию целиком.
 *
 * <p>Класс не знает ни про {@link Item}, ни про {@link Recipe}, ни про технологии — он крутит
 * голые тики. Что считать «эффективным временем порции» (тех-апгрейд может его уменьшить) —
 * решает вызывающий и передаёт числом при каждом {@link #tick}; так {@code ProcessTimer} не
 * зависит от {@link World}/{@link Research} и его можно тестировать без них.
 */
final class ProcessTimer {

    private int cooldown;

    ProcessTimer(int initialTime) {
        this.cooldown = initialTime;
    }

    /**
     * Прожить один тик. Возвращает {@code true} ровно в тот тик, когда порция стала готова —
     * тогда же отсчёт сбрасывается на {@code nextTime} (уже эффективное, тех-модифицированное
     * время следующей порции, а не то, что было при постройке здания).
     */
    boolean tick(int nextTime) {
        if (--cooldown > 0) {
            return false;
        }
        cooldown = nextTime;
        return true;
    }

    /** Текущее значение отсчёта — для сохранения. */
    int cooldown() {
        return cooldown;
    }

    /** Восстановить отсчёт из сохранённого значения. */
    void restore(int cooldown) {
        this.cooldown = cooldown;
    }
}

package com.rustorio.core;

/**
 * Что технология делает с игрой, когда её открыли.
 *
 * <p><b>Почему эффект — это отдельный тип, а не «просто код».</b> Соблазн: написать в
 * исследовании {@code switch (tech) { case FAST_BELT -> balance.setBeltSlotsPerTick(3); … }}.
 * Работает — ровно до пятой технологии, после чего {@code switch} превращается в помойку, и
 * каждая новая технология требует правки КОДА, а не данных.
 *
 * <p>Вместо этого эффект — маленький {@code record}, который сам умеет применить себя к
 * {@link Balance}. Технология в таблице просто перечисляет свои эффекты. Новая технология =
 * строка данных; новый ВИД эффекта = новый record здесь (и компилятор, благодаря
 * {@code sealed}, сам напомнит обо всех местах, где его надо учесть).
 *
 * <p>Обратите внимание: эффект меняет только {@link Balance} — то есть ИЗМЕНЯЕМЫЙ баланс, а
 * не {@code Config}. Ради этого их и разделили в спринте 0: константы времени компиляции
 * поменять нельзя, а прогрессия обязана менять числа во время игры.
 */
public sealed interface Effect {

    /** Применить эффект к балансу игры. */
    void applyTo(Balance balance);

    /** Ускорить машину: {@code factor = 1.5} — «работает в полтора раза быстрее». */
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

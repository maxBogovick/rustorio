package com.rustorio;

/**
 * Бур: раз в несколько тиков добывает порцию руды.
 *
 * <p>Обычный класс, без интерфейсов и базовых типов — здание пока ОДНО, обобщать нечего.
 */
public final class Miner {

    /** За сколько тиков готовится одна порция руды. */
    private static final int MINE_TIME = 3;

    /** Сколько тиков осталось до следующей порции. */
    private int cooldown = MINE_TIME;

    /** Прожить один тик. Вернуть добытую руду или {@code null}, если ещё не готова. */
    public Item tick() {
        cooldown--;
        if (cooldown > 0) {
            return null;             // ещё копаем
        }
        cooldown = MINE_TIME;        // завод на следующую порцию
        return Item.IRON_ORE;
    }
}

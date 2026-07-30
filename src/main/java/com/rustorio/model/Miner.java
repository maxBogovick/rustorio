package com.rustorio.model;

import com.rustorio.*;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;

import java.util.Map;
import java.util.Optional;

import static com.rustorio.World.key;

/**
 * Бур: раз в несколько тиков добывает порцию руды.
 *
 * <p>Обычный класс, без интерфейсов и базовых типов — здание пока ОДНО, обобщать нечего.
 */
public final class Miner implements Building {

    /** За сколько тиков готовится одна порция руды. */
    private static final int MINE_TIME = 3;

    /** Сколько тиков осталось до следующей порции. */
    private int cooldown = MINE_TIME;

    /** Прожить один тик. Вернуть добытую руду или {@code null}, если ещё не готова. */
    public void tick(World world, int x, int y) {
        cooldown--;
        if (--cooldown > 0) {
            return;             // ещё копаем
        }
        cooldown = MINE_TIME;       // завод на следующую порцию
        world.offerToNeighbor(x, y, Item.IRON_ORE);
    }

    public Appearance appearance() { return Appearance.of(Sprite.MINER); }

    @Override
    public Optional<Direction> direction() {
        return Optional.empty();
    }

    @Override public BuildingType type() { return BuildingType.MINER; }
    @Override public String save()       { return Integer.toString(cooldown); }

    public static Miner load(String data) {
        Miner miner = new Miner();
        miner.cooldown = Integer.parseInt(data);
        return miner;
    }
}

package com.rustorio;

import java.util.HashMap;
import java.util.Map;

/**
 * Игровое поле: где лежит руда (через {@link OreMap}) и какие буры на нём стоят.
 */
public final class World {

    private final int width;
    private final int height;

    /** Клетка (упакованные координаты) → бур на ней. */
    private final Map<Long, Miner> miners = new HashMap<>();

    public World(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Есть ли под клеткой руда — бур ставится только на неё. */
    public boolean hasOre(int x, int y) {
        return OreMap.hasOre(x, y);
    }

    public boolean hasMiner(int x, int y) {
        return miners.containsKey(key(x, y));
    }

    /**
     * Поставить бур, если клетка в поле, на руде и ещё свободна.
     *
     * <p>Правило «только на руде» живёт ЗДЕСЬ, а не в вводе: где можно строить — это про мир, а
     * не про то, какой кнопкой кликнули.
     */
    public void placeMiner(int x, int y) {
        if (inBounds(x, y) && hasOre(x, y) && !hasMiner(x, y)) {
            miners.put(key(x, y), new Miner());
        }
    }

    /** Один шаг мира: каждый бур проживает свой тик. */
    public void tick() {
        for (Miner miner : miners.values()) {
            miner.tick();
        }
    }

    /** Обойти все буры с их координатами — нужно отрисовке. */
    public void forEachMiner(MinerVisitor visitor) {
        for (Map.Entry<Long, Miner> entry : miners.entrySet()) {
            long k = entry.getKey();
            visitor.visit(keyX(k), keyY(k), entry.getValue());
        }
    }

    /** Что делать с каждой клеткой-буром при обходе. */
    @FunctionalInterface
    public interface MinerVisitor {
        void visit(int x, int y, Miner miner);
    }

    // --- Ключ карты: две координаты, упакованные в один long ---------------------------------

    private static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyY(long key) {
        return (int) key;
    }
}

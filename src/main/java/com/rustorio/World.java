package com.rustorio;

import com.rustorio.core.Item;
import com.rustorio.model.Building;
import com.rustorio.model.Chest;
import com.rustorio.model.Furnace;
import com.rustorio.model.Miner;

import java.util.HashMap;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

import static com.rustorio.model.Building.buildings;

/**
 * Игровое поле: где лежит руда (через {@link OreMap}) и какие буры на нём стоят.
 */
public final class World {

    private final int width;
    private final int height;
    private ProductionStats stats =  new ProductionStats();

    private final NavigableMap<Long, Building> buildings = new TreeMap<>();

    public void tick() {
        for (Map.Entry<Long, Building> entry : buildings.descendingMap().entrySet()) {
            long k = entry.getKey();
            entry.getValue().tick(this, keyX(k), keyY(k));
        }
    }

    /**
     * Клетка (упакованные координаты) → бур на ней.
     */


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

    /**
     * Есть ли под клеткой руда — бур ставится только на неё.
     */
    public boolean hasOre(int x, int y) {
        return OreMap.hasOre(x, y);
    }

    public boolean hasBuilding(int x, int y) {
        return buildings.containsKey(key(x, y));
    }

    /**
     * Поставить бур, если клетка в поле, на руде и ещё свободна.
     *
     * <p>Правило «только на руде» живёт ЗДЕСЬ, а не в вводе: где можно строить — это про мир, а
     * не про то, какой кнопкой кликнули.
     */
    public void placeMiner(int x, int y) {
        if (inBounds(x, y) && hasOre(x, y) && !hasBuilding(x, y)) {
            buildings.put(key(x, y), new Miner());
        }
    }

    public void placeChest(int x, int y) {
        if (inBounds(x, y) && !hasBuilding(x, y)) {
            buildings.put(key(x, y), new Chest());
        }
    }

    public void placeFurnace(int x, int y) {
        if (inBounds(x, y) && !hasBuilding(x, y)) {
            buildings.put(key(x, y), new Furnace());
        }
    }

    // World
    public void place(BuildingType type, int x, int y) {
        switch (type) {
            case MINER -> placeMiner(x, y);
            case CHEST -> placeChest(x, y);
            case FURNACE -> placeFurnace(x, y);
        }
    }

    void restore(int x, int y, Building building) {
        buildings.put(key(x, y), building);
    }

    /**
     * Обойти все буры с их координатами — нужно отрисовке.
     */
    public void forEachBuilding(BuildingVisitor visitor) {
        for (Map.Entry<Long, Building> entry : buildings.entrySet()) {
            long k = entry.getKey();
            visitor.visit(keyX(k), keyY(k), entry.getValue());
        }
    }

    public boolean offerForward(int x, int y, Item item) {
        return offer(x, y, item);
    }

    /**
     * Что делать с каждой клеткой-буром при обходе.
     */
    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
    }

    public void removeObject(int x, int y) {
        buildings.remove(key(x, y));

    }

    public boolean offerToNeighbor(int x, int y, Item item) {
        stats.record(item);
        return offer(x + 1, y, item) || offer(x - 1, y, item)
                || offer(x, y + 1, item) || offer(x, y - 1, item);
    }

    private boolean offer(int x, int y, Item item) {
        Building building = buildings.get(key(x, y));
        return building != null && building.accept(item);
    }

    public ProductionStats stats() { return stats; }

    void clear() {
        buildings.clear();
        stats.clear();
    }

    // --- Ключ карты: две координаты, упакованные в один long ---------------------------------

    public static long key(int x, int y) {
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    private static int keyY(long key) {
        return (int) key;
    }
}

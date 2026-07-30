package com.rustorio.model;

import com.rustorio.Appearance;
import com.rustorio.BuildingType;
import com.rustorio.core.Item;
import com.rustorio.World;
import com.rustorio.core.Direction;
import com.rustorio.core.Tool;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

// model/Building.java
public sealed interface Building permits Belt, Chest, Furnace, Miner {
    public final Map<Long, Building> buildings = new HashMap<>();

    Optional<Direction> direction(); // у ящика — empty; у будущего бура — сторона выдачи

    // Building — БЕЗ реализации по умолчанию: внешность есть у любого здания
    Appearance appearance();

    /** «Такое же» здание: тот же тип и то же направление? (зачем — в шаге 2) */
    default boolean sameKind(Building other) {
        return this.getClass() ==  other.getClass() && this.direction().equals(other.direction());
    }

    /** Фабрика «инструмент → новое здание». Optional: зданий пока меньше, чем инструментов. */
    static Optional<Building> create(Tool tool, Direction dir) {
        return switch (tool) {
            case CHEST -> Optional.of(new Chest());
            case BELT -> Optional.empty();
            case MINER ->  Optional.of(new Miner());
            case FURNACE -> Optional.of(new Furnace());
        };
    }

    default void tick(World world, int x, int y) {}

    default boolean accept(Item item) {return false;   // по умолчанию здание НЕ принимает ничего
    }

    BuildingType type();
    String save();
}

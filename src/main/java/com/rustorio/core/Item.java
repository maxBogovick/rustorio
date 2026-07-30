package com.rustorio.core;

// core/Item.java
/**
 * этот енам содержит в себе ресурсы в разных состояниях: руда - пластина - шестеренка (пока не реализована).
 */
public enum Item {
    IRON_ORE("Iron Ore"),
    IRON_PLATE("Iron Plate");

    public  final String name;
    Item(String name)
    {this.name = name;}
}

package com.rustorio;

import com.rustorio.core.Item;

@FunctionalInterface
public interface SortRule {
    boolean forward(Item item);

    SortRule ORE_FORWARD = item -> item == Item.IRON_ORE;
}

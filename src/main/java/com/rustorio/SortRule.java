package com.rustorio;

import com.rustorio.core.Item;

import java.util.function.Supplier;

@FunctionalInterface
public interface SortRule {
    boolean forward(Supplier<Item> itemSupplier);

    SortRule ORE_FORWARD = item -> item.equals(item.get());
}

package com.rustorio.domain.world;

import com.rustorio.domain.Item;

/**
 * Observer pattern: notified whenever an item is produced anywhere on the map. {@link World}
 * knows only that each subscriber can answer "an item was produced, here it is" — not how many
 * there are or what they do with it.
 */
@FunctionalInterface
public interface ProductionListener {
    void onProduced(Item item);
}

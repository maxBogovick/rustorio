package com.rustorio.domain.world;

import com.rustorio.domain.Item;
import java.util.ArrayList;
import java.util.List;

/**
 * The last few produced items, most recent first — a second, entirely independent {@link
 * ProductionListener} alongside {@link ProductionStats}. Neither knows the other exists; both
 * learn of an event at the same time and separately decide what to do with it. {@link World}
 * doesn't know this class exists either — it only knows about {@link World#addProductionListener}.
 */
public final class ProductionLog implements ProductionListener {

    private static final int CAPACITY = 5;

    private final List<Item> recent = new ArrayList<>();

    @Override
    public void onProduced(Item item) {
        recent.add(0, item);
        if (recent.size() > CAPACITY) {
            recent.remove(recent.size() - 1);
        }
    }

    public List<Item> recent() {
        return List.copyOf(recent);
    }
}

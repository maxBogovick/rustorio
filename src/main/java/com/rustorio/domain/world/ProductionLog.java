package com.rustorio.domain.world;

import com.rustorio.domain.Item;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The last few produced items, most recent first — a second, entirely independent {@link
 * ProductionListener} alongside {@link ProductionStats}. Neither knows the other exists; both
 * learn of an event at the same time and separately decide what to do with it. {@link World}
 * doesn't know this class exists either — it only knows about {@link World#addProductionListener}.
 */
public final class ProductionLog implements ProductionListener, ProductionLogView {

    private static final int CAPACITY = 5;

    /**
     * {@link ArrayDeque}, not {@code ArrayList} (P4-05, BUG_FIX_PROGRESS.md): the access pattern
     * is exactly "push to the front, trim from the back," which {@code ArrayList} can only do via
     * {@code add(0, …)} — an O(n) shift on every single production event.
     */
    private final Deque<Item> recent = new ArrayDeque<>();

    /** {@code tick} isn't used here (D-06, DEV_TASKS.md) — the recent-items log only ever cared about order, not timing. */
    @Override
    public void onProduced(long tick, Item item) {
        recent.addFirst(item);
        if (recent.size() > CAPACITY) {
            recent.removeLast();
        }
    }

    @Override
    public List<Item> recent() {
        return List.copyOf(recent);
    }
}

package com.rustorio;

import java.util.ArrayList;
import java.util.List;

/**
 * Последние несколько произведённых предметов, самый свежий первым.
 *
 * <p>Второй, совершенно независимый слушатель {@link ProductionListener} — рядом с
 * {@link ProductionStats}. Ни этот класс не знает про статистику, ни статистика про него; оба
 * узнают о событии одновременно и порознь решают, что с ним делать. {@link World} про существование
 * ЭТОГО класса вообще не знает — он лишь зовёт {@link World#addProductionListener}.
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

    /** Последние произведённые предметы, самый свежий первым. */
    public List<Item> recent() {
        return List.copyOf(recent);
    }
}

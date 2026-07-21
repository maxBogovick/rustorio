package com.rustorio;

import java.util.List;

/**
 * Несколько действий, применяемых и откатываемых как ОДНО.
 *
 * <p>Ничего нового не понадобилось: {@code CompositeAction} — обычная реализация
 * {@link PlayerAction}, как {@link PlaceAction} и {@link RemoveAction}. {@link ActionHistory} про
 * неё вообще не знает — с её точки зрения это всё то же одно действие с {@code apply}/{@code
 * undo}. В этом и мораль урока 15: правильно построенный паттерн окупается на следующей фиче
 * даром — ни строчки в {@code ActionHistory} не понадобилось менять.
 */
public final class CompositeAction implements PlayerAction {

    private final List<PlayerAction> actions;

    public CompositeAction(List<PlayerAction> actions) {
        this.actions = actions;
    }

    @Override
    public boolean apply(World world) {
        boolean any = false;
        for (PlayerAction action : actions) {
            if (action.apply(world)) {
                any = true;
            }
        }
        return any;
    }

    @Override
    public void undo(World world) {
        // Откатываем В ОБРАТНОМ порядке — как стопку тарелок: последнее положенное снимается
        // первым. Для независимых построек порядок неважен, но так честнее в общем случае.
        for (int i = actions.size() - 1; i >= 0; i--) {
            actions.get(i).undo(world);
        }
    }
}

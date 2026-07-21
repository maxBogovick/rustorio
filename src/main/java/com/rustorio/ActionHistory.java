package com.rustorio;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * История применённых действий: что сделано — можно откатить (undo); что откачено — можно
 * повторить (redo), пока не сделано ничего нового.
 */
public final class ActionHistory {

    private final Deque<PlayerAction> done = new ArrayDeque<>();
    private final Deque<PlayerAction> undone = new ArrayDeque<>();

    /**
     * Применить действие к миру; если оно реально что-то изменило — запомнить для отмены.
     * Неудачная попытка (клетка занята, снести нечего) не засоряет историю.
     */
    public void perform(World world, PlayerAction action) {
        if (action.apply(world)) {
            done.push(action);
            undone.clear(); // новое действие обрывает «будущее» — повторить старый redo нельзя
        }
    }

    /** Откатить последнее запомненное действие, если оно есть. */
    public void undo(World world) {
        if (done.isEmpty()) {
            return;
        }
        PlayerAction action = done.pop();
        action.undo(world);
        undone.push(action);
    }

    /** Повторить последнее откаченное действие, если оно есть. */
    public void redo(World world) {
        if (undone.isEmpty()) {
            return;
        }
        PlayerAction action = undone.pop();
        action.apply(world);
        done.push(action);
    }
}

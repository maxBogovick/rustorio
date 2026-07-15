package com.rustorio.game;

import com.rustorio.game.action.PlayerAction;
import com.rustorio.model.World;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * История отмен: два стека уже выполненных команд («сделано» и «отменено»).
 *
 * <p>В терминах паттерна Command это <b>инвокер</b> — тот, кто хранит команды и решает,
 * когда их выполнять и откатывать. Вся «магия» отмены сводится к работе со стеком:
 * <ul>
 *   <li>новая команда кладётся в стек «сделано», а стек «отменено» очищается — после
 *       нового действия старая ветка повторов невозвратна (так ведёт себя любой редактор);
 *   <li>отмена снимает верхнюю из «сделано», откатывает и кладёт в «отменено»;
 *   <li>повтор — зеркально.</li>
 * </ul>
 *
 * <p><b>Почему стек, а не список с указателем.</b> Оба варианта из «Game Programming
 * Patterns» эквивалентны; два стека делают инвариант «после нового действия повторы
 * пропадают» невозможным нарушить — очищаем один стек, и всё. Меньше состояния — меньше
 * багов, а для учебного кода это важнее пары сэкономленных объектов.
 */
public final class ActionHistory {

    private final Deque<PlayerAction> done = new ArrayDeque<>();
    private final Deque<PlayerAction> undone = new ArrayDeque<>();

    /** Записать уже выполненную команду. Ветка повторов после этого обнуляется. */
    public void push(PlayerAction action) {
        done.push(action);
        undone.clear();
    }

    /** Отменить последнюю команду (если есть). */
    public void undo(World world) {
        if (done.isEmpty()) {
            return;
        }
        PlayerAction action = done.pop();
        action.revert(world);
        undone.push(action);
    }

    /** Повторить последнюю отменённую команду (если есть). */
    public void redo(World world) {
        if (undone.isEmpty()) {
            return;
        }
        PlayerAction action = undone.pop();
        action.apply(world);
        done.push(action);
    }

    /**
     * Забыть всю историю. Нужно после загрузки сохранения: команды в стеках ссылались на
     * здания прежнего мира, откатывать их поверх загруженного поля бессмысленно и опасно.
     */
    public void clear() {
        done.clear();
        undone.clear();
    }

    /** Сколько отмен доступно (для HUD). */
    public int undoDepth() {
        return done.size();
    }

    /** Сколько повторов доступно. */
    public int redoDepth() {
        return undone.size();
    }
}

package com.rustorio.game.action;

import com.rustorio.model.World;

import java.util.List;

/**
 * Несколько команд, ведущих себя как ОДНА, — паттерн <b>Composite</b> поверх Command.
 *
 * <p><b>Зачем.</b> Провёл линию из десяти лент, зажав ЛКМ, — это десять
 * {@link PlaceBuilding}, но игрок мыслит их одним жестом и ждёт, что <i>одна</i> отмена
 * уберёт всю линию, а не по клетке. Композит — тот самый «один жест»: снаружи это обычная
 * {@link PlayerAction}, внутри — список. Тот же тип позже соберёт «снос области» и
 * «вставку чертежа» — ноль нового кода отмены.
 *
 * <p><b>Ключевая тонкость — порядок отката.</b> Применяем детей по порядку, а откатываем
 * в ОБРАТНОМ. Если два действия затронули одну клетку (перерисовал, потом ещё раз),
 * отменять надо с конца — иначе снимки «что было» встанут не на свои места и мир не
 * вернётся к исходному. Это общий закон вложенных откатов: разворачивай стек, а не
 * проигрывай его сначала.
 *
 * <p>Список копируется ({@code List.copyOf}) — композит неизменяем как контейнер; его дети
 * при этом хранят свою память отката внутри себя, и это нормально.
 */
public record CompositeAction(List<PlayerAction> actions) implements PlayerAction {

    public CompositeAction {
        actions = List.copyOf(actions);
    }

    @Override
    public void apply(World world) {
        for (PlayerAction action : actions) {
            action.apply(world);
        }
    }

    @Override
    public void revert(World world) {
        for (int i = actions.size() - 1; i >= 0; i--) {
            actions.get(i).revert(world);
        }
    }

    @Override
    public boolean hadEffect() {
        for (PlayerAction action : actions) {
            if (action.hadEffect()) {
                return true;
            }
        }
        return false;
    }
}

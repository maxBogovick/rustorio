package com.rustorio;

/**
 * Вставить модуль скорости в здание клетки — и откатить, СНЯВ обёртку обратно.
 *
 * <p>Отмена здесь не «снести и поставить заново» — она просто возвращает ТО ЖЕ здание, каким оно
 * было ДО апгрейда (обычным или уже во сколько-то модулей, если апгрейдили не первый раз): ровно
 * то, что уже лежало в {@link #previous} к моменту {@link #apply}. Тот же приём «запомнить
 * объект, а не пересоздавать», что и у {@link RemoveAction} (урок 15).
 */
public final class UpgradeSpeedAction implements PlayerAction {

    private final int x;
    private final int y;

    /** Что стояло в клетке ДО апгрейда — запоминаем в {@link #apply}, возвращаем в {@link #undo}. */
    private Building previous;

    public UpgradeSpeedAction(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public boolean apply(World world) {
        previous = world.removeBuilding(x, y);
        if (previous == null) {
            return false; // клетка пуста — апгрейдить нечего
        }
        world.restore(x, y, new SpeedModule(previous));
        return true;
    }

    @Override
    public void undo(World world) {
        world.restore(x, y, previous);
    }
}

package com.rustorio.game.action;

import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.World;
import org.jspecify.annotations.Nullable;

/**
 * Команда «поставить здание на клетку».
 *
 * <p>Это <b>изменяемый класс</b>, а не {@code record}, и намеренно: команда с отменой
 * обязана хранить состояние — снимок того, что стояло на клетке ДО неё
 * ({@link #previous}), и признак, изменила ли она мир ({@link #changed}). Оба заполняются
 * в {@link #apply}. Это классический вид Command-с-Undo: «чистых данных» тут нет, есть
 * маленькая память для отката.
 *
 * <p><b>Почему откат идёт через {@code World.place}/{@code remove}, а не «в лоб».</b>
 * Guard мира ({@link World#place}) блокирует перезапись здания ДРУГОГО типа и пропускает
 * ТОЧНО ТАКОЕ ЖЕ. Из этого следует всё поведение отката без нового API мира:
 * <ul>
 *   <li>ставили на пустую клетку → откат = {@link World#remove};</li>
 *   <li>перерисовали ленту в другом направлении (тот же тип) → откат = поставить прежний
 *       объект: guard это разрешает (тот же класс, другое «настроение»);</li>
 *   <li>guard заблокировал постройку (другой тип уже стоял) → мы ничего не меняли
 *       ({@code changed == false}) → откат ничего не делает.</li>
 * </ul>
 * Переиспользовать протестированные {@code place}/{@code remove} безопаснее, чем заводить
 * «force-set», который в обход guard-а мог бы рассинхронить сеть лент.
 */
public final class PlaceBuilding implements PlayerAction {

    private final int x;
    private final int y;
    private final Building building;

    /** Что стояло на клетке до нас — снимок для отката. Заполняется в {@link #apply}. */
    private @Nullable Building previous;
    private boolean changed;

    public PlaceBuilding(Cell cell, Building building) {
        this.x = cell.x();
        this.y = cell.y();
        this.building = building;
    }

    @Override
    public void apply(World world) {
        if (!world.inBounds(x, y)) {
            changed = false;
            return;
        }
        previous = world.tile(x, y).building();
        world.place(x, y, building);
        // Сравнение по ССЫЛКЕ: если объект на клетке сменился — постройка состоялась.
        // guard мира при отказе оставляет прежний объект, и changed честно будет false.
        changed = world.tile(x, y).building() != previous;
    }

    @Override
    public void revert(World world) {
        if (!changed) {
            return;
        }
        if (previous == null) {
            world.remove(x, y); // клетка была пуста — просто убираем поставленное
        } else {
            world.place(x, y, previous); // вернуть прежнее (тот же тип — guard пропустит)
        }
    }

    @Override
    public boolean hadEffect() {
        return changed;
    }
}

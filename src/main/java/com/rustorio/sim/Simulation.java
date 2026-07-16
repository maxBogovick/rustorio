package com.rustorio.sim;

import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.model.Building;
import com.rustorio.model.Handoff;
import com.rustorio.model.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Симуляция: как мир продвигается на один тик.
 *
 * <p>Один шаг мира — конвейер «систем», выполненных по порядку. «Система» — это
 * функция над миром: приватный метод, который читает и меняет его данные. Хочешь
 * новое поведение мира? Пишешь новую систему и добавляешь её вызов в {@link #step}.
 *
 * <p>Систем три: {@link #runMachines} (каждое здание делает свою внутреннюю работу —
 * Update Method), {@link #moveBelts} (предметы едут ВНУТРИ транспортных линий) и
 * {@link #moveItems} (передача предметов МЕЖДУ зданиями). Развилки/подземку добавит
 * L10.
 *
 * <p><b>Почему это ОБЪЕКТ, а не набор {@code static}-методов.</b> Симуляция владеет
 * переиспользуемым между тиками буфером передач {@link #moves}: вместо
 * {@code new ArrayList<>()} на каждом шаге — одна «тарелка», которую моют.
 */
public final class Simulation {

    private final World world;

    /** Переиспользуемый буфер запланированных передач: чистится в начале каждого тика. */
    private final List<Move> moves = new ArrayList<>();

    public Simulation(World world) {
        this.world = world;
    }

    /** ОДИН шаг симуляции = конвейер систем по порядку. */
    public void step(TickContext ctx) {
        world.beginTick(); // счётчик штампов живёт в мире — там же, где сами штампы
        runMachines(ctx);
        moveBelts();
        moveItems();
    }

    /** Система №1: каждое здание делает свою внутреннюю работу (бур копает). */
    private void runMachines(TickContext ctx) {
        world.forEachBuilding((x, y, building) -> building.update(ctx));
    }

    /**
     * Система №2: предметы едут ВНУТРИ транспортных линий.
     *
     * <p>Отдельная фаза, потому что лента больше не «здание с одним предметом»:
     * непрерывная цепочка — одна линия, и двигать её надо целиком, с головы. Стык
     * «машина ↔ лента» остался прежним и обслуживается {@link #moveItems}, поэтому бур,
     * печь и сборщик не знают, что ленты переписаны.
     */
    private void moveBelts() {
        world.belts().step(Config.BELT_SLOTS_PER_TICK);
    }

    /**
     * Система №3: передача предметов между зданиями. Приём в две фазы:
     * <ol>
     *   <li><b>Планируем</b>, только ЧИТАЯ поле: собираем список передач.</li>
     *   <li><b>Применяем</b>, меняя поле.</li>
     * </ol>
     * Так получается честная «одновременная» передача без артефактов порядка обхода.
     * Штамп тика на клетке гарантирует, что за тик в неё отдаст предмет только один
     * сосед — иначе два источника пропихнули бы два предмета в один слот.
     */
    private void moveItems() {
        moves.clear(); // очистить, а не создать заново

        // Фаза 1: планирование (только чтение).
        world.forEachBuilding((x, y, source) -> {
            Optional<Handoff> handoff = source.output();
            if (handoff.isEmpty()) {
                return;
            }
            Item item = handoff.get().item();
            Building receiver = world.neighborBuilding(x, y, handoff.get().direction());
            if (receiver == null || !receiver.canAccept(item)) {
                return;
            }
            // Столбим клетку последней: если сосед успел раньше — передачи не будет.
            if (world.claimNeighbor(x, y, handoff.get().direction())) {
                moves.add(new Move(source, receiver, item));
            }
        });

        // Фаза 2: применение (можно менять).
        for (Move move : moves) {
            move.source().removeOutput();
            move.target().accept(move.item());
        }
    }

    /** Запланированная передача предмета от здания-источника к приёмнику. */
    private record Move(Building source, Building target, Item item) {
    }
}

package com.rustorio.sim;

import com.rustorio.core.Item;
import com.rustorio.model.Building;
import com.rustorio.model.Handoff;
import com.rustorio.model.Tile;
import com.rustorio.model.World;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Симуляция: как мир продвигается на один тик.
 *
 * <p>«Система» — это функция над миром: читает и меняет его данные. Это
 * мини-версия того, как устроены большие движки (данные отдельно —
 * {@link World}, логика отдельно — здесь). Один шаг мира — это конвейер систем,
 * выполненных по порядку.
 *
 * <p>Хочешь новое поведение мира («руда истощается»)? Пишешь новый приватный
 * метод-систему и добавляешь его вызов в {@link #step}. Всё.
 *
 * <p>Класс {@code final} с приватным конструктором: это набор статических
 * систем без состояния — состояние живёт в {@link World}.
 */
public final class Systems {

    private Systems() {
    }

    /**
     * ОДИН шаг симуляции = конвейер систем по порядку.
     * Порядок важен: сперва здания «поработали», потом предметы поехали дальше.
     */
    public static void step(World world, float dt) {
        runMachines(world, dt);
        moveItems(world);
    }

    /**
     * Система №1: каждое здание делает свою внутреннюю работу (бур копает, печь
     * плавит). Ленты и ящики здесь ничего не делают.
     */
    private static void runMachines(World world, float dt) {
        for (int i = 0; i < world.tileCount(); i++) {
            Tile tile = world.tileAt(i);
            Building building = tile.building();
            if (building != null) {
                building.update(dt, tile.hasOre());
            }
        }
    }

    /**
     * Система №2: предметы едут на одну клетку вперёд.
     *
     * <p>Приём в две фазы (перенесён из Rust-версии — там его требовал borrow
     * checker, а здесь он даёт корректность):
     * <ol>
     *   <li><b>Планируем</b>, только ЧИТАЯ поле: собираем список передач.</li>
     *   <li><b>Применяем</b>, меняя поле.</li>
     * </ol>
     * Так получаем честную «одновременную» передачу без артефактов порядка
     * обхода. {@code claimed[j]} гарантирует, что в клетку {@code j} за тик
     * отдаст предмет только один сосед (иначе две ленты пропихнули бы два
     * предмета в один слот).
     */
    private static void moveItems(World world) {
        List<Move> moves = new ArrayList<>();
        boolean[] claimed = new boolean[world.tileCount()];

        // Фаза 1: планирование (только чтение).
        for (int i = 0; i < world.tileCount(); i++) {
            Building source = world.tileAt(i).building();
            if (source == null) {
                continue;
            }
            var handoff = source.output();
            if (handoff.isEmpty()) {
                continue;
            }
            OptionalInt neighbor = world.neighbor(i, handoff.get().direction());
            if (neighbor.isEmpty()) {
                continue;
            }
            int j = neighbor.getAsInt();
            if (claimed[j]) {
                continue;
            }
            Building target = world.tileAt(j).building();
            Item item = handoff.get().item();
            if (target != null && target.canAccept(item)) {
                moves.add(new Move(i, j, item));
                claimed[j] = true;
            }
        }

        // Фаза 2: применение (можно менять).
        for (Move move : moves) {
            world.tileAt(move.from()).building().removeOutput();
            world.tileAt(move.to()).building().accept(move.item());
        }
    }

    /** Запланированная передача: из клетки {@code from} в клетку {@code to}. */
    private record Move(int from, int to, Item item) {
    }
}

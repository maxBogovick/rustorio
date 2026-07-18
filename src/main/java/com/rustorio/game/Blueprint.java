package com.rustorio.game;

import com.rustorio.core.Direction;
import com.rustorio.core.Tool;
import com.rustorio.game.action.CompositeAction;
import com.rustorio.game.action.PlaceBuilding;
import com.rustorio.game.action.PlayerAction;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.World;

import java.util.ArrayList;
import java.util.List;

/**
 * Чертёж: снятый с области ШАБЛОН построек, который можно штамповать где угодно одним
 * отменяемым действием.
 *
 * <p><b>Что нового по сравнению с командой.</b> {@link PlayerAction} — это конкретное,
 * уже привязанное к клетке действие («поставить ЭТО на ЭТУ клетку»). Чертёж — на шаг выше:
 * это ОТНОСИТЕЛЬНЫЙ, переносимый рецепт, который при штамповке ПОРОЖДАЕТ команды под нужное
 * место. Одно и то же снятое хранится, а стемпить его можно в десяти местах.
 *
 * <p><b>Почему это почти бесплатно.</b> Вся тяжёлая работа (поставить, запомнить прежнее,
 * откатить, сгруппировать в одну отмену) уже сделана в {@link PlaceBuilding} и {@link
 * CompositeAction}. Чертёж их только СОБИРАЕТ: {@link #stampAt} выдаёт композит из
 * {@code PlaceBuilding} — и штамповка любого размера откатывается одним Ctrl+Z. Это и есть
 * выгода правильно построенного Command: новая фича ложится поверх, не переписывая отмену.
 */
public final class Blueprint {

    /** Одна постройка чертежа: смещение от угла + чем и куда её ставить. */
    public record Stamp(int dx, int dy, Tool tool, Direction dir) {
    }

    private final List<Stamp> stamps;

    private Blueprint(List<Stamp> stamps) {
        this.stamps = List.copyOf(stamps);
    }

    public boolean isEmpty() {
        return stamps.isEmpty();
    }

    public int size() {
        return stamps.size();
    }

    /**
     * Снять чертёж с прямоугольной области мира между клетками {@code a} и {@code b}
     * (в любом порядке). Смещения считаются от верхнего-левого угла области; берётся тип и
     * направление каждого здания, но НЕ его содержимое — штампуются свежие пустые постройки.
     */
    public static Blueprint capture(World world, Cell a, Cell b) {
        int minX = Math.min(a.x(), b.x());
        int minY = Math.min(a.y(), b.y());
        int maxX = Math.max(a.x(), b.x());
        int maxY = Math.max(a.y(), b.y());
        List<Stamp> stamps = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                if (!world.inBounds(x, y)) {
                    continue;
                }
                Building building = world.tile(x, y).building();
                if (building == null) {
                    continue;
                }
                Direction dir = building.direction().orElse(Direction.EAST);
                stamps.add(new Stamp(x - minX, y - minY, building.tool(), dir));
            }
        }
        return new Blueprint(stamps);
    }

    /**
     * Собрать команду штамповки от клетки {@code origin}: композит из {@link PlaceBuilding}.
     * Выполнить его через {@code GameState.perform} — и весь оттиск ляжет в историю ОДНОЙ
     * отменой.
     */
    public CompositeAction stampAt(Cell origin) {
        List<PlayerAction> actions = new ArrayList<>();
        for (Stamp stamp : stamps) {
            Cell cell = new Cell(origin.x() + stamp.dx(), origin.y() + stamp.dy());
            actions.add(new PlaceBuilding(cell, Building.create(stamp.tool(), stamp.dir())));
        }
        return new CompositeAction(actions);
    }

    /** Абсолютные клетки оттиска от {@code origin} — для превью подсветкой. */
    public List<Cell> cells(Cell origin) {
        List<Cell> out = new ArrayList<>();
        for (Stamp stamp : stamps) {
            out.add(new Cell(origin.x() + stamp.dx(), origin.y() + stamp.dy()));
        }
        return out;
    }
}

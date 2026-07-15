package com.rustorio.game.action;

import com.rustorio.core.Direction;
import com.rustorio.core.Tool;
import com.rustorio.game.ActionHistory;
import com.rustorio.model.Belt;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import com.rustorio.model.Miner;
import com.rustorio.model.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Команды игрока: главный инвариант — <b>apply, а затем revert возвращают мир в точности
 * как было</b>. Если он держится, отмена/повтор и составные жесты «просто работают».
 */
class PlayerActionTest {

    private static World world() {
        return World.generate(16, 16);
    }

    /** Сколько зданий сейчас в мире — через штатный обход (плоского индекса больше нет). */
    private static int buildingCount(World world) {
        int[] count = {0};
        world.forEachBuilding((x, y, b) -> count[0]++);
        return count[0];
    }

    @Test
    void placeThenRevertLeavesWorldUnchanged() {
        World world = world();
        PlaceBuilding place = new PlaceBuilding(
                new Cell(2, 3), Building.create(Tool.MINER, Direction.EAST));

        place.apply(world);
        assertInstanceOf(Miner.class, world.tile(2, 3).building());

        place.revert(world);
        assertNull(world.tile(2, 3).building(), "откат постройки на пустую клетку — снос");
        assertEquals(0, buildingCount(world));
    }

    @Test
    void removeThenRevertRestoresBuilding() {
        World world = world();
        world.place(4, 4, Building.create(Tool.CHEST, Direction.EAST));

        RemoveBuilding remove = new RemoveBuilding(new Cell(4, 4));
        remove.apply(world);
        assertNull(world.tile(4, 4).building());

        remove.revert(world);
        assertEquals(1, buildingCount(world), "снесённое здание вернулось");
    }

    @Test
    void revertRestoresOverwrittenSameTypeBuilding() {
        World world = world();
        // Ставим ленту на восток, затем перерисовываем её на север (тот же тип, другое
        // направление — мир такое замещает). Откат обязан вернуть именно восточную.
        new PlaceBuilding(new Cell(5, 5), Building.create(Tool.BELT, Direction.EAST)).apply(world);

        PlaceBuilding turn = new PlaceBuilding(
                new Cell(5, 5), Building.create(Tool.BELT, Direction.NORTH));
        turn.apply(world);
        assertEquals(Direction.NORTH, direction(world));

        turn.revert(world);
        assertEquals(Direction.EAST, direction(world), "откат вернул прежнее направление");
    }

    private static Direction direction(World world) {
        Building b = world.tile(5, 5).building();
        assertInstanceOf(Belt.class, b);
        return b.direction().orElseThrow();
    }

    @Test
    void compositeRevertsEveryChild() {
        World world = world();
        PlaceBuilding a = new PlaceBuilding(new Cell(0, 0), Building.create(Tool.BELT, Direction.EAST));
        PlaceBuilding b = new PlaceBuilding(new Cell(1, 0), Building.create(Tool.BELT, Direction.EAST));
        a.apply(world);
        b.apply(world);
        assertEquals(2, buildingCount(world));

        CompositeAction stroke = new CompositeAction(List.of(a, b));
        stroke.revert(world);
        assertEquals(0, buildingCount(world), "весь штрих отменился одной операцией");
    }

    @Test
    void historyUndoRedoRoundTrips() {
        World world = world();
        ActionHistory history = new ActionHistory();
        PlaceBuilding place = new PlaceBuilding(
                new Cell(7, 7), Building.create(Tool.FURNACE, Direction.EAST));

        history.push(applied(place, world));
        assertEquals(1, buildingCount(world));
        assertEquals(1, history.undoDepth());

        history.undo(world);
        assertEquals(0, buildingCount(world));
        assertEquals(1, history.redoDepth());

        history.redo(world);
        assertEquals(1, buildingCount(world), "повтор восстановил здание");
    }

    @Test
    void newActionClearsRedoBranch() {
        World world = world();
        ActionHistory history = new ActionHistory();
        history.push(applied(new PlaceBuilding(new Cell(1, 1),
                Building.create(Tool.MINER, Direction.EAST)), world));
        history.undo(world); // теперь в стеке повторов одна команда

        // Новое действие обязано обнулить ветку повторов — как в любом редакторе.
        history.push(applied(new PlaceBuilding(new Cell(2, 2),
                Building.create(Tool.MINER, Direction.EAST)), world));
        assertEquals(0, history.redoDepth());
    }

    /** Применить команду и вернуть её же — сахар для «выполнили, затем положили в историю». */
    private static PlaceBuilding applied(PlaceBuilding action, World world) {
        action.apply(world);
        return action;
    }
}

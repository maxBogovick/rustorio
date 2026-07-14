package com.rustorio.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.rustorio.core.Config;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;

/**
 * Фаза 1 игрового цикла: ввод.
 *
 * <p>Единственная задача — превратить действия игрока (клавиши, мышь) в
 * изменения состояния. Здесь НИЧЕГО не рисуется. Мир меняем только через методы
 * {@link GameState}/{@link com.rustorio.model.World}. Класс без состояния,
 * поэтому один статический метод.
 */
public final class InputHandler {

    private InputHandler() {
    }

    public static void handle(GameState game) {
        // Сразу запоминаем клетку под курсором — ею воспользуются и постройка
        // ниже, и отрисовка «призрака» (рендер читает game.hover, а не мышь).
        game.setHover(hoveredCell(game));

        // Выбор инструмента (клавиши 1..5).
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1)) {
            game.selectTool(Tool.MINER);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_2)) {
            game.selectTool(Tool.BELT);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_3)) {
            game.selectTool(Tool.FURNACE);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_4)) {
            game.selectTool(Tool.CHEST);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_5)) {
            game.selectTool(Tool.ASSEMBLER);
        }

        // Поворот и пауза.
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            game.rotateDirection();
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            game.togglePause();
        }

        // Строительство мышью. ЛКМ можно держать и вести линию лент.
        game.hover().ifPresent(cell -> {
            if (Gdx.input.isButtonPressed(Input.Buttons.LEFT)) {
                game.world().place(cell.x(), cell.y(),
                        Building.create(game.tool(), game.direction()));
            }
            if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT)) {
                game.world().remove(cell.x(), cell.y());
            }
        });
    }

    /**
     * Пиксель мыши → координата клетки (если курсор над полем).
     *
     * <p>{@code Gdx.input.getY()} измеряется от ВЕРХА окна (ось вниз) — ровно как
     * в Rust-версии, поэтому формула перевода совпадает один-в-один. Перевод в
     * систему Y-вверх нужен только отрисовке, вводу — нет.
     */
    private static Cell hoveredCell(GameState game) {
        float mx = Gdx.input.getX();
        float my = Gdx.input.getY();
        int tx = (int) Math.floor((mx - Config.OFFSET_X) / Config.TILE);
        int ty = (int) Math.floor((my - Config.OFFSET_Y) / Config.TILE);
        return game.world().inBounds(tx, ty) ? new Cell(tx, ty) : null;
    }
}

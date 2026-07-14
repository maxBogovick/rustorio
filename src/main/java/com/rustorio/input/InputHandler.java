package com.rustorio.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.rustorio.core.Config;
import com.rustorio.core.Tech;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Building;
import com.rustorio.model.Cell;
import org.jspecify.annotations.Nullable;

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

        // Выбор инструмента: идём по СПИСКУ инструментов, а не по руками написанной
        // лесенке «if (нажата 1) … if (нажата 5)». Раньше добавить здание и забыть
        // привязать ему клавишу можно было молча — компилятор не возражал, а здание
        // просто оказывалось недоступным. Теперь номер слота объявлен в самом Tool,
        // и забыть его нельзя: не скомпилируется.
        for (Tool tool : Tool.values()) {
            if (Gdx.input.isKeyJustPressed(keyForSlot(tool.hotkeySlot()))) {
                game.selectTool(tool);
            }
        }

        // Открыть технологию: F1..F4 по списку Tech — снова НЕ лесенка из if'ов, а цикл
        // по данным. Добавится пятая технология — клавиша появится сама.
        for (Tech tech : Tech.values()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.F1 + tech.ordinal())) {
                game.research().research(tech); // сам проверит, можно ли: очки, предпосылки
            }
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
     * Номер слота (1..9) → код клавиши libGDX.
     *
     * <p>Перевод живёт ЗДЕСЬ, а не в {@code Tool}: {@code Tool} лежит в {@code core},
     * которому запрещено знать про движок (это стережёт {@code ArchitectureTest}).
     * Слой ввода про движок знать обязан — вот пусть он и переводит.
     */
    private static int keyForSlot(int slot) {
        return Input.Keys.NUM_0 + slot;
    }

    /**
     * Пиксель мыши → координата клетки (если курсор над полем).
     *
     * <p>{@code Gdx.input.getY()} измеряется от ВЕРХА окна (ось вниз) — ровно как
     * в Rust-версии, поэтому формула перевода совпадает один-в-один. Перевод в
     * систему Y-вверх нужен только отрисовке, вводу — нет.
     */
    private static @Nullable Cell hoveredCell(GameState game) {
        float mx = Gdx.input.getX();
        float my = Gdx.input.getY();
        int tx = (int) Math.floor((mx - Config.OFFSET_X) / Config.TILE);
        int ty = (int) Math.floor((my - Config.OFFSET_Y) / Config.TILE);
        return game.world().inBounds(tx, ty) ? new Cell(tx, ty) : null;
    }
}

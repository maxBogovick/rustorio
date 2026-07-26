package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.render.HudState;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;

/**
 * Пауза, скорость симуляции и переключатель книги рецептов — view-state, которое читают {@code
 * GameScreen} (звать ли {@code world.tick()}) и {@code Renderer} (через {@link HudState}), но
 * которое не трогает ни камеру, ни постройку. Вынесено из {@link InputHandler} (P3-07,
 * BUG_FIX_PROGRESS.md): раньше этот класс попутно был ещё и view-model'ю HUD, теперь ей — этот.
 */
final class SimulationControls {

    /** Скорости симуляции по кругу — клавиши {@code [}/{@code ]} двигают индекс в этом массиве. */
    private static final int[] SPEEDS = {1, 2, 4};

    /** На паузе {@code GameScreen} не зовёт {@code world.tick()} вовсе. */
    private boolean paused;
    /** Индекс в {@link #SPEEDS} — во сколько раз чаще, чем обычно, тикает мир, пока не на паузе. */
    private int speedIndex;

    /**
     * Открыта ли книга рецептов ({@code Renderer} рисует панель поверх экрана, если {@code true})
     * — TAB переключает. Мир при этом продолжает тикать: книга — справочник, а не пауза; кто
     * хочет разглядывать рецепты без спешки, ставит паузу отдельно (SPACE).
     */
    private boolean showRecipeBook;

    void handle() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            paused = !paused;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) {
            speedIndex = Math.max(0, speedIndex - 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) {
            speedIndex = Math.min(SPEEDS.length - 1, speedIndex + 1);
        }
        // Книга рецептов (TAB): чистый переключатель показа, мира не касается вовсе.
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            showRecipeBook = !showRecipeBook;
        }
    }

    boolean isPaused() {
        return paused;
    }

    int speed() {
        return SPEEDS[speedIndex];
    }

    boolean showRecipeBook() {
        return showRecipeBook;
    }

    /** {@link InputHandler} supplies {@code selected}/{@code facing} — those are its own business, not ours. */
    HudState hudState(BuildingType selected, Direction facing) {
        return new HudState(selected, facing, paused, speed(), showRecipeBook);
    }
}

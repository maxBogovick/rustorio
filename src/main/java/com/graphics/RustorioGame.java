package com.graphics;

import com.badlogic.gdx.Game;
import com.graphics.screen.GameScreen;
import org.jspecify.annotations.Nullable;

/**
 * Корневой класс игры для libGDX.
 *
 * <p>{@link Game} — базовый класс libGDX, умеющий переключать {@code Screen}'ы.
 * Он платформо-независим: этот же класс запускается и на desktop, и (позже) на
 * Android — меняется только «лаунчер» ({@link com.graphics.Main}).
 */
public final class RustorioGame extends Game {

    /** Зерно карты руды из {@code --seed=}, или {@code null} — тогда карта фиксированная. */
    private final @Nullable Long oreSeed;

    public RustorioGame(@Nullable Long oreSeed) {
        this.oreSeed = oreSeed;
    }

    @Override
    public void create() {
        setScreen(oreSeed == null ? new GameScreen() : new GameScreen(oreSeed));
    }
}

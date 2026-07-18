package com.graphics;

import com.badlogic.gdx.Game;
import com.graphics.screen.GameScreen;

/**
 * Корневой класс игры для libGDX.
 *
 * <p>{@link Game} — базовый класс libGDX, умеющий переключать {@code Screen}'ы.
 * Он платформо-независим: этот же класс запускается и на desktop, и (позже) на
 * Android — меняется только «лаунчер» ({@link com.graphics.Main}).
 */
public final class RustorioGame extends Game {

    @Override
    public void create() {
        setScreen(new GameScreen());
    }
}

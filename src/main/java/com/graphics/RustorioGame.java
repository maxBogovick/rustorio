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

    /** {@code --dev} — стенд из {@code GameScreen.DevScene} вместо пустой карты; см. {@link com.graphics.Main}. */
    private final boolean devMode;

    public RustorioGame(@Nullable Long oreSeed, boolean devMode) {
        this.oreSeed = oreSeed;
        this.devMode = devMode;
    }

    @Override
    public void create() {
        // devMode игнорирует oreSeed: DevScene завязан на координаты РЕАЛЬНЫХ рудных пятен
        // фиксированной карты (PatchOreLayout.standard()), со случайной картой они бы не совпали.
        setScreen(devMode ? new GameScreen(true) : oreSeed == null ? new GameScreen() : new GameScreen(oreSeed));
    }
}

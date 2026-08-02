package com.graphics;

import com.badlogic.gdx.Game;
import com.graphics.screen.GameScreen;
import com.rustorio.api.content.ContentId;
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

    /** Id авторской карты из {@code --map=} (мод-контент, {@code content/maps/*.json}), или {@code null}. */
    private final @Nullable ContentId mapId;

    /** {@code --dev} — стенд из {@code GameScreen.DevScene} вместо пустой карты; см. {@link com.graphics.Main}. */
    private final boolean devMode;

    public RustorioGame(@Nullable Long oreSeed, @Nullable ContentId mapId, boolean devMode) {
        this.oreSeed = oreSeed;
        this.mapId = mapId;
        this.devMode = devMode;
    }

    @Override
    public void create() {
        // Приоритет тот же, что и раньше между --dev/--seed, плюс --map= встал между ними:
        // devMode игнорирует всё остальное (DevScene завязан на координаты РЕАЛЬНЫХ рудных пятен
        // фиксированной карты, PatchOreLayout.standard()), а --map= и --seed= вместе не имеют
        // смысла (обе выбирают ЧЕМ заполнена карта) — --map=, если он есть, побеждает --seed=.
        setScreen(devMode ? new GameScreen(true)
                : mapId != null ? new GameScreen(mapId)
                : oreSeed == null ? new GameScreen()
                : new GameScreen(oreSeed));
    }
}

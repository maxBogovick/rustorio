package com.graphics;

import com.badlogic.gdx.Game;
import com.graphics.screen.GameScreen;
import com.graphics.screen.MainMenuScreen;
import com.rustorio.api.content.ContentId;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModDirectories;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Корневой класс игры для libGDX.
 *
 * <p>{@link Game} — базовый класс libGDX, умеющий переключать {@code Screen}'ы.
 * Он платформо-независим: этот же класс запускается и на desktop, и (позже) на
 * Android — меняется только «лаунчер» ({@link com.graphics.Main}).
 *
 * <p>Mods are loaded exactly once, here, in {@link #create()} — not inside {@code GameScreen}
 * (which used to do it itself, before {@link MainMenuScreen} existed): both the menu (to list
 * maps/saves) and whichever {@code GameScreen} it eventually switches to need the same {@link
 * LoadedGame}, and loading it twice per launch would be both wasteful and a second, independent
 * {@link com.rustorio.api.registry.Registry} instance for no reason.
 */
public final class RustorioGame extends Game {

    /** Where every mod (including the built-in {@code rustorio} one) lives. */
    private static final Path MODS_ROOT = Path.of("resources", "mods");

    /** Зерно карты руды из {@code --seed=}, или {@code null} — тогда карта фиксированная. */
    private final @Nullable Long oreSeed;

    /** Id авторской карты из {@code --map=} (мод-контент, {@code content/maps/*.json}), или {@code null}. */
    private final @Nullable ContentId mapId;

    /** {@code --dev} — стенд из {@code GameScreen.DevScene} вместо пустой карты; см. {@link com.graphics.Main}. */
    private final boolean devMode;

    /**
     * {@code --no-menu} — go straight to the vanilla map instead of {@link MainMenuScreen}, with
     * no other flag set. Exists for {@code com.rustorio.editor}'s own "Play" button (see {@code
     * GameProcess#relaunch}): a dev preview with no specific map picked always meant "just show me
     * the default fixed map" — {@link MainMenuScreen} would otherwise sit in the way of that on
     * every single relaunch, which a mod author iterating on content doesn't want.
     */
    private final boolean skipMenu;

    public RustorioGame(@Nullable Long oreSeed, @Nullable ContentId mapId, boolean devMode, boolean skipMenu) {
        this.oreSeed = oreSeed;
        this.mapId = mapId;
        this.devMode = devMode;
        this.skipMenu = skipMenu;
    }

    @Override
    public void create() {
        List<Path> modDirectories = ModDirectories.discover(MODS_ROOT);
        LoadedGame loadedGame = ModLoader.loadAll(modDirectories);
        // Приоритет тот же, что и раньше между --dev/--seed, плюс --map= встал между ними:
        // devMode игнорирует всё остальное (DevScene завязан на координаты РЕАЛЬНЫХ рудных пятен
        // фиксированной карты, PatchOreLayout.standard()), а --map= и --seed= вместе не имеют
        // смысла (обе выбирают ЧЕМ заполнена карта) — --map=, если он есть, побеждает --seed=.
        // Ни один из трёх флагов не задан И --no-menu не стоит (обычный запуск с рабочего стола) —
        // теперь ведёт в MainMenuScreen вместо прежней прямой карты по умолчанию; остальные флаги
        // остаются dev-обходом меню (бенчмарки, ручная проверка конкретной карты/сида) и меню не трогают.
        setScreen(devMode ? new GameScreen(this, loadedGame, modDirectories, true)
                : mapId != null ? new GameScreen(this, loadedGame, modDirectories, mapId)
                : oreSeed != null ? new GameScreen(this, loadedGame, modDirectories, oreSeed)
                : skipMenu ? new GameScreen(this, loadedGame, modDirectories)
                : new MainMenuScreen(this, loadedGame, modDirectories));
    }
}

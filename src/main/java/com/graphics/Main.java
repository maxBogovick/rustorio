package com.graphics;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.rustorio.api.content.ContentId;
import org.jspecify.annotations.Nullable;

/**
 * Точка входа для десктопа (бэкенд LWJGL3).
 *
 * <p>Это «лаунчер»: единственное место, зависящее от конкретной платформы. Он
 * настраивает окно (размер фиксированный: мир смотрят через камеру, а не через
 * «окно по размеру поля») и запускает платформо-независимую {@link RustorioGame}.
 *
 * <p>{@code --seed=<число>} меняет карту руды: без флага игра использует
 * фиксированную {@link com.rustorio.domain.PatchOreLayout#standard()}, с флагом —
 * {@link com.rustorio.domain.RandomOreLayout}, сгенерированную из зерна (та же
 * стратегия {@link com.rustorio.domain.OreLayout}, реальная точка подмены, а не
 * только тестовая).
 *
 * <p>{@code --map=<namespace:path>} играет на авторской карте мода (нарисованной в
 * {@code com.rustorio.editor}, {@code content/maps/*.json}) — побеждает {@code --seed=}, если
 * заданы оба: обе выбирают, ЧЕМ заполнена карта, а не совместимы друг с другом.
 *
 * <p>{@code --dev} запускает игру с уже построенным стендом (по одному живому примеру каждого
 * здания плюс одно намеренно сломанное — {@code com.graphics.screen.DevScene}) вместо пустой
 * карты — не карточка из DEV_TASKS.md, прямая просьба: чтобы не пересобирать один и тот же
 * тестовый стенд руками при каждой проверке механики. Игнорирует {@code --seed=}/{@code --map=},
 * если заданы разом — координаты стенда завязаны на реальные рудные пятна фиксированной карты.
 *
 * <p>{@code --no-menu} пропускает {@code com.graphics.screen.MainMenuScreen} и идёт сразу на
 * карту по умолчанию, если ни один другой флаг не задан — используется {@code com.rustorio.editor}
 * ({@code GameProcess#relaunch}) для кнопки Play без выбранной карты: там меню — лишний клик на
 * каждый релонч, а не то, что мод-автор просил проверить.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rustorio");
        config.setWindowedMode(GfxConfig.WINDOW_W, GfxConfig.WINDOW_H);
        config.setResizable(true); // камера умеет пересчитываться под новый размер
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new RustorioGame(parseSeed(args), parseMap(args),
                hasFlag(args, "--dev"), hasFlag(args, "--no-menu")), config);
    }

    private static @Nullable Long parseSeed(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--seed=")) {
                return Long.parseLong(arg.substring("--seed=".length()));
            }
        }
        return null;
    }

    private static @Nullable ContentId parseMap(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--map=")) {
                return ContentId.of(arg.substring("--map=".length()));
            }
        }
        return null;
    }

    private static boolean hasFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag)) {
                return true;
            }
        }
        return false;
    }
}

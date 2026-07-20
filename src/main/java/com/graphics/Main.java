package com.graphics;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * Точка входа для десктопа (бэкенд LWJGL3).
 *
 * <p>Это «лаунчер»: единственное место, зависящее от конкретной платформы. Он
 * настраивает окно (размер фиксированный: мир смотрят через камеру, а не через
 * «окно по размеру поля») и запускает платформо-независимую {@link RustorioGame}.
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
        new Lwjgl3Application(new RustorioGame(), config);
    }
}

package com.rustorio;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.rustorio.core.Config;

/**
 * Точка входа для десктопа (бэкенд LWJGL3).
 *
 * <p>Это «лаунчер»: единственное место, зависящее от конкретной платформы. Он
 * настраивает окно (размер вычисляется из сетки, как в Rust-версии) и запускает
 * платформо-независимую {@link RustorioGame}.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Rustorio");
        config.setWindowedMode(Config.windowWidth(), Config.windowHeight());
        config.setResizable(false);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new RustorioGame(), config);
    }
}

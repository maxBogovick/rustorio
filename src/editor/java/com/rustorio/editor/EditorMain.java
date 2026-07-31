package com.rustorio.editor;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/**
 * Entry point for {@code ./gradlew editor}: a local HTTP server (JDK's own {@link HttpServer}, no
 * new dependency) exposing a small REST API over {@code resources/mods/rustorio/content/*.json}
 * plus its {@code textures/} directory, and serving the static frontend that talks to it from
 * {@code resources/editor/}. See each handler's own javadoc for its part of the API; {@link
 * ValidateHandler} and {@link RelaunchHandler} are the two that make "try it in the game" real —
 * the game itself now loads through {@code com.rustorio.mod.ModLoader} at startup (see {@code
 * com.graphics.screen.GameScreen}), so a saved edit here is exactly what the next relaunch sees.
 */
public final class EditorMain {

    private static final int PORT = 8787;

    private EditorMain() {
    }

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/api/items", new JsonCrudHandler(EditorPaths.ITEMS_DIR, "/api/items", Validators::item));
        server.createContext("/api/buildings", new JsonCrudHandler(EditorPaths.BUILDINGS_DIR, "/api/buildings", Validators::building));
        server.createContext("/api/recipes", new RecipesHandler());
        server.createContext("/api/textures", new TexturesHandler());
        server.createContext("/api/validate", new ValidateHandler());
        server.createContext("/api/relaunch", new RelaunchHandler());
        server.createContext("/", new StaticFileHandler(EditorPaths.FRONTEND_ROOT));

        server.start();
        System.out.println("Rustorio content editor: http://localhost:" + PORT + "/");
    }
}

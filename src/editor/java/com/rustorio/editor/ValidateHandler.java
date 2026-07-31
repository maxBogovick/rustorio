package com.rustorio.editor;

import com.rustorio.mod.ModDirectories;
import com.rustorio.mod.ModLoadException;
import com.rustorio.mod.ModLoader;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/**
 * {@code POST /api/validate} — runs the REAL {@link ModLoader#loadAll} over {@code resources/mods}
 * (same discovery {@code com.graphics.screen.GameScreen} now uses at startup, see {@link
 * ModDirectories}) and reports whether it would succeed. This is the one check in the whole editor
 * that can't be wrong: the per-field checks in {@link Validators} are convenience UI feedback, but
 * this endpoint literally IS the game's own loading path, just run without opening a window — the
 * frontend calls it before every {@code /api/relaunch} so a bad edit gets reported inline instead
 * of surfacing as a crashed game window a few seconds later.
 */
final class ValidateHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            EditorHttp.sendError(exchange, 405, "method not allowed: " + exchange.getRequestMethod());
            return;
        }
        try {
            ModLoader.loadAll(ModDirectories.discover(EditorPaths.MODS_ROOT));
            EditorHttp.sendJson(exchange, 200, "{\"ok\":true}");
        } catch (ModLoadException e) {
            EditorHttp.sendJson(exchange, 200, "{\"ok\":false,\"error\":" + EditorHttp.quote(e.getMessage()) + "}");
        }
    }
}

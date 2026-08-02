package com.rustorio.editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.util.List;

/**
 * {@code GET /api/mods} — every mod directory under {@code resources/mods}, sorted, as a plain
 * JSON array of ids (e.g. {@code ["rustorio","sandbox"]}). Backs the editor's mod switcher: the
 * frontend needs to know what's actually there before offering a {@code ?mod=} choice, the same
 * way {@link EditorHttp#modId} then validates whichever one gets picked.
 */
final class ModsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            EditorHttp.sendError(exchange, 405, "method not allowed: " + exchange.getRequestMethod());
            return;
        }
        List<String> modIds = EditorHttp.knownModIds();
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < modIds.size(); i++) {
            if (i > 0) {
                json.append(',');
            }
            json.append(EditorHttp.quote(modIds.get(i)));
        }
        json.append(']');
        EditorHttp.sendJson(exchange, 200, json.toString());
    }
}

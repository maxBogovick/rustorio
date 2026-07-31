package com.rustorio.editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/**
 * {@code POST /api/relaunch} — kill the previous game process (if any) and start a new
 * {@code ./gradlew run}, see {@link GameProcess}.<br>
 * {@code GET /api/relaunch}      — current {@link GameProcess.Status}, for the frontend to poll.<br>
 * {@code DELETE /api/relaunch}   — stop the game without starting a new one.
 */
final class RelaunchHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestMethod()) {
            case "POST" -> {
                GameProcess.relaunch();
                EditorHttp.sendJson(exchange, 202, toJson(GameProcess.status()));
            }
            case "GET" -> EditorHttp.sendJson(exchange, 200, toJson(GameProcess.status()));
            case "DELETE" -> {
                GameProcess.stop();
                EditorHttp.sendJson(exchange, 200, toJson(GameProcess.status()));
            }
            default -> EditorHttp.sendError(exchange, 405, "method not allowed: " + exchange.getRequestMethod());
        }
    }

    private static String toJson(GameProcess.Status status) {
        return "{\"state\":" + EditorHttp.quote(status.state())
                + ",\"exitCode\":" + (status.exitCode() == null ? "null" : status.exitCode())
                + ",\"tailLog\":" + EditorHttp.quote(status.tailLog()) + "}";
    }
}

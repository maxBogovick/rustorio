package com.rustorio.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/**
 * {@code POST /api/relaunch} — kill the previous game process (if any) and start a new
 * {@code ./gradlew run}, see {@link GameProcess}. An optional JSON body {@code {"map": "ns:path"}}
 * plays that authored map instead of the default fixed one — the frontend sends it when the Maps
 * tab has one open, so "draw it, hit Play" needs no separate step.<br>
 * {@code GET /api/relaunch}      — current {@link GameProcess.Status}, for the frontend to poll.<br>
 * {@code DELETE /api/relaunch}   — stop the game without starting a new one.
 */
final class RelaunchHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        switch (exchange.getRequestMethod()) {
            case "POST" -> {
                GameProcess.relaunch(readMapId(EditorHttp.readBody(exchange)));
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

    /** {@code null} if the body is empty, isn't a JSON object, or has no non-blank {@code "map"} field — every one of those means "play the default map," not an error. */
    private static String readMapId(byte[] body) {
        if (body.length == 0) {
            return null;
        }
        JsonNode root;
        try {
            root = EditorJson.MAPPER.readTree(body);
        } catch (IOException e) {
            return null;
        }
        if (root == null || !root.isObject() || !root.hasNonNull("map") || !root.get("map").isTextual()) {
            return null;
        }
        String map = root.get("map").asText();
        return map.isBlank() ? null : map;
    }

    private static String toJson(GameProcess.Status status) {
        return "{\"state\":" + EditorHttp.quote(status.state())
                + ",\"exitCode\":" + (status.exitCode() == null ? "null" : status.exitCode())
                + ",\"tailLog\":" + EditorHttp.quote(status.tailLog()) + "}";
    }
}

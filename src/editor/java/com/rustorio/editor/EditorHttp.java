package com.rustorio.editor;

import com.rustorio.mod.ModDirectories;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

/** Small shared request/response helpers every handler in this package needs. */
final class EditorHttp {

    private EditorHttp() {
    }

    /**
     * Which mod a content request targets — the {@code ?mod=} query parameter, or {@link
     * EditorPaths#DEFAULT_MOD_ID} if the request named none (every caller from before the mod
     * switcher existed). Checked against the real directories under {@link EditorPaths#MODS_ROOT}
     * so a typo'd mod id fails loudly (400) instead of silently reading/writing an empty,
     * never-loaded directory tree.
     */
    static String modId(HttpExchange exchange) {
        String raw = queryParam(exchange, "mod");
        String modId = raw == null || raw.isBlank() ? EditorPaths.DEFAULT_MOD_ID : raw;
        if (!knownModIds().contains(modId)) {
            throw new ApiException(400, "unknown mod '" + modId + "' — expected one of " + knownModIds());
        }
        return modId;
    }

    static List<String> knownModIds() {
        return ModDirectories.discover(EditorPaths.MODS_ROOT).stream()
                .map(p -> p.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
    }

    static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0 && pair.substring(0, eq).equals(name)) {
                return URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    static byte[] readBody(HttpExchange exchange) throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            return in.readAllBytes();
        }
    }

    /** The path segment after {@code prefix} (e.g. {@code "iron_plate"} for {@code /api/items/iron_plate} against prefix {@code "/api/items"}) — {@code null} if the request named nothing past the prefix. */
    static String keyAfter(HttpExchange exchange, String prefix) {
        String path = exchange.getRequestURI().getPath();
        if (path.length() <= prefix.length()) {
            return null;
        }
        String rest = path.substring(prefix.length());
        if (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        return rest.isEmpty() ? null : rest;
    }

    static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void sendBytes(HttpExchange exchange, int status, String contentType, byte[] bytes) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    static void sendEmpty(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    static void sendError(HttpExchange exchange, int status, String message) throws IOException {
        sendJson(exchange, status, "{\"error\":" + quote(message) + "}");
    }

    static String quote(String s) {
        StringBuilder out = new StringBuilder(s.length() + 2).append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> out.append(c);
            }
        }
        return out.append('"').toString();
    }
}

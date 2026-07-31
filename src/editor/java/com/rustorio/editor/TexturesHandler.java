package com.rustorio.editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Lists and serves the sprites a building's {@code "texture"} field can legally name, and lets the
 * editor add new ones.
 *
 * <p>Two sources, same as the real game ends up packing (see {@code Textures.loadFrom}, wired into
 * {@code GameScreen} for this exact purpose): the 12 built-in vanilla sprites (fixed files under
 * {@code resources/}, duplicated here as a small lookup table purely for this picker's thumbnails —
 * {@link ValidateHandler} is what actually re-checks a {@code texture} reference for real before
 * any relaunch, so this table going stale is a UI inconvenience, not a correctness gap), and
 * whatever {@code .png} files already live under {@code resources/mods/rustorio/textures/}, each
 * one becoming {@code "rustorio:<filename>"} once {@link com.graphics.render.Textures#loadFrom}
 * scans that directory at game startup.
 *
 * <p>{@code GET  /api/textures}                    — {@code {"vanilla":[...], "mod":[...]}}, each entry {@code {"id":…, "assetUrl":…}}<br>
 * {@code GET  /api/textures/asset?kind=&name=}      — the PNG bytes for one entry<br>
 * {@code POST /api/textures/{name}}                 — raw PNG body, saved as {@code textures/{name}.png} (becomes {@code rustorio:{name}})
 */
final class TexturesHandler implements HttpHandler {

    private static final String PREFIX = "/api/textures";
    private static final Pattern NAME = Pattern.compile("[a-z0-9_]+");

    /** Mirrors {@code com.graphics.render.TextureIndex#vanilla()} — see the class javadoc for why this copy is safe to go stale. */
    private static final Map<String, String> VANILLA = new LinkedHashMap<>();

    static {
        VANILLA.put("rustorio:miner", "resources/miner_1.png");
        VANILLA.put("rustorio:chest", "resources/chest.png");
        VANILLA.put("rustorio:furnace_hot", "resources/furnace_on.png");
        VANILLA.put("rustorio:furnace_cold", "resources/furnace_off.png");
        VANILLA.put("rustorio:belt_empty", "resources/belt_1.png");
        VANILLA.put("rustorio:belt_full", "resources/belt_2.png");
        VANILLA.put("rustorio:splitter", "resources/branch_1.png");
        VANILLA.put("rustorio:filter", "resources/branch_2.png");
        VANILLA.put("rustorio:inserter", "resources/branch_3.png");
        VANILLA.put("rustorio:underground_in", "resources/underground_in.png");
        VANILLA.put("rustorio:underground_out", "resources/underground_out.png");
        VANILLA.put("rustorio:assembler", "resources/assembler.png");
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ApiException e) {
            EditorHttp.sendError(exchange, e.status, e.getMessage());
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        if (path.equals(PREFIX + "/asset") && method.equals("GET")) {
            asset(exchange);
            return;
        }
        if (path.equals(PREFIX) && method.equals("GET")) {
            list(exchange);
            return;
        }
        String key = EditorHttp.keyAfter(exchange, PREFIX);
        if (key != null && method.equals("POST")) {
            upload(exchange, key);
            return;
        }
        EditorHttp.sendError(exchange, 405, "method not allowed: " + method + " " + path);
    }

    private void list(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("{\"vanilla\":[");
        appendEntries(json, VANILLA.keySet(), "vanilla");
        json.append("],\"mod\":[");
        appendEntries(json, modTextureIds(), "mod");
        json.append("]}");
        EditorHttp.sendJson(exchange, 200, json.toString());
    }

    private static void appendEntries(StringBuilder json, Iterable<String> ids, String kind) {
        boolean first = true;
        for (String id : ids) {
            if (!first) {
                json.append(',');
            }
            first = false;
            String assetUrl = PREFIX + "/asset?kind=" + kind + "&name=" + id;
            json.append("{\"id\":").append(EditorHttp.quote(id)).append(",\"assetUrl\":").append(EditorHttp.quote(assetUrl)).append('}');
        }
    }

    private static java.util.List<String> modTextureIds() {
        if (!Files.isDirectory(EditorPaths.TEXTURES_DIR)) {
            return java.util.List.of();
        }
        try (var files = Files.list(EditorPaths.TEXTURES_DIR)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".png"))
                    .map(p -> {
                        String name = p.getFileName().toString();
                        return "rustorio:" + name.substring(0, name.length() - ".png".length());
                    })
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    private void asset(HttpExchange exchange) throws IOException {
        Map<String, String> query = parseQuery(exchange.getRequestURI().getRawQuery());
        String kind = query.get("kind");
        String name = query.get("name");
        if (kind == null || name == null) {
            throw new ApiException(400, "asset needs ?kind=vanilla|mod&name=<id>");
        }
        Path file = switch (kind) {
            case "vanilla" -> {
                String relative = VANILLA.get(name);
                if (relative == null) {
                    throw new ApiException(404, "no vanilla texture '" + name + "'");
                }
                yield Path.of(relative);
            }
            case "mod" -> EditorPaths.TEXTURES_DIR.resolve(bareName(name) + ".png");
            default -> throw new ApiException(400, "kind must be 'vanilla' or 'mod'");
        };
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, "no such texture file: " + file);
        }
        EditorHttp.sendBytes(exchange, 200, "image/png", Files.readAllBytes(file));
    }

    private void upload(HttpExchange exchange, String name) throws IOException {
        if (!NAME.matcher(name).matches()) {
            throw new ApiException(400, "texture name must match [a-z0-9_]+: \"" + name + "\"");
        }
        byte[] body = EditorHttp.readBody(exchange);
        if (body.length < 8 || (body[0] & 0xFF) != 0x89 || body[1] != 'P' || body[2] != 'N' || body[3] != 'G') {
            throw new ApiException(400, "request body doesn't look like a PNG file");
        }
        Path file = EditorPaths.TEXTURES_DIR.resolve(name + ".png");
        Files.createDirectories(EditorPaths.TEXTURES_DIR);
        Files.write(file, body);
        EditorHttp.sendJson(exchange, 200, "{\"id\":" + EditorHttp.quote("rustorio:" + name) + "}");
    }

    private static String bareName(String contentId) {
        int colon = contentId.indexOf(':');
        return colon >= 0 ? contentId.substring(colon + 1) : contentId;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> result = new LinkedHashMap<>();
        if (query == null) {
            return result;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0) {
                result.put(pair.substring(0, eq), URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return result;
    }
}

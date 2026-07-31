package com.rustorio.editor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CRUD over {@code content/recipes/*.json} — unlike items/buildings ({@link JsonCrudHandler}) a
 * recipe file carries no identifying field of its own ({@code {"ingredients":…, "output":…,
 * "time":…, "kind":…}}, see {@code RecipeJsonLoader}), so its filename IS its identity, named by
 * this API's own {@code /api/recipes/{file}} key rather than anything inside the JSON body.
 *
 * <p>{@code GET  /api/recipes}          — list every recipe, each tagged with its own {@code "file"}<br>
 * {@code GET  /api/recipes/{file}}      — one recipe<br>
 * {@code POST /api/recipes?file={name}} — create {@code {name}.json}; 409 if it exists<br>
 * {@code PUT  /api/recipes/{file}}      — replace it<br>
 * {@code DELETE /api/recipes/{file}}    — remove it
 */
final class RecipesHandler implements HttpHandler {

    private static final String PREFIX = "/api/recipes";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (ApiException e) {
            EditorHttp.sendError(exchange, e.status, e.getMessage());
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        String key = EditorHttp.keyAfter(exchange, PREFIX);
        switch (exchange.getRequestMethod()) {
            case "GET" -> {
                if (key == null) {
                    listAll(exchange);
                } else {
                    getOne(exchange, key);
                }
            }
            case "POST" -> create(exchange);
            case "PUT" -> update(exchange, requireKey(key));
            case "DELETE" -> remove(exchange, requireKey(key));
            default -> EditorHttp.sendError(exchange, 405, "method not allowed: " + exchange.getRequestMethod());
        }
    }

    private static String requireKey(String key) {
        if (key == null) {
            throw new ApiException(400, "this method needs a /{file} in the URL");
        }
        return key;
    }

    private void listAll(HttpExchange exchange) throws IOException {
        ArrayNode all = EditorJson.MAPPER.createArrayNode();
        for (Path file : EditorJson.listJsonFilesSorted(EditorPaths.RECIPES_DIR)) {
            ObjectNode node = ((ObjectNode) EditorJson.readTree(file)).deepCopy();
            node.put("file", fileKey(file));
            all.add(node);
        }
        EditorHttp.sendJson(exchange, 200, all.toString());
    }

    private void getOne(HttpExchange exchange, String key) throws IOException {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, "no recipe named '" + key + "'");
        }
        ObjectNode node = ((ObjectNode) EditorJson.readTree(file)).deepCopy();
        node.put("file", key);
        EditorHttp.sendJson(exchange, 200, node.toString());
    }

    private void create(HttpExchange exchange) throws IOException {
        String name = queryParam(exchange, "file");
        if (name == null || name.isBlank()) {
            throw new ApiException(400, "POST /api/recipes needs a ?file=<name> query parameter");
        }
        Path file = fileFor(name);
        if (Files.exists(file)) {
            throw new ApiException(409, "'" + name + "' already exists");
        }
        ObjectNode body = EditorJson.readBodyAsObject(EditorHttp.readBody(exchange));
        body.remove("file");
        Validators.recipe(body);
        EditorJson.write(file, body);
        EditorHttp.sendJson(exchange, 201, body.toString());
    }

    private void update(HttpExchange exchange, String key) throws IOException {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, "no recipe named '" + key + "'");
        }
        ObjectNode body = EditorJson.readBodyAsObject(EditorHttp.readBody(exchange));
        body.remove("file");
        Validators.recipe(body);
        EditorJson.write(file, body);
        EditorHttp.sendJson(exchange, 200, body.toString());
    }

    private void remove(HttpExchange exchange, String key) throws IOException {
        if (!EditorJson.delete(fileFor(key))) {
            throw new ApiException(404, "no recipe named '" + key + "'");
        }
        EditorHttp.sendEmpty(exchange, 204);
    }

    private static String queryParam(HttpExchange exchange, String name) {
        String query = exchange.getRequestURI().getRawQuery();
        if (query == null) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq >= 0 && pair.substring(0, eq).equals(name)) {
                return java.net.URLDecoder.decode(pair.substring(eq + 1), java.nio.charset.StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static Path fileFor(String key) {
        return EditorPaths.RECIPES_DIR.resolve(key + ".json");
    }

    private static String fileKey(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }
}

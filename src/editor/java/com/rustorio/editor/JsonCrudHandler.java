package com.rustorio.editor;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * CRUD over one {@code content/<kind>/*.json} directory whose entries carry their own identity in
 * a {@code "path"} field (items, buildings — NOT recipes, which have no such field; see {@link
 * RecipesHandler}) — filename is always {@code <path>.json}, the same convention every file already
 * checked into {@code resources/mods/rustorio/content/} follows.
 *
 * <p>{@code GET  {prefix}}         — list every entry, raw JSON, as a JSON array<br>
 * {@code GET  {prefix}/{path}}     — one entry<br>
 * {@code POST {prefix}}            — create; body's own {@code "path"} names the new file; 409 if it exists<br>
 * {@code PUT  {prefix}/{path}}     — replace the entry at {@code path}; a different {@code "path"} in the body renames it<br>
 * {@code DELETE {prefix}/{path}}   — remove it
 */
final class JsonCrudHandler implements HttpHandler {

    private final Path dir;
    private final String prefix;
    private final Consumer<ObjectNode> validate;

    JsonCrudHandler(Path dir, String prefix, Consumer<ObjectNode> validate) {
        this.dir = dir;
        this.prefix = prefix;
        this.validate = validate;
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
        String key = EditorHttp.keyAfter(exchange, prefix);
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
            throw new ApiException(400, "this method needs a /{path} in the URL");
        }
        return key;
    }

    private void listAll(HttpExchange exchange) throws IOException {
        ArrayNode all = EditorJson.MAPPER.createArrayNode();
        for (Path file : EditorJson.listJsonFilesSorted(dir)) {
            all.add(EditorJson.readTree(file));
        }
        EditorHttp.sendJson(exchange, 200, all.toString());
    }

    private void getOne(HttpExchange exchange, String key) throws IOException {
        Path file = fileFor(key);
        if (!Files.isRegularFile(file)) {
            throw new ApiException(404, "no entry named '" + key + "'");
        }
        EditorHttp.sendJson(exchange, 200, EditorJson.readTree(file).toString());
    }

    private void create(HttpExchange exchange) throws IOException {
        ObjectNode body = EditorJson.readBodyAsObject(EditorHttp.readBody(exchange));
        validate.accept(body);
        String path = body.get("path").asText();
        Path file = fileFor(path);
        if (Files.exists(file)) {
            throw new ApiException(409, "'" + path + "' already exists");
        }
        EditorJson.write(file, body);
        EditorHttp.sendJson(exchange, 201, body.toString());
    }

    private void update(HttpExchange exchange, String key) throws IOException {
        Path existing = fileFor(key);
        if (!Files.isRegularFile(existing)) {
            throw new ApiException(404, "no entry named '" + key + "'");
        }
        ObjectNode body = EditorJson.readBodyAsObject(EditorHttp.readBody(exchange));
        validate.accept(body);
        String newPath = body.get("path").asText();
        Path target = fileFor(newPath);
        if (!newPath.equals(key) && Files.exists(target)) {
            throw new ApiException(409, "'" + newPath + "' already exists");
        }
        EditorJson.write(target, body);
        if (!newPath.equals(key)) {
            EditorJson.delete(existing);
        }
        EditorHttp.sendJson(exchange, 200, body.toString());
    }

    private void remove(HttpExchange exchange, String key) throws IOException {
        if (!EditorJson.delete(fileFor(key))) {
            throw new ApiException(404, "no entry named '" + key + "'");
        }
        EditorHttp.sendEmpty(exchange, 204);
    }

    private Path fileFor(String path) {
        return dir.resolve(path + ".json");
    }
}

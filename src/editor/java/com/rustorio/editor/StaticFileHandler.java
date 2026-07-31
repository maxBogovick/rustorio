package com.rustorio.editor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the frontend's static files ({@code resources/editor/}) — no build step, plain files. */
final class StaticFileHandler implements HttpHandler {

    private final Path root;

    StaticFileHandler(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            EditorHttp.sendError(exchange, 405, "method not allowed: " + exchange.getRequestMethod());
            return;
        }
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath.equals("/")) {
            requestPath = "/index.html";
        }
        // Reject anything that would climb out of `root` (e.g. "/../build.gradle") BEFORE
        // resolving it against the filesystem.
        if (requestPath.contains("..")) {
            EditorHttp.sendError(exchange, 400, "invalid path");
            return;
        }
        Path resolved = root.resolve(requestPath.substring(1)).normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            EditorHttp.sendError(exchange, 404, "not found: " + requestPath);
            return;
        }
        byte[] bytes = Files.readAllBytes(resolved);
        exchange.getResponseHeaders().set("Content-Type", contentType(resolved));
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String contentType(Path file) {
        String name = file.getFileName().toString();
        if (name.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (name.endsWith(".js")) {
            return "text/javascript; charset=utf-8";
        }
        if (name.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (name.endsWith(".png")) {
            return "image/png";
        }
        return "application/octet-stream";
    }
}

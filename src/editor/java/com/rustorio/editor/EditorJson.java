package com.rustorio.editor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Small shared JSON read/write helpers for the content CRUD handlers — the write half {@code
 * com.rustorio.mod.JsonNodes} (package-private, read-only) doesn't have, and isn't extended with:
 * that class exists for the game's actual loading path, this one for the editor's own.
 */
final class EditorJson {

    static final ObjectMapper MAPPER = new ObjectMapper();

    private EditorJson() {
    }

    static List<Path> listJsonFilesSorted(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static JsonNode readTree(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static ObjectNode readBodyAsObject(byte[] body) {
        JsonNode node;
        try {
            node = MAPPER.readTree(body);
        } catch (IOException e) {
            throw new ApiException(400, "request body is not valid JSON: " + e.getMessage());
        }
        if (node == null || !(node.isObject())) {
            throw new ApiException(400, "request body must be a JSON object");
        }
        return (ObjectNode) node;
    }

    static void write(Path file, ObjectNode node) {
        try {
            Files.createDirectories(file.getParent());
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file.toFile(), node);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code true} if a file existed and was removed. */
    static boolean delete(Path file) {
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String requireText(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual() || value.asText().isBlank()) {
            throw new ApiException(400, "missing or invalid required text field '" + field + "'");
        }
        return value.asText();
    }

    static int requireInt(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            throw new ApiException(400, "missing or invalid required integer field '" + field + "'");
        }
        return value.asInt();
    }

    /**
     * Unlike {@link #requireText}, accepts what a label field is actually allowed to hold: a plain
     * string, OR a localized {@code {en,ru,...}} object (what every vanilla item/building file
     * already carries, and what {@code JsonNodes.requireLocalizedText} reads on the engine side).
     * {@code requireText} rejected the object form outright, which meant saving any vanilla
     * content with a translated label through the editor 400'd unconditionally.
     */
    static void requireLabel(ObjectNode node, String field) {
        JsonNode value = node.get(field);
        if (value != null && value.isTextual() && !value.asText().isBlank()) {
            return;
        }
        if (value != null && value.isObject()) {
            for (JsonNode entry : value) {
                if (entry.isTextual() && !entry.asText().isBlank()) {
                    return;
                }
            }
        }
        throw new ApiException(400, "'" + field + "' must be a non-blank string or a localized "
                + "{en,ru,...} object with at least one non-blank value");
    }
}

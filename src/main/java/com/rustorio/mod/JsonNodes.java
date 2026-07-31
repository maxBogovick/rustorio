package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Small shared reading helpers for the three {@code content/*}<!---->{@code /*.json} loaders
 * (items/recipes/buildings) — every one of them needs "read this file, require this field, name the
 * file and field on failure". Deliberately separate from {@link ModJsonReader}'s own private
 * helpers: that class already has its own working, tested field-reading code for {@code mod.json}
 * specifically, and touching it just to share a few lines with a later, unrelated caller isn't
 * worth the churn.
 */
final class JsonNodes {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNodes() {
    }

    static JsonNode readTree(Path file) {
        try {
            return MAPPER.readTree(file.toFile());
        } catch (IOException e) {
            throw new ModLoadException(file + ": could not read JSON (" + e.getMessage() + ")", e);
        }
    }

    static String requireText(JsonNode node, String field, Path file) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isTextual()) {
            throw new ModLoadException(file + ": missing or non-string required field '" + field + "'");
        }
        return value.asText();
    }

    static int requireInt(JsonNode node, String field, Path file) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            throw new ModLoadException(file + ": missing or non-integer required field '" + field + "'");
        }
        return value.asInt();
    }

    static boolean optionalBoolean(JsonNode node, String field, boolean defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asBoolean(defaultValue);
    }

    static List<String> requireTextArray(JsonNode node, String field, Path file) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray() || value.isEmpty()) {
            throw new ModLoadException(file + ": missing or empty required array field '" + field + "'");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual()) {
                throw new ModLoadException(file + ": every element of array field '" + field + "' must be a string");
            }
            result.add(item.asText());
        }
        return result;
    }

    /** Every {@code *.json} file directly inside {@code dir}, sorted by filename — empty (not an error) if {@code dir} doesn't exist at all. */
    static List<Path> listJsonFilesSorted(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            return entries.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            throw new ModLoadException(dir + ": could not list content files (" + e.getMessage() + ")", e);
        }
    }
}

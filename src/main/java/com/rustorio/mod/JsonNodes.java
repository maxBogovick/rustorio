package com.rustorio.mod;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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

    /**
     * Reads a display label that may be either a plain string (used as-is, any locale) or an
     * object of {@code {"en": "...", "ru": "..."}} — resolved against {@code locale} (callers pass
     * {@link ContentLocale#current()}; taken as a parameter, not read internally, so this stays a
     * pure function of its arguments — easy to test at any locale without depending on a value
     * fixed once at class-load time for the whole JVM), falling back to {@code "en"} if {@code
     * locale} has no entry, and to {@code idFallback} (the content's own {@link
     * com.rustorio.api.content.ContentId}, as text) if even THAT is missing. The plain-string form
     * is untouched by locale entirely — existing content written before this format existed keeps
     * reading exactly the same regardless of {@code locale}.
     */
    static String requireLocalizedText(JsonNode node, String field, Path file, String idFallback, String locale) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new ModLoadException(file + ": missing required field '" + field + "'");
        }
        if (value.isTextual()) {
            return value.asText();
        }
        if (value.isObject()) {
            JsonNode localized = value.get(locale);
            if (localized != null && localized.isTextual()) {
                return localized.asText();
            }
            JsonNode english = value.get("en");
            if (english != null && english.isTextual()) {
                return english.asText();
            }
            return idFallback;
        }
        throw new ModLoadException(file + ": field '" + field + "' must be a string or an object of locale -> string");
    }

    static int requireInt(JsonNode node, String field, Path file) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || !value.isIntegralNumber()) {
            throw new ModLoadException(file + ": missing or non-integer required field '" + field + "'");
        }
        return value.asInt();
    }

    /**
     * Rejects any field {@code node} carries that is not in {@code allowed}, naming the file, the
     * offending key and what was expected.
     *
     * <p>Without this a misspelled key is simply not read: {@code "fluidOutut"} used to produce a
     * pump that loaded fine and silently pumped nothing, which is the worst kind of mod bug —
     * nothing is wrong anywhere, the machine just does not work. Every other error in these loaders
     * already names the mod, the file and the field, and a typo deserves the same treatment.
     *
     * <p>The cost is deliberate: a mod carrying a field the engine has never heard of now fails to
     * load rather than being quietly tolerated. That is the trade the owner chose, and it is why
     * {@code allowed} has to include keys nothing reads — an item's {@code "tool"} is authoring
     * metadata for the content editor, real and intended, just not the game's business.
     *
     * @param where what is being read ({@code "item"}, {@code "building"}), for the message
     */
    static void rejectUnknownFields(JsonNode node, Path file, String where, List<String> allowed) {
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            String name = names.next();
            if (!allowed.contains(name)) {
                throw new ModLoadException(file + ": unknown " + where + " field '" + name
                        + "' (expected one of " + allowed + ") — check the spelling: a field this "
                        + "loader does not know would otherwise be read by nothing at all");
            }
        }
    }

    /**
     * {@code field} as a packed {@code 0xRRGGBB} int, written {@code "#RRGGBB"} — the form both an
     * item's and a fluid's color take. Shared here rather than parsed in each loader: the two would
     * otherwise disagree about which spellings are legal the first time one of them was fixed.
     */
    static int requireColorRgb(JsonNode node, String field, Path file) {
        String text = requireText(node, field, file);
        if (text.length() != 7 || text.charAt(0) != '#') {
            throw new ModLoadException(file + ": field '" + field + "' must be \"#RRGGBB\": \"" + text + "\"");
        }
        try {
            return Integer.parseInt(text.substring(1), 16);
        } catch (NumberFormatException e) {
            throw new ModLoadException(file + ": field '" + field + "' is not valid hex: \"" + text + "\"");
        }
    }

    /** {@code field} as text, or {@code defaultValue} when it is absent, null or not a string. */
    static String optionalText(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isTextual() ? defaultValue : value.asText();
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

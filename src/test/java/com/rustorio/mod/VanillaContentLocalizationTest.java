package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Proves the REAL {@code resources/mods/rustorio} files resolve correctly at both locales the
 * roadmap names (en, ru) — not synthetic fixtures. Doesn't drive a full {@link ModLoader#loadAll}
 * under {@code ru}: {@link ContentLocale#current()} is read once per JVM at class-load time (see
 * its own javadoc), so flipping it reliably mid-test-suite would need a separate forked JVM, which
 * would mean touching {@code build.gradle} — out of bounds. {@code en} (the JVM's actual current
 * locale for this whole test suite) is already exercised end to end by {@code
 * VanillaAsModParityTest}; this test isolates exactly the part that varies by locale
 * ({@link JsonNodes#requireLocalizedText}) and proves it against the real files at BOTH locales
 * directly, without needing to change the process-wide value at all.
 */
class VanillaContentLocalizationTest {

    private static final Path RUSTORIO_ITEMS = Path.of("resources", "mods", "rustorio", "content", "items");
    private static final Path RUSTORIO_BUILDINGS = Path.of("resources", "mods", "rustorio", "content", "buildings");

    @Test
    void ironOreLabelResolvesAtBothLocales() {
        JsonNode node = JsonNodes.readTree(RUSTORIO_ITEMS.resolve("iron_ore.json"));

        assertEquals("Iron Ore", JsonNodes.requireLocalizedText(node, "label", RUSTORIO_ITEMS, "fallback", "en"));
        assertEquals("Железная руда", JsonNodes.requireLocalizedText(node, "label", RUSTORIO_ITEMS, "fallback", "ru"));
    }

    @Test
    void everyVanillaItemFileHasBothLocalesAndNeitherIsBlank() {
        for (Path file : JsonNodes.listJsonFilesSorted(RUSTORIO_ITEMS)) {
            JsonNode node = JsonNodes.readTree(file);
            String en = JsonNodes.requireLocalizedText(node, "label", file, "fallback", "en");
            String ru = JsonNodes.requireLocalizedText(node, "label", file, "fallback", "ru");
            assertTrueNotBlank(en, file);
            assertTrueNotBlank(ru, file);
        }
    }

    @Test
    void everyVanillaBuildingFileHasBothLocalesAndNeitherIsBlank() {
        for (Path file : JsonNodes.listJsonFilesSorted(RUSTORIO_BUILDINGS)) {
            JsonNode node = JsonNodes.readTree(file);
            String en = JsonNodes.requireLocalizedText(node, "label", file, "fallback", "en");
            String ru = JsonNodes.requireLocalizedText(node, "label", file, "fallback", "ru");
            assertTrueNotBlank(en, file);
            assertTrueNotBlank(ru, file);
        }
    }

    private static void assertTrueNotBlank(String text, Path file) {
        if (text.isBlank() || text.equals("fallback")) {
            throw new AssertionError(file + ": label resolved to a blank or the fallback placeholder — a real translation is missing");
        }
    }
}

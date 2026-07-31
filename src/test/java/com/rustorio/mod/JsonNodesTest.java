package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link JsonNodes#requireLocalizedText} — the label format {@code ItemJsonLoader}/{@code
 * BuildingJsonLoader} share (Phase 8). {@code locale} is a plain parameter, not read off {@link
 * ContentLocale} internally, specifically so every locale can be exercised here without depending
 * on the one value fixed for the whole JVM at class-load time.
 */
class JsonNodesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void plainStringIsUsedAsIsRegardlessOfLocale() throws IOException {
        JsonNode node = parse("""
                { "label": "Iron Ore" }
                """);

        assertEquals("Iron Ore", JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "en"));
        assertEquals("Iron Ore", JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "ru"));
    }

    @Test
    void objectFormResolvesTheRequestedLocale() throws IOException {
        JsonNode node = parse("""
                { "label": { "en": "Iron Ore", "ru": "Железная руда" } }
                """);

        assertEquals("Железная руда", JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "ru"));
        assertEquals("Iron Ore", JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "en"));
    }

    @Test
    void fallsBackToEnglishWhenTheRequestedLocaleIsMissing() throws IOException {
        JsonNode node = parse("""
                { "label": { "en": "Iron Ore" } }
                """);

        assertEquals("Iron Ore", JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "de"));
    }

    @Test
    void fallsBackToTheContentIdWhenNeitherTheLocaleNorEnglishArePresent() throws IOException {
        JsonNode node = parse("""
                { "label": { "ru": "Железная руда" } }
                """);

        assertEquals("rustorio:iron_ore", JsonNodes.requireLocalizedText(node, "label", file(), "rustorio:iron_ore", "de"));
    }

    @Test
    void missingFieldThrows() throws IOException {
        JsonNode node = parse("{ }");

        assertThrows(ModLoadException.class, () -> JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "en"));
    }

    @Test
    void nonStringNonObjectThrows() throws IOException {
        JsonNode node = parse("""
                { "label": 5 }
                """);

        assertThrows(ModLoadException.class, () -> JsonNodes.requireLocalizedText(node, "label", file(), "fallback", "en"));
    }

    private JsonNode parse(String json) throws IOException {
        return MAPPER.readTree(json);
    }

    private Path file() {
        return tempDir.resolve("test.json");
    }
}

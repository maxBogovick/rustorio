package com.graphics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link HudText} — what the HUD can draw and how it folds what it cannot.
 *
 * <p>libGDX's built-in font has 168 glyphs, all inside Latin-1. A character it lacks is not drawn
 * at all, so text made of them leaves a blank the player cannot tell from an empty panel. That
 * stopped being hypothetical the moment a mod started showing fetched web pages: an HTML parser
 * decodes {@code &mdash;} into a real em dash, and a real em dash is exactly such a character.
 */
class HudTextTest {

    /** Typographic punctuation folds to its ASCII cousin, because a hyphen where a dash belongs reads correctly and a blank does not. */
    @Test
    void punctuationTheFontLacksIsFoldedToItsAsciiCousin() {
        assertEquals("Hacker News - Front Page", fit("Hacker News — Front Page"));
        assertEquals("it's \"quoted\"", fit("it’s “quoted”"));
        assertEquals("and so on.", fit("and so on…"));
    }

    /** A space is a space however it was typed — a non-breaking one must not become a question mark, and must be a place a row may break. */
    @Test
    void exoticSpacesBecomeOrdinarySpacesRatherThanQuestionMarks() {
        assertEquals("two words", fit("two words"));
        assertEquals("two words", fit("two ​words"));
    }

    /**
     * A script the font has no glyphs for becomes question marks, not nothing. Both are wrong; only
     * one of them tells the player there is text here. The real fix is a font that has the glyphs,
     * which is a decision about what this game ships, not something to slip into a wrap routine.
     */
    @Test
    void charactersWithNoGlyphAtAllBecomeQuestionMarksInsteadOfDisappearing() {
        String folded = fit("Привет");

        assertEquals(6, folded.length(), "one mark per character, so the text's presence is visible: " + folded);
        assertTrue(folded.chars().allMatch(c -> c == '?'), folded);
    }

    /** Runs {@code text} through the same path a panel row takes, and hands back the single row it produced. */
    private static String fit(String text) {
        return HudText.keepStart(text, InspectionPanelLayout.ROW_WIDTH);
    }
}

package com.rustorio.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * {@link UiSettings} — the quick bar's own little file.
 *
 * <p>Every test here is about surviving a bad file rather than about writing a good one, and that
 * is the point: this holds a cosmetic preference, so nothing it can contain may keep the game from
 * starting. The save format is allowed to refuse a file it does not understand; this is not.
 */
class UiSettingsTest {

    @TempDir
    Path tempDir;

    private static final ContentId BELT = ContentId.of("rustorio:belt");
    private static final ContentId MINER = ContentId.of("rustorio:miner");

    @Test
    void aSavedBarComesBackInTheSameOrder() {
        UiSettings settings = new UiSettings(tempDir.resolve("ui.json"));

        assertTrue(settings.saveQuickBar(List.of(BELT, MINER)));

        assertEquals(List.of(BELT, MINER), new UiSettings(tempDir.resolve("ui.json")).quickBar(),
                "order is the player's arrangement, not a set");
    }

    @Test
    void aMissingFileIsAnEmptyBarRatherThanAFailure() {
        assertEquals(List.of(), new UiSettings(tempDir.resolve("never-written.json")).quickBar(),
                "a first-ever launch has no file, and that is the ordinary case");
    }

    @Test
    void anUnreadableFileIsAnEmptyBarRatherThanACrash() throws IOException {
        Path path = tempDir.resolve("broken.json");
        Files.writeString(path, "{ this is not json at all");

        assertEquals(List.of(), new UiSettings(path).quickBar(),
                "a corrupted preferences file must cost the player their bar, not their game");
    }

    /** A hand-edited file is player input: entries that are not content ids are dropped, and the rest still load. */
    @Test
    void entriesThatAreNotContentIdsAreDroppedAndTheRestSurvive() throws IOException {
        Path path = tempDir.resolve("mixed.json");
        Files.writeString(path, "{\"quickBar\":[\"rustorio:belt\",\"nonsense\",\"\",\"rustorio:miner\"]}");

        assertEquals(List.of(BELT, MINER), new UiSettings(path).quickBar());
    }

    @Test
    void savingAnEmptyBarIsLegalAndReadsBackEmpty() {
        UiSettings settings = new UiSettings(tempDir.resolve("empty.json"));

        assertTrue(settings.saveQuickBar(List.of()), "clearing the bar is a normal thing to do");
        assertEquals(List.of(), settings.quickBar());
    }
}

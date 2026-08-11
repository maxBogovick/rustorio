package com.rustorio.persistence;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.AuthoredMap;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.RandomOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.World;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closes the finding that motivated {@link SaveSlots}: before it existed, a menu had no way to
 * list, tell apart, or manage more than {@link JsonSaveRepository}'s one hardcoded save file.
 * These tests fail against a bare {@code Files.list}-based sketch that doesn't sort deterministically
 * or peek {@code oreLayout} without a full {@code ItemType}-aware mapper.
 */
class SaveSlotsTest {

    @Test
    void listingAMissingDirectoryIsEmptyNotAnError(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir.resolve("does-not-exist"));

        assertEquals(List.of(), slots.list());
    }

    @Test
    void savedSlotsAreListedNewestFirst(@TempDir Path dir) throws IOException {
        SaveSlots slots = new SaveSlots(dir);
        writeMinimalSave(slots.prepareForSave("older"), Instant.parse("2026-01-01T00:00:00Z"));
        writeMinimalSave(slots.prepareForSave("newer"), Instant.parse("2026-06-01T00:00:00Z"));

        List<SaveSlotInfo> listed = slots.list();

        assertEquals(List.of("newer", "older"), listed.stream().map(SaveSlotInfo::name).toList());
    }

    @Test
    void equalTimestampsBreakTiesByNameForADeterministicOrder(@TempDir Path dir) throws IOException {
        SaveSlots slots = new SaveSlots(dir);
        Instant sameInstant = Instant.parse("2026-01-01T00:00:00Z");
        writeMinimalSave(slots.prepareForSave("zebra"), sameInstant);
        writeMinimalSave(slots.prepareForSave("apple"), sameInstant);

        List<SaveSlotInfo> listed = slots.list();

        assertEquals(List.of("apple", "zebra"), listed.stream().map(SaveSlotInfo::name).toList());
    }

    @Test
    void peekingAPatchMapSaveReportsVanilla(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);
        World world = new World(4, 4); // BuildingFactory.standard() -> PatchOreLayout
        new JsonSaveRepository(slots.prepareForSave("vanilla-run")).save(world);

        SaveSlotInfo info = onlySlot(slots);
        assertEquals("vanilla", info.mapLabel());
    }

    @Test
    void peekingARandomMapSaveReportsItsSeed(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);
        BuildingFactory factory = new BuildingFactory(new RandomOreLayout(42L, 4, 4), RecipeBook.standard());
        World world = new World(4, 4, factory);
        new JsonSaveRepository(slots.prepareForSave("seeded-run")).save(world);

        SaveSlotInfo info = onlySlot(slots);
        assertEquals("random (seed 42)", info.mapLabel());
    }

    @Test
    void peekingAnAuthoredMapSaveReportsTheMapsContentId(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);
        AuthoredMap map = new AuthoredMap(ContentId.of("rustorio:test_map"), List.of(), List.of());
        BuildingFactory factory = new BuildingFactory(AuthoredOreLayout.from(map), RecipeBook.standard());
        World world = new World(4, 4, factory);
        new JsonSaveRepository(slots.prepareForSave("authored-run")).save(world);

        SaveSlotInfo info = onlySlot(slots);
        assertEquals("rustorio:test_map", info.mapLabel());
    }

    @Test
    void peekingAFileThatIsNotAValidSaveReportsAnUnknownMapInsteadOfThrowing(@TempDir Path dir) throws IOException {
        SaveSlots slots = new SaveSlots(dir);
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("not-a-save.json"), "not even json");

        SaveSlotInfo info = onlySlot(slots);
        assertEquals("unknown map", info.mapLabel());
    }

    @Test
    void rejectingNamesWithPathSeparatorsClosesOffTraversal(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);

        assertThrows(IllegalArgumentException.class, () -> slots.pathFor("../outside"));
        assertThrows(IllegalArgumentException.class, () -> slots.pathFor("nested/name"));
    }

    @Test
    void deletingARemovesItsFileAndReportsWhetherItExisted(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);
        World world = new World(4, 4);
        new JsonSaveRepository(slots.prepareForSave("throwaway")).save(world);

        assertTrue(slots.delete("throwaway"), "the slot existed, so deleting it must report true");
        assertFalse(slots.exists("throwaway"));
        assertFalse(slots.delete("throwaway"), "deleting an already-gone slot is not an error");
    }

    @Test
    void renamingMovesTheSaveUnderTheNewNameOnly(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);
        World world = new World(4, 4);
        new JsonSaveRepository(slots.prepareForSave("draft")).save(world);

        slots.rename("draft", "final");

        assertFalse(slots.exists("draft"));
        assertTrue(slots.exists("final"));
    }

    @Test
    void renamingOntoAnExistingSlotRefusesToSilentlyOverwriteIt(@TempDir Path dir) {
        SaveSlots slots = new SaveSlots(dir);
        new JsonSaveRepository(slots.prepareForSave("a")).save(new World(4, 4));
        new JsonSaveRepository(slots.prepareForSave("b")).save(new World(4, 4));

        assertThrows(java.io.UncheckedIOException.class, () -> slots.rename("a", "b"));
        assertTrue(slots.exists("b"), "the refused rename must leave the existing slot untouched");
    }

    private static SaveSlotInfo onlySlot(SaveSlots slots) {
        List<SaveSlotInfo> listed = slots.list();
        assertEquals(1, listed.size(), "expected exactly one save slot");
        return listed.get(0);
    }

    private static void writeMinimalSave(Path path, Instant savedAt) throws IOException {
        new JsonSaveRepository(path).save(new World(4, 4));
        Files.setLastModifiedTime(path, FileTime.from(savedAt));
    }
}

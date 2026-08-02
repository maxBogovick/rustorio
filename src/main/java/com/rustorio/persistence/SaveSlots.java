package com.rustorio.persistence;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustorio.domain.OreLayoutId;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Turns "the saves folder" into named save slots a menu can list, delete and rename — the
 * multi-slot counterpart to {@link JsonSaveRepository}'s single hardcoded {@link
 * JsonSaveRepository#DEFAULT_PATH}. Deliberately NOT a {@link SaveRepository} itself: this class's
 * contract is about slot NAMES (list/delete/rename), not about reading/writing a {@code World} —
 * that's still {@link JsonSaveRepository}'s job, constructed against whatever {@link Path} {@link
 * #pathFor} resolves to. A missing saves directory isn't an error (same reasoning as {@code
 * com.rustorio.mod.ModDirectories#discover}): it means "no saves yet", not "broken install".
 */
public final class SaveSlots {

    /** Default location — a sibling of {@link JsonSaveRepository#DEFAULT_PATH}, one file per slot. */
    public static final Path DEFAULT_DIR = Path.of("saves");

    /**
     * Filesystem-safe and simple enough for a menu text field: letters, digits, spaces, {@code
     * _}/{@code -}. No {@code .} or {@code /} at all (not even one to allow later) — closes off
     * path traversal by construction instead of by blacklisting {@code ".."}.
     */
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9 _-]{1,64}");

    /**
     * Reads only {@link WorldSnapshot#version}/{@link WorldSnapshot#oreLayout} out of a save file —
     * unlike {@link JsonSaveRepository}'s mapper, needs no {@code ItemType} (de)serializer module,
     * since neither field ever mentions one. {@code FAIL_ON_UNKNOWN_PROPERTIES} disabled so the
     * other fields (buildings, inventory, ...) are simply ignored rather than rejected.
     */
    private static final ObjectMapper HEADER_MAPPER = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private final Path dir;

    public SaveSlots() {
        this(DEFAULT_DIR);
    }

    public SaveSlots(Path dir) {
        this.dir = dir;
    }

    /**
     * Every save slot in this directory, newest {@link SaveSlotInfo#savedAt} first — ties (same
     * filesystem timestamp) broken by name so the order never depends on {@link Files#list}'s
     * unspecified iteration order (determinism, see {@code AGENTS.md}).
     */
    public List<SaveSlotInfo> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> entries = Files.list(dir)) {
            List<SaveSlotInfo> slots = new ArrayList<>();
            for (Path path : entries.filter(SaveSlots::isSaveFile).toList()) {
                slots.add(infoFor(path));
            }
            slots.sort(Comparator.comparing(SaveSlotInfo::savedAt).reversed()
                    .thenComparing(SaveSlotInfo::name));
            return List.copyOf(slots);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to list save slots under " + dir, e);
        }
    }

    private static boolean isSaveFile(Path path) {
        return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json");
    }

    private static SaveSlotInfo infoFor(Path path) {
        String fileName = path.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - ".json".length());
        FileTime modified;
        try {
            modified = Files.getLastModifiedTime(path);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to read save slot timestamp: " + path, e);
        }
        return new SaveSlotInfo(name, modified.toInstant(), peekMapLayout(path));
    }

    /** {@code null} on anything that stops the header from being read — corrupt/foreign JSON is the menu's problem to report, not this method's to throw on. */
    private static @Nullable OreLayoutId peekMapLayout(Path path) {
        try {
            return HEADER_MAPPER.readValue(path.toFile(), Header.class).oreLayout();
        } catch (IOException e) {
            return null;
        }
    }

    private record Header(int version, @Nullable OreLayoutId oreLayout) {
    }

    /** {@code true} if a slot named {@code name} already has a save file. */
    public boolean exists(String name) {
        return Files.exists(pathFor(name));
    }

    /**
     * Where slot {@code name}'s save file lives — for reading (an existing slot) or deleting.
     * Throws {@link IllegalArgumentException} if {@code name} doesn't match {@link #VALID_NAME}
     * (the same "reject up front with a precise message" style as {@link
     * com.rustorio.api.content.ContentId}), whether or not that slot currently exists.
     */
    public Path pathFor(String name) {
        requireValidName(name);
        return dir.resolve(name + ".json");
    }

    /**
     * Same as {@link #pathFor}, but also creates {@link #dir} if it doesn't exist yet — the one
     * call a caller about to WRITE a brand new slot needs: {@link JsonSaveRepository#save} creates
     * its temp file next to the target and fails outright if that directory is missing.
     */
    public Path prepareForSave(String name) {
        Path target = pathFor(name);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to create saves directory " + dir, e);
        }
        return target;
    }

    /** Deletes slot {@code name}'s save file. Returns {@code false} (not an error) if it didn't exist. */
    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(pathFor(name));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to delete save slot: " + name, e);
        }
    }

    /**
     * Renames slot {@code from} to {@code to}. Refuses to overwrite an existing {@code to} (throws
     * {@link FileAlreadyExistsException}, wrapped) rather than silently replacing it — a menu that
     * wants "overwrite" prompts the player and calls {@link #delete} first, the same as {@link
     * JsonSaveRepository#save} already overwrites its own single slot deliberately, on purpose,
     * every time.
     */
    public void rename(String from, String to) {
        Path source = pathFor(from);
        Path target = pathFor(to);
        try {
            Files.move(source, target);
        } catch (IOException e) {
            throw new UncheckedIOException("failed to rename save slot \"" + from + "\" to \"" + to + "\"", e);
        }
    }

    private static void requireValidName(String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "save slot name must match " + VALID_NAME.pattern() + ": \"" + name + "\"");
        }
    }
}

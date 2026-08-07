package com.rustorio.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rustorio.api.content.ContentId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The player's interface preferences — today only which buildings they pinned to the quick bar.
 *
 * <p>A file of its OWN, beside the save rather than inside it, and the owner chose that
 * deliberately: a pinned bar is a fact about the PLAYER, not about a world. Putting it in the save
 * would mean loading a friend's factory silently replaced your own layout, and that every world you
 * ever start begins with an empty bar again.
 *
 * <p>Stored as plain content ids, in order. Nothing here validates them — a mod may have been
 * uninstalled since, and {@code com.graphics.input.QuickBar} is where "an id nobody registered" is
 * dealt with. This class's whole job is the file.
 *
 * <p>Failure is never fatal on either side. A settings file that is missing, unreadable or
 * hand-edited into nonsense yields an empty layout, because the alternative — refusing to start the
 * game over a cosmetic preference — is absurd. A write that fails is reported and dropped for the
 * same reason.
 */
public final class UiSettings {

    /** Default location — a sibling of {@link JsonSaveRepository#DEFAULT_PATH}, same working directory. */
    public static final Path DEFAULT_PATH = Path.of("rustorio-ui.json");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path path;

    public UiSettings() {
        this(DEFAULT_PATH);
    }

    public UiSettings(Path path) {
        this.path = path;
    }

    /** The stored quick-bar ids in order, or an empty list when there is nothing readable to restore. */
    public List<ContentId> quickBar() {
        if (!Files.isRegularFile(path)) {
            return List.of();
        }
        try {
            Stored stored = MAPPER.readValue(Files.readString(path), new TypeReference<Stored>() { });
            List<String> raw = stored.quickBar();
            if (raw == null) {
                return List.of();
            }
            List<ContentId> ids = new ArrayList<>(raw.size());
            for (String id : raw) {
                if (id != null && id.indexOf(':') > 0) {
                    ids.add(ContentId.of(id));
                }
            }
            return List.copyOf(ids);
        } catch (IOException | RuntimeException e) {
            // Unreadable, wrong shape, or an id that isn't one — see the class javadoc: a cosmetic
            // preference must never be the reason a game refuses to open.
            return List.of();
        }
    }

    /** Writes {@code quickBar} out, replacing whatever was there. Returns whether it landed — the caller decides whether anyone should be told. */
    public boolean saveQuickBar(List<ContentId> quickBar) {
        List<String> ids = quickBar.stream().map(ContentId::toString).toList();
        try {
            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(new Stored(ids)) + System.lineSeparator());
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /**
     * The file's own shape. A record with one field rather than a bare list, so the next preference
     * is a new key in a file that already exists instead of a second file — the reason this is
     * named for the whole UI and not for the quick bar alone.
     */
    private record Stored(List<String> quickBar) {
    }
}

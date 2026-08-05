package com.rustorio.mod;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * The human-facing half of a {@code mod.json}: what to call this mod in a list, who wrote it, and
 * where to go with a question. None of it affects loading — it exists because "which of these
 * thirty folders is the one misbehaving" is a question an id alone answers badly, and because a
 * modder has nowhere else to put a homepage.
 *
 * <p>Every field is optional. {@link #unnamed} stands in when a manifest says nothing, using the
 * mod's own id as its name — the same thing a reader would have had to do anyway.
 */
public record ModMetadata(String name, @Nullable String description, List<String> authors,
        @Nullable String homepage, @Nullable String license) {

    public ModMetadata {
        authors = List.copyOf(authors);
    }

    /** Metadata for a manifest that declares none — the name falls back to the id. */
    public static ModMetadata unnamed(ModId id) {
        return new ModMetadata(id.value(), null, List.of(), null, null);
    }
}

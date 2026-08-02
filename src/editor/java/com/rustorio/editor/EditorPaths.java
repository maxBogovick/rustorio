package com.rustorio.editor;

import java.nio.file.Path;

/**
 * Where everything lives, relative to the repo root the {@code editor} Gradle task's {@code
 * workingDir} sets (same convention {@code run}/{@code game}/{@code benchmark} already use).
 *
 * <p>V2: every content directory is resolved PER REQUEST from a {@code modId} (see {@link
 * EditorHttp#modId}), not baked in as a fixed path — {@code resources/mods} now has a real second
 * mod ({@code sandbox}) content should actually be able to land in, and a single-mod editor was
 * exactly what made a stray test item end up inside {@code rustorio} instead (see the git history
 * around the map editor's first version). {@link #DEFAULT_MOD_ID} keeps every caller that doesn't
 * care which mod (an old bookmark with no {@code ?mod=}, a script) pointed at {@code rustorio}
 * exactly like the single-mod version always was.
 */
final class EditorPaths {

    static final Path REPO_ROOT = Path.of(".");
    static final Path MODS_ROOT = Path.of("resources", "mods");
    static final Path FRONTEND_ROOT = Path.of("resources", "editor");
    static final String DEFAULT_MOD_ID = "rustorio";

    private EditorPaths() {
    }

    /** {@code resources/mods/<modId>/content/<kind>} — {@code kind} is one of {@code items/buildings/kinds/maps/recipes}. */
    static Path contentDir(String modId, String kind) {
        return MODS_ROOT.resolve(modId).resolve("content").resolve(kind);
    }

    /** {@code resources/mods/<modId>/textures}. */
    static Path texturesDir(String modId) {
        return MODS_ROOT.resolve(modId).resolve("textures");
    }
}

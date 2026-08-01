package com.rustorio.editor;

import java.nio.file.Path;

/**
 * Where everything lives, relative to the repo root the {@code editor} Gradle task's {@code
 * workingDir} sets (same convention {@code run}/{@code game}/{@code benchmark} already use). V1
 * targets the single {@code rustorio} mod that ships in this repo — {@code resources/mods} has
 * exactly one directory today; a generic "pick which mod" UI is speculative until a second one
 * exists.
 */
final class EditorPaths {

    static final Path REPO_ROOT = Path.of(".");
    static final Path MODS_ROOT = Path.of("resources", "mods");
    static final Path MOD_DIR = MODS_ROOT.resolve("rustorio");
    static final Path ITEMS_DIR = MOD_DIR.resolve("content").resolve("items");
    static final Path RECIPES_DIR = MOD_DIR.resolve("content").resolve("recipes");
    static final Path BUILDINGS_DIR = MOD_DIR.resolve("content").resolve("buildings");
    static final Path KINDS_DIR = MOD_DIR.resolve("content").resolve("kinds");
    static final Path TEXTURES_DIR = MOD_DIR.resolve("textures");
    static final Path FRONTEND_ROOT = Path.of("resources", "editor");

    private EditorPaths() {
    }
}

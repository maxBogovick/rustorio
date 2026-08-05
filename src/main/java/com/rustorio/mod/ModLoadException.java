package com.rustorio.mod;

import org.jspecify.annotations.Nullable;

/**
 * Thrown for anything that stops mod loading — a malformed {@code mod.json}, an unresolved or
 * cyclic dependency, a jar with no (or more than one) {@code RustorioMod} implementation, invalid
 * content JSON, a dangling content reference. The message always names which mod/file/field is at
 * fault — a modder reading a stack trace should never have to guess which of several installed
 * mods caused it.
 *
 * <p>{@link #culprit()} is the machine-readable half of that: the single mod whose removal would
 * make this particular failure go away, or {@code null} when no single mod is to blame. {@link
 * ModLoader} skips a culprit and retries; a {@code null} culprit stops loading outright. The
 * distinction is deliberately conservative — "two mods both claim this rename" and "these mods form
 * a dependency cycle" name several mods and have no innocent choice among them, so they stay fatal
 * rather than letting the loader silently pick a loser.
 */
public final class ModLoadException extends RuntimeException {

    private final @Nullable ModId culprit;

    public ModLoadException(String message) {
        this(message, (ModId) null);
    }

    public ModLoadException(String message, @Nullable ModId culprit) {
        super(message);
        this.culprit = culprit;
    }

    public ModLoadException(String message, Throwable cause) {
        this(message, null, cause);
    }

    public ModLoadException(String message, @Nullable ModId culprit, Throwable cause) {
        super(message, cause);
        this.culprit = culprit;
    }

    /** The one mod whose removal would resolve this failure, or {@code null} if no single mod is to blame — see the class javadoc. */
    public @Nullable ModId culprit() {
        return culprit;
    }
}

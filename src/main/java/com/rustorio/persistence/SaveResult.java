package com.rustorio.persistence;

import org.jspecify.annotations.Nullable;

/**
 * Outcome of a {@link SaveRepository#save}/{@link SaveRepository#load} call — replaces a bare
 * {@code boolean} that could only ever say "it failed," never why. The failure path always
 * carries {@link Failure#reason()} straight from the underlying {@link java.io.IOException},
 * so the caller decides how (or whether) to surface it instead of the repository deciding for
 * everyone by writing to {@code System.err} itself.
 */
public sealed interface SaveResult {

    record Success() implements SaveResult {
    }

    record Failure(@Nullable String reason) implements SaveResult {
    }

    default boolean succeeded() {
        return this instanceof Success;
    }
}

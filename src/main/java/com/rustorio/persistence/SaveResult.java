package com.rustorio.persistence;

import com.rustorio.api.content.ContentId;
import java.util.List;
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

    /**
     * A save loaded, but named at least one {@link ContentId} no longer registered (its mod was
     * removed) — every OTHER building loaded normally; each cell that would have held a missing
     * one is simply empty. Deliberately not {@link Failure}: rejecting the whole save over content
     * that's merely absent (as opposed to unreadable) would throw away everything else it still
     * remembers. {@link #succeeded()} is {@code true} here — the caller (a test today, a future
     * UI) decides what to do with the loss report, this type only guarantees the loss is never
     * silent.
     */
    record PartialSuccess(List<ContentId> missingPrototypeIds, int buildingsSkipped) implements SaveResult {
    }

    record Failure(@Nullable String reason) implements SaveResult {
    }

    default boolean succeeded() {
        return !(this instanceof Failure);
    }
}

package com.rustorio.mod;

/**
 * A mod that was present on disk but is not in the running game, and why — what {@link
 * LoadedGame#skippedMods()} carries so a player sees "this one didn't load" instead of a game that
 * refuses to start, and a modder sees the reason without reading a stack trace.
 *
 * <p>{@code reason} is the full message of the {@link ModLoadException} that caused the skip,
 * which by this package's own convention already names the file and field at fault.
 */
public record SkippedMod(ModId id, String reason) {
}

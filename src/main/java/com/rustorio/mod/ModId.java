package com.rustorio.mod;

import java.util.regex.Pattern;

/**
 * A mod's own identifier — the namespace half of every {@link com.rustorio.api.content.ContentId}
 * its content registers under. Same format as {@link com.rustorio.api.content.ContentId}'s own
 * namespace segment ({@code [a-z0-9_]+}), duplicated rather than shared: {@code ContentId}'s
 * pattern is a private implementation detail of that record, and one shared regex constant isn't
 * worth a new cross-package utility class for.
 *
 * <p>{@link Comparable} by plain string order, same reasoning as {@code ContentId}'s own — {@link
 * DependencyResolver} breaks ties between mods with no relative ordering requirement by this order,
 * not by their position in whatever list the loader happened to hand it (see {@code
 * DependencyResolverTest}'s determinism test).
 */
public record ModId(String value) implements Comparable<ModId> {

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_]+");

    public ModId {
        if (!SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "mod id must match [a-z0-9_]+ (lowercase ASCII, non-empty): \"" + value + "\"");
        }
    }

    @Override
    public int compareTo(ModId other) {
        return value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}

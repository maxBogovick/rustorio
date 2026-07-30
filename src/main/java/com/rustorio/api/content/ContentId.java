package com.rustorio.api.content;

import java.util.regex.Pattern;

/**
 * A namespaced content identifier, {@code namespace:path} (e.g. {@code "rustorio:iron_ore"}) —
 * lets two independent mod authors name their own content without colliding, and doubles as the
 * path to that content's assets.
 *
 * <p>Lowercase ASCII only ({@code [a-z0-9_]+}): this name also has to work as a filename and a
 * save-file key, and case-only differences break across filesystems.
 *
 * <p>{@link #compareTo} is plain {@code String.compareTo} on {@link #toString()}, not a
 * locale-aware collator — {@code Registry.freeze()} assigns {@code rawId} in this order, so it
 * can't depend on the JVM's default locale or on mod load order.
 */
public record ContentId(String namespace, String path) implements Comparable<ContentId> {

    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_]+");

    public ContentId {
        requireValidSegment("namespace", namespace);
        requireValidSegment("path", path);
    }

    private static void requireValidSegment(String label, String value) {
        if (!SEGMENT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "ContentId " + label + " must match [a-z0-9_]+ (lowercase ASCII, non-empty): \"" + value + "\"");
        }
    }

    /** Parses {@code "namespace:path"} — the inverse of {@link #toString()}. */
    public static ContentId of(String id) {
        String[] parts = id.split(":", -1);
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    "ContentId must be exactly one \"namespace:path\" (found " + (parts.length - 1)
                            + " ':' instead of 1): \"" + id + "\"");
        }
        return new ContentId(parts[0], parts[1]);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }

    @Override
    public int compareTo(ContentId other) {
        return toString().compareTo(other.toString());
    }
}

package com.rustorio.mod;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A {@code MAJOR.MINOR.PATCH} version, the only format {@code mod.json}'s {@code version}/dependency ranges accept. */
public record SemVer(int major, int minor, int patch) implements Comparable<SemVer> {

    private static final Pattern FORMAT = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)");

    public SemVer {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("version components must be non-negative: " + major + "." + minor + "." + patch);
        }
    }

    /** Parses {@code "MAJOR.MINOR.PATCH"} (e.g. {@code "1.2.0"}) — the inverse of {@link #toString()}. */
    public static SemVer parse(String text) {
        Matcher matcher = FORMAT.matcher(text.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException("version must be MAJOR.MINOR.PATCH (e.g. \"1.2.0\"): \"" + text + "\"");
        }
        return new SemVer(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3)));
    }

    @Override
    public int compareTo(SemVer other) {
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}

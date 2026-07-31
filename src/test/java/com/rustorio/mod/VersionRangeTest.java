package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class VersionRangeTest {

    @Test
    void starMatchesAnyVersion() {
        VersionRange range = VersionRange.parse("*");
        assertTrue(range.matches(SemVer.parse("0.0.1")));
        assertTrue(range.matches(SemVer.parse("99.9.9")));
    }

    @Test
    void bareVersionMeansExactMatch() {
        VersionRange range = VersionRange.parse("1.2.3");
        assertTrue(range.matches(SemVer.parse("1.2.3")));
        assertFalse(range.matches(SemVer.parse("1.2.4")));
    }

    @Test
    void gteOperatorAcceptsEqualOrHigher() {
        VersionRange range = VersionRange.parse(">=1.0.0");
        assertTrue(range.matches(SemVer.parse("1.0.0")));
        assertTrue(range.matches(SemVer.parse("2.0.0")));
        assertFalse(range.matches(SemVer.parse("0.9.9")));
    }

    @Test
    void gteIsNotMisreadAsGtFollowedByAStrayEquals() {
        // The bug this test guards against: checking ">" before ">=" would parse ">=1.0.0" as
        // operator ">" over the version text "=1.0.0", which SemVer.parse would then reject.
        VersionRange range = VersionRange.parse(">=1.0.0");
        assertTrue(range.matches(SemVer.parse("1.0.0")), "\">=\" must accept the boundary version itself");
    }

    @Test
    void commaJoinedConstraintsAreCombinedWithAnd() {
        VersionRange range = VersionRange.parse(">=1.0.0,<2.0.0");
        assertTrue(range.matches(SemVer.parse("1.5.0")));
        assertFalse(range.matches(SemVer.parse("2.0.0")), "upper bound is exclusive");
        assertFalse(range.matches(SemVer.parse("0.9.0")), "lower bound must also hold");
    }

    @Test
    void neqExcludesExactlyOneVersion() {
        VersionRange range = VersionRange.parse("!=1.5.0");
        assertTrue(range.matches(SemVer.parse("1.4.0")));
        assertFalse(range.matches(SemVer.parse("1.5.0")));
    }

    @Test
    void rejectsAnUnparsableConstraint() {
        assertThrows(IllegalArgumentException.class, () -> VersionRange.parse(">=abc"));
    }
}

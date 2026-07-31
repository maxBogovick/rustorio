package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SemVerTest {

    @Test
    void parsesMajorMinorPatch() {
        assertEquals(new SemVer(1, 2, 3), SemVer.parse("1.2.3"));
    }

    @Test
    void toStringIsTheInverseOfParse() {
        assertEquals("1.2.3", SemVer.parse("1.2.3").toString());
    }

    @Test
    void rejectsAMalformedVersionString() {
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.2"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("v1.2.3"));
        assertThrows(IllegalArgumentException.class, () -> SemVer.parse("1.2.3-beta"));
    }

    @Test
    void ordersByMajorThenMinorThenPatch() {
        assertTrue(SemVer.parse("2.0.0").compareTo(SemVer.parse("1.9.9")) > 0, "major wins over minor/patch");
        assertTrue(SemVer.parse("1.2.0").compareTo(SemVer.parse("1.1.9")) > 0, "minor wins over patch");
        assertTrue(SemVer.parse("1.1.2").compareTo(SemVer.parse("1.1.1")) > 0, "patch breaks the remaining tie");
        assertEquals(0, SemVer.parse("1.2.3").compareTo(SemVer.parse("1.2.3")));
    }
}

package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * {@link ContentLocale#current()} defaults to {@code "en"} when {@code rustorio.locale} isn't set
 * — the normal case for this test suite (nothing in the Gradle test task sets it). Testing an
 * OVERRIDDEN locale would need a separate JVM (the property is read once at class-load time, see
 * the class's own javadoc for why) — {@code JsonNodesTest} exercises the actual resolution logic
 * at any locale instead, without depending on this process-wide value at all.
 */
class ContentLocaleTest {

    @Test
    void defaultsToEnglishWhenTheSystemPropertyIsNotSet() {
        assertEquals("en", ContentLocale.current());
    }
}

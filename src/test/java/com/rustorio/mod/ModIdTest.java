package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ModIdTest {

    @Test
    void acceptsLowercaseAsciiDigitsAndUnderscore() {
        assertEquals("example_mod_2", new ModId("example_mod_2").value());
    }

    @Test
    void rejectsUppercase() {
        assertThrows(IllegalArgumentException.class, () -> new ModId("ExampleMod"));
    }

    @Test
    void rejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> new ModId(""));
    }

    @Test
    void rejectsAColon() {
        assertThrows(IllegalArgumentException.class, () -> new ModId("example:mod"));
    }
}

package com.rustorio.mod;

/**
 * Thrown for anything that stops mod loading — a malformed {@code mod.json}, an unresolved or
 * cyclic dependency, a jar with no (or more than one) {@code RustorioMod} implementation, invalid
 * content JSON, a dangling content reference. The message always names which mod/file/field is at
 * fault — a modder reading a stack trace should never have to guess which of several installed
 * mods caused it.
 */
public final class ModLoadException extends RuntimeException {

    public ModLoadException(String message) {
        super(message);
    }

    public ModLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}

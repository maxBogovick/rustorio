package com.rustorio.mod;

/**
 * The locale content labels resolve against — read ONCE, from the {@code rustorio.locale} system
 * property (default {@code "en"}), at class-load time. Not a live, in-game switchable setting: no
 * screen in this project offers one, and building one is a separate, bigger UI feature than
 * resolving a label at load time.
 */
public final class ContentLocale {

    private static final String CURRENT = System.getProperty("rustorio.locale", "en");

    private ContentLocale() {
    }

    public static String current() {
        return CURRENT;
    }
}

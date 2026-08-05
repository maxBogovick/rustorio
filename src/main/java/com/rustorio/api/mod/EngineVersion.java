package com.rustorio.api.mod;

/**
 * The engine's own version, as a mod sees it.
 *
 * <p>Exists so a mod can say which engine it was written against ({@code minEngineVersion} in
 * {@code mod.json}) and be refused with a sentence a player can act on, instead of loading against
 * an engine whose API has moved and failing later with a {@code NoSuchMethodError} that names a
 * method nobody recognises.
 *
 * <p>The literal is duplicated from {@code build.gradle}'s {@code version} rather than read from a
 * jar manifest: the game is routinely run straight from a Gradle source set with no jar and no
 * manifest at all, and a version that is only correct in a packaged build is worse than none.
 * {@code EngineVersionTest} reads {@code build.gradle} and fails if the two ever disagree, so the
 * duplication cannot drift silently.
 */
public final class EngineVersion {

    /** This engine's version, {@code major.minor.patch}. */
    public static final String CURRENT = "0.1.0";

    private EngineVersion() {
    }
}

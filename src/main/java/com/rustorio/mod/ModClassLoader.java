package com.rustorio.mod;

import java.net.URL;
import java.net.URLClassLoader;
import java.util.List;

/**
 * One classloader per mod jar — two mods never share an instance, so one mod's classes are never
 * visible to another's even though both ultimately share the same JVM (see {@code
 * ModClassLoaderTest.twoModsWithAClassOfTheSameNameLoadTwoGenuinelyDifferentClasses}).
 *
 * <p>Overrides the default parent-first delegation model, which would otherwise defeat isolation
 * entirely: this project builds as a single Gradle module (no standalone API artifact yet), so the
 * PARENT classloader already has {@code com.rustorio.persistence}, {@code com.graphics}, and every
 * other mod's classes on its own classpath. Falling back to the parent for a class this loader
 * doesn't recognize would let a mod reach any of those simply by naming them — so a name outside
 * {@link #PARENT_DELEGATED_PREFIXES} is looked up ONLY in this mod's own jar ({@link #findClass}),
 * never in the parent, and fails with {@link ClassNotFoundException} if the jar doesn't have it.
 *
 * <p>{@link #PARENT_DELEGATED_PREFIXES} is wider than just {@code com.rustorio.api}: catalog
 * models and vanilla helpers already live under {@code com.rustorio.api.content.model} /
 * {@code api.content.vanilla}, but mods still name remaining simulation types that stay under
 * {@code com.rustorio.domain} / {@code domain.building} until further narrowing ({@code Direction},
 * {@code Cell}, {@code BuildingStatus}, {@code Appearance}, {@code Research}/{@code ResearchView},
 * {@code BuildingType}, plus allowlisted building contracts). No standalone, implementation-free
 * API artifact exists yet, so those packages stay delegated. Narrowing further toward strictly
 * {@code com.rustorio.api} is still ahead; {@code domain.world}/{@code domain.action} are denied
 * entirely, and {@code domain.building} is reduced to {@link ModBuildingApiAllowlist} (contracts +
 * SimpleCrafter + BeltSegment — not networks or vanilla concretes).
 *
 * <p>{@link #isParentDelegated} is the SINGLE authority on where the engine ends and a mod
 * begins, and {@code ModApiSurfaceTest} holds the published artifact to it: it opens the {@code
 * rustorio-api} jar and fails if that jar ships a package this list would refuse to load. The two
 * had already drifted once — {@code include 'com/rustorio/**'} shipped {@code persistence}, {@code
 * mod} and {@code game} in full, three packages a mod could compile against and never load, turning
 * a build error into a {@link ClassNotFoundException} in a player's game.
 *
 * <p>What a MOD may name is held to the same line twice over. The compiler gets there first: an
 * in-repo mod's compile classpath is the {@code apiJar} output rather than the engine's own (see the
 * source-set block in {@code build.gradle}), so a mod naming {@code ModLoader} does not compile.
 * {@code ModBoundaryRulesTest} then checks the mods' bytecode anyway — the compiler's guarantee is
 * only as durable as one line of a build script, and no test reads build scripts. Its own javadoc
 * has the failure that made keeping both worthwhile.
 *
 * <p>Deliberately a hard-coded literal rather than a resource file the build and this loader both
 * read. A shared file would make "one source" literal, but it would also make an isolation
 * allowlist something that can go missing — and a missing file reads as an EMPTY or absent list,
 * which fails open. A literal cannot be lost.
 */
final class ModClassLoader extends URLClassLoader {

    private static final List<String> PARENT_DELEGATED_PREFIXES = List.of(
            "com.rustorio.api.", "com.rustorio.domain.", "java.", "javax.", "jdk.", "sun.");

    /** Denied even though they sit under a delegated prefix — simulation kitchen, not mod API. */
    private static final List<String> PARENT_DENIED_PREFIXES = List.of(
            "com.rustorio.domain.world.", "com.rustorio.domain.action.");

    private static final String BUILDING_PKG = "com.rustorio.domain.building.";

    ModClassLoader(URL jarUrl, ClassLoader parent) {
        super(new URL[] {jarUrl}, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> alreadyLoaded = findLoadedClass(name);
            if (alreadyLoaded != null) {
                return finishLoading(alreadyLoaded, resolve);
            }
            if (isParentDelegated(name)) {
                return finishLoading(super.loadClass(name, false), resolve);
            }
            // Not allowlisted: this mod's own jar is the ONLY place this name may come from — no
            // fallback to the parent (see the class javadoc for why that would defeat isolation).
            return finishLoading(findClass(name), resolve);
        }
    }

    /**
     * Package-private so {@code ModApiSurfaceTest} can ask the loader itself whether a class in the
     * published jar would be delegated, instead of restating the prefixes. Restating them is what
     * this predicate exists to prevent: the prefixes carry a trailing dot on purpose ({@code
     * "com.rustorio.api."}), so {@code com.rustorio.apiary.Foo} is NOT delegated — a test that
     * matched on {@code "com.rustorio.api"} without it would call that class part of the API surface
     * while the running game refused to load it, which is the exact drift being guarded against.
     */
    static boolean isParentDelegated(String name) {
        for (String denied : PARENT_DENIED_PREFIXES) {
            if (name.startsWith(denied)) {
                return false;
            }
        }
        if (name.startsWith(BUILDING_PKG)) {
            return ModBuildingApiAllowlist.isAllowed(name);
        }
        for (String prefix : PARENT_DELEGATED_PREFIXES) {
            if (name.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private Class<?> finishLoading(Class<?> clazz, boolean resolve) {
        if (resolve) {
            resolveClass(clazz);
        }
        return clazz;
    }
}

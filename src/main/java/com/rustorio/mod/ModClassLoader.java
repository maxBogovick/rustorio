package com.rustorio.mod;

import java.net.URL;
import java.net.URLClassLoader;

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
 * <p>{@link #PARENT_DELEGATED_PREFIXES} is wider than just {@code com.rustorio.api}: {@code
 * ItemType}/{@code BuildingPrototype}/{@code Recipe} etc. physically live under {@code
 * com.rustorio.domain}/{@code domain.building} today, not under {@code com.rustorio.api} — no
 * standalone, implementation-free API artifact exists yet — so a mod that constructs either (which
 * every mod registering an item or a building prototype must do) needs those packages delegated
 * too. Narrowing this allowlist down to strictly {@code com.rustorio.api} is a follow-up for
 * whenever that split actually happens, not a silent gap here.
 */
final class ModClassLoader extends URLClassLoader {

    private static final String[] PARENT_DELEGATED_PREFIXES = {
            "com.rustorio.api.", "com.rustorio.domain.", "java.", "javax.", "jdk.", "sun."
    };

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

    private static boolean isParentDelegated(String name) {
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

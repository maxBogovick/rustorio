package com.rustorio.mod;

import com.rustorio.api.mod.RustorioMod;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Finds and instantiates a code mod's {@link RustorioMod} entry point from its own {@code .jar} —
 * standard {@link ServiceLoader} discovery (the mod jar declares its implementation in {@code
 * META-INF/services/com.rustorio.api.mod.RustorioMod}, the same mechanism any Java service provider
 * uses, not a bespoke convention), run against a fresh {@link ModClassLoader} scoped to that one jar.
 */
final class ModJarLoader {

    private ModJarLoader() {
    }

    /**
     * @param declaredEntryPoint the fully-qualified class name {@code mod.json} claims — checked
     *                           against what {@link ServiceLoader} actually finds, so a stale or
     *                           wrong {@code mod.json} is caught instead of silently ignored.
     * @throws ModLoadException if the jar can't be opened, declares zero or more than one {@link
     *                          RustorioMod} implementation, or the found implementation's class
     *                          doesn't match {@code declaredEntryPoint}.
     */
    static RustorioMod loadEntryPoint(Path jarFile, String declaredEntryPoint, ClassLoader parent) {
        URL jarUrl = toUrl(jarFile);
        ModClassLoader loader = new ModClassLoader(jarUrl, parent);
        List<RustorioMod> found = new ArrayList<>();
        for (RustorioMod mod : ServiceLoader.load(RustorioMod.class, loader)) {
            found.add(mod);
        }
        if (found.isEmpty()) {
            throw new ModLoadException(jarFile + ": no META-INF/services/" + RustorioMod.class.getName()
                    + " entry found — a code mod must declare exactly one " + RustorioMod.class.getSimpleName()
                    + " implementation there");
        }
        if (found.size() > 1) {
            throw new ModLoadException(jarFile + ": found " + found.size()
                    + " RustorioMod implementations via ServiceLoader — a mod jar must declare exactly one");
        }
        RustorioMod entryPoint = found.get(0);
        String actualClassName = entryPoint.getClass().getName();
        if (!actualClassName.equals(declaredEntryPoint)) {
            throw new ModLoadException(jarFile + ": mod.json declares entryPoint '" + declaredEntryPoint
                    + "' but ServiceLoader found '" + actualClassName + "'");
        }
        return entryPoint;
    }

    private static URL toUrl(Path jarFile) {
        try {
            return jarFile.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new ModLoadException(jarFile + ": not a valid jar path (" + e.getMessage() + ")", e);
        }
    }
}

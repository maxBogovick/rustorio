package com.rustorio.mod;

import com.rustorio.architecture.BuildOutputs;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The published {@code rustorio-api} jar must ship exactly the packages {@link ModClassLoader}
 * agrees to delegate to the parent — no more.
 *
 * <p>The finding this closes: the jar's packaging task said {@code include 'com/rustorio/**'} while
 * the loader's allowlist said {@code com.rustorio.api} plus {@code com.rustorio.domain}. Three whole
 * packages sat in the gap — the mod loader itself, the save repository, the game entry point. A mod
 * author could name {@code JsonSaveRepository}, compile green against the published coordinate, ship
 * it, and have it die with {@code ClassNotFoundException} the first time a player started the game:
 * the loader looks such a name up only inside the mod's own jar and never falls back to the parent.
 * Turning a compile error into a runtime failure at a player's machine is the exact thing publishing
 * an API surface is supposed to prevent, and nothing in the build noticed the gap for as long as it
 * existed.
 *
 * <p>Deliberately opens the BUILT ARTIFACT rather than reading the source tree or the build script.
 * Those would only prove that a pattern was written somewhere; the question is what a mod author
 * actually receives, and the jar is the only thing that answers it.
 *
 * <p>Lives in {@code com.rustorio.mod} rather than beside the other architecture tests because it
 * asks {@link ModClassLoader#isParentDelegated} itself instead of re-stating the allowlist. A copy
 * of that list here would be a second source of truth, and this test exists precisely because a
 * second source of truth had drifted.
 */
class ModApiSurfaceTest {

    /**
     * A class the jar must contain, so the assertion below cannot pass by the jar being empty of
     * anything a mod actually needs — {@link BuildOutputs#classNamesIn} already rejects a jar with
     * no classes at all, but a jar carrying only, say, the domain would still be useless.
     */
    private static final String MOD_ENTRY_POINT = "com.rustorio.api.mod.RustorioMod";

    @Test
    void apiJarShipsOnlyWhatTheModClassLoaderWillLoad() {
        List<String> shipped = BuildOutputs.classNamesIn(BuildOutputs.apiJar());

        List<String> notDelegated = new ArrayList<>();
        for (String className : shipped) {
            if (!ModClassLoader.isParentDelegated(className)) {
                notDelegated.add(className);
            }
        }

        assertTrue(shipped.contains(MOD_ENTRY_POINT),
                "the API jar has to carry " + MOD_ENTRY_POINT + " — a mod cannot implement an entry "
                        + "point it never receives");
        assertEquals(List.of(), notDelegated,
                "these classes ship in rustorio-api but ModClassLoader will not delegate them, so a "
                        + "mod that names one compiles green and dies with ClassNotFoundException in "
                        + "a player's game — either add the package to PARENT_DELEGATED_PREFIXES on "
                        + "purpose, or stop shipping it from the apiJar task");
    }
}

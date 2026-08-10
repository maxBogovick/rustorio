package com.rustorio.mod;

import com.rustorio.architecture.BuildOutputs;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A mod may only name the part of the engine {@link ModClassLoader} delegates to the parent. Every
 * other engine name is looked up inside the mod's own jar, is not there, and throws {@code
 * ClassNotFoundException} the first time a player loads the mod.
 *
 * <p><b>The compiler already enforces this, and that is not enough.</b> An in-repo mod's compile
 * classpath is the {@code apiJar} output rather than the engine's own output (see the source-set
 * block in {@code build.gradle}), so a mod naming {@code ModLoader} does not compile at all — an
 * error on the line that types it, which is both earlier and clearer than any test. But that
 * guarantee lives entirely in one line of a build script, and no test reads build scripts. Someone
 * hitting that compile error can "fix" it by putting {@code compileClasspath += sourceSets.main
 * .output} back — which is exactly how the line read before, so it looks like a restoration rather
 * than a regression — and from that moment the whole gate stays green while a mod imports the save
 * repository. {@code ModApiSurfaceTest} would not notice: it inspects the published jar, not the
 * mods.
 *
 * <p>That is the same reasoning {@code PackageBoundaryRulesTest.theEngineDoesNotDependOnAnyMod}
 * already records for the opposite direction ("the source-set split already makes the dependency
 * impossible to compile, and this rule would only fire if someone put a mod back into src/main").
 * The two checks fail independently: the compiler stops it while the build file says so, this rule
 * stops it regardless of what the build file says.
 *
 * <p>Scope is engine packages only. Whether a mod's THIRD-PARTY libraries travel inside its jar —
 * the other way a mod compiles green and dies on load — is {@code ModJarLibrariesTest}'s question,
 * not this one.
 */
class ModBoundaryRulesTest {

    @Test
    void aModDependsOnlyOnTheDelegatedEngineApi() {
        ArchRule rule = noClasses()
                .should().dependOnClassesThat(new NotDelegatedEngineClass())
                .because("a mod may only name the part of the engine ModClassLoader delegates to the "
                        + "parent (com.rustorio.api, com.rustorio.domain) — every other engine name "
                        + "is looked up inside the mod's own jar, is not there, and throws "
                        + "ClassNotFoundException the first time a player loads the mod");

        rule.check(modClasses());
    }

    /**
     * Asked separately rather than folded into the rule above: an import that silently found no
     * classes would make {@code noClasses().should()} pass for the emptiest of reasons, and the test
     * would then stay green for every future mod as well.
     */
    @Test
    void theModsAreActuallyOnDiskWhereThisTestLooksForThem() {
        assertFalse(modClasses().isEmpty(),
                "no compiled mod classes were found in " + BuildOutputs.modClassesDirs() + " — the "
                        + "boundary rule above would then hold vacuously, which is worse than not "
                        + "having it at all");
    }

    private static JavaClasses modClasses() {
        return new ClassFileImporter().importPaths(BuildOutputs.modClassesDirs());
    }

    /**
     * An engine class the loader will not delegate. Asks {@link ModClassLoader#isParentDelegated}
     * itself rather than restating the prefix list, so this rule cannot come to disagree with the
     * loader about where the engine ends — that drift is what the whole pair of tests exists for.
     */
    private static final class NotDelegatedEngineClass extends DescribedPredicate<JavaClass> {

        private NotDelegatedEngineClass() {
            // Reads as a clause after "no classes should depend on classes that ..." — ArchUnit
            // splices this description straight into the sentence it prints on failure.
            super("are engine classes the mod class loader will not delegate to the parent");
        }

        @Override
        public boolean test(JavaClass target) {
            String name = target.getName();
            boolean engineClass = name.startsWith("com.rustorio.") || name.startsWith("com.graphics.");
            return engineClass && !ModClassLoader.isParentDelegated(name);
        }
    }
}

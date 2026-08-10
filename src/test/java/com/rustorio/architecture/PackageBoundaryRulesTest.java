package com.rustorio.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Machine-checks the dependency directions each package already states in prose in its own
 * {@code package-info.java} — by bytecode import (ArchUnit), not by grepping {@code import}
 * lines, so a rename or reformat can't silently make a violation invisible. Restricted to
 * {@code src/main} ({@link ImportOption.DoNotIncludeTests}) — test fixtures reach across every
 * layer on purpose and aren't subject to these rules.
 */
@AnalyzeClasses(packages = {"com.rustorio", "com.graphics"}, importOptions = ImportOption.DoNotIncludeTests.class)
class PackageBoundaryRulesTest {

    /**
     * {@code com.rustorio.domain}'s own {@code package-info.java}: "Nothing here depends on
     * com.rustorio.domain.building, com.rustorio.domain.world, com.rustorio.persistence or
     * com.graphics — this is the innermost ring". Deliberately checks the plain package
     * {@code com.rustorio.domain} only (no {@code ..} suffix on the {@code that()} side) — sibling
     * packages one level down ({@code domain.building}, {@code domain.world}, {@code
     * domain.action}) have their own, separately stated rules.
     *
     * <p>The {@code com.graphics} half of that prose is NOT restated here: {@link
     * #nothingInTheGameDependsOnRendering} already forbids it for the entire {@code com.rustorio}
     * tree, this package included. Listing it twice cost a reader a comparison to discover the two
     * were saying the same thing, and would have let them drift into saying almost the same thing.
     */
    @ArchTest
    static final ArchRule domainIsTheInnermostRing = noClasses()
            .that().resideInAPackage("com.rustorio.domain")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.rustorio.domain.building..", "com.rustorio.domain.world..",
                    "com.rustorio.persistence..")
            .because("com.rustorio.domain is the innermost ring (package-info.java) — value types "
                    + "and strategies everything else is built from, depending on nothing themselves");

    /**
     * {@code com.rustorio.domain.building}'s own {@code package-info.java}: "Depends only on
     * com.rustorio.domain … never on the world package one level up, on persistence, or on the
     * rendering/input layer." Buildings talk back to {@code World} only through {@link
     * com.rustorio.domain.building.TickContext} (six methods), never by importing {@code World}.
     *
     * <p>The rendering half of that prose lives in {@link #nothingInTheGameDependsOnRendering},
     * which covers this package along with every other one under {@code com.rustorio} — same
     * reasoning as {@link #domainIsTheInnermostRing}.
     */
    @ArchTest
    static final ArchRule buildingDependsOnlyOnDomain = noClasses()
            .that().resideInAPackage("com.rustorio.domain.building")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.rustorio.domain.world..", "com.rustorio.persistence..")
            .because("com.rustorio.domain.building depends only on com.rustorio.domain "
                    + "(package-info.java) — the world/building dependency is one-way, world -> building");

    /**
     * {@code com.rustorio.persistence}'s own {@code package-info.java}: "the only packages allowed
     * to import Jackson are persistence and the mod loader". Every building's own {@code Codec}
     * converts its state record to plain JDK types Jackson can serialize generically, so the domain
     * module never carries a Jackson annotation itself. {@code com.rustorio.mod} joined this
     * allowlist when the mod loader started reading arbitrary JSON off disk ({@code mod.json},
     * {@code content/**}<!---->{@code .json}) — the same reason persistence needed it, for a
     * different file format.
     */
    /**
     * The headline boundary of the whole codebase: {@code com.graphics} knows about the game,
     * the game knows nothing about rendering or input. Stated ONCE, for the whole {@code
     * com.rustorio} tree, so a new package inherits it by existing rather than needing its own rule
     * — which is how {@code domain.world}, {@code domain.action}, {@code api..}, {@code mod} and
     * {@code persistence} came to be covered at all: they were checked by nothing but prose, and a
     * renderer type could have reached any of them without a test turning red.
     *
     * <p>The two rules above therefore do NOT repeat {@code com.graphics..} in their own forbidden
     * lists. They did once, back when this rule did not exist yet; leaving the duplicate in place
     * afterwards meant three rules had to be read together to answer "where is rendering forbidden",
     * and any one of them could have been narrowed without the others noticing.
     */
    @ArchTest
    static final ArchRule nothingInTheGameDependsOnRendering = noClasses()
            .that().resideInAPackage("com.rustorio..")
            .should().dependOnClassesThat().resideInAPackage("com.graphics..")
            .because("the dependency runs one way, com.graphics -> com.rustorio: the game has to be "
                    + "runnable, testable and moddable without a window");

    /**
     * The same boundary one level lower, and the part prose couldn't express: the game must not
     * touch libGDX itself either. Without this, a domain class could take a {@code Vector2} or a
     * {@code TextureRegion} in a signature and still satisfy the rule above — the type would come
     * from the engine, not from {@code com.graphics}. This is what "the domain doesn't accept
     * render types in signatures, plain {@code int} instead" means in bytecode, and it's also what
     * keeps the headless paths ({@code World.tick}, the mod loader, every domain test) free of a
     * dependency that needs a GL context to initialize.
     */
    @ArchTest
    static final ArchRule nothingInTheGameDependsOnLibGdx = noClasses()
            .that().resideInAPackage("com.rustorio..")
            .should().dependOnClassesThat().resideInAPackage("com.badlogic..")
            .because("libGDX types belong to the rendering layer; a domain that imports them can no "
                    + "longer run headless, and its signatures start leaking engine types to mods");

    /**
     * The moddability boundary, stated in the only direction that means anything: the ENGINE must
     * not know a mod exists. A mod naming engine types is the whole point of an engine; an engine
     * naming a mod's types is the defect — it makes that mod uninstallable, unshippable as a
     * separate artifact, and turns "add a building" back into "edit the game".
     *
     * <p>{@code com.webminer} is the mod that lives in this repository. It began as three archetype
     * classes inside {@code com.rustorio.domain.building}, three methods on {@code TickContext} and
     * a branch in the inspection panel — every one of which this rule now forbids, and none of
     * which any test noticed at the time, because the architecture tests only ever looked at
     * dependency DIRECTION between engine packages and never at whether a mod had grown into one.
     *
     * <p>No package is exempt any more. Two were, for as long as this repository had no way to
     * package a mod: the composition roots ({@code com.graphics.screen}, {@code com.rustorio.Main})
     * had to call the mod's registration by name, because a class in {@code src/main} cannot be
     * something {@code ServiceLoader} discovers in a jar. Now {@code webminerModJar} builds that
     * jar from its own source set and the mod loader finds it like any third-party mod's, so
     * nothing in the engine names it — and this rule says the whole engine, with no carve-outs.
     *
     * <p>Belt and braces on purpose: the source-set split already makes the dependency impossible
     * to compile, and this rule would only fire if someone put a mod back into {@code src/main}.
     * That is precisely the regression worth catching, because it is how the situation arose the
     * first time — nobody decided to couple the engine to a mod; the mod was simply in the same
     * source set, and nothing objected.
     */
    @ArchTest
    static final ArchRule theEngineDoesNotDependOnAnyMod = noClasses()
            .that().resideInAnyPackage("com.rustorio..", "com.graphics..")
            .should().dependOnClassesThat().resideInAPackage("com.webminer..")
            .because("a mod may name the engine; the engine naming a mod is what makes that mod "
                    + "impossible to uninstall or ship separately — webminer ships as its own jar "
                    + "(see the webminerModJar task) and is discovered at runtime, not compiled in");

    @ArchTest
    static final ArchRule onlyPersistenceOrModImportJackson = noClasses()
            .that().resideOutsideOfPackage("com.rustorio.persistence..")
            .and().resideOutsideOfPackage("com.rustorio.mod..")
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
            .because("only com.rustorio.persistence and com.rustorio.mod may import Jackson "
                    + "(package-info.java of each) — everywhere else stays a plain DTO");
}

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
     */
    @ArchTest
    static final ArchRule domainIsTheInnermostRing = noClasses()
            .that().resideInAPackage("com.rustorio.domain")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.rustorio.domain.building..", "com.rustorio.domain.world..",
                    "com.rustorio.persistence..", "com.graphics..")
            .because("com.rustorio.domain is the innermost ring (package-info.java) — value types "
                    + "and strategies everything else is built from, depending on nothing themselves");

    /**
     * {@code com.rustorio.domain.building}'s own {@code package-info.java}: "Depends only on
     * com.rustorio.domain … never on the world package one level up, on persistence, or on the
     * rendering/input layer." Buildings talk back to {@code World} only through {@link
     * com.rustorio.domain.building.TickContext} (six methods), never by importing {@code World}.
     */
    @ArchTest
    static final ArchRule buildingDependsOnlyOnDomain = noClasses()
            .that().resideInAPackage("com.rustorio.domain.building")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.rustorio.domain.world..", "com.rustorio.persistence..", "com.graphics..")
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
    @ArchTest
    static final ArchRule onlyPersistenceOrModImportJackson = noClasses()
            .that().resideOutsideOfPackage("com.rustorio.persistence..")
            .and().resideOutsideOfPackage("com.rustorio.mod..")
            .should().dependOnClassesThat().resideInAPackage("com.fasterxml.jackson..")
            .because("only com.rustorio.persistence and com.rustorio.mod may import Jackson "
                    + "(package-info.java of each) — everywhere else stays a plain DTO");
}

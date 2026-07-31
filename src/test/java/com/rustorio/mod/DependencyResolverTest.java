package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DependencyResolverTest {

    private static ModDescriptor mod(String id, ModDependency... dependencies) {
        return new ModDescriptor(new ModId(id), new SemVer(1, 0, 0), null, List.of(dependencies));
    }

    private static ModDependency dependsOn(String id) {
        return new ModDependency(new ModId(id), VersionRange.parse("*"));
    }

    @Test
    void independentModsAreOrderedByIdRegardlessOfInputOrder() {
        ModDescriptor alpha = mod("alpha");
        ModDescriptor beta = mod("beta");
        ModDescriptor gamma = mod("gamma");

        List<ModDescriptor> forward = DependencyResolver.resolve(List.of(alpha, beta, gamma));
        List<ModDescriptor> reversed = DependencyResolver.resolve(List.of(gamma, beta, alpha));

        List<ModId> expected = List.of(new ModId("alpha"), new ModId("beta"), new ModId("gamma"));
        assertEquals(expected, ids(forward), "forward input order must yield id-sorted output");
        assertEquals(expected, ids(reversed), "reversed input order must yield the SAME output — order is a function of the set, not the list");
    }

    @Test
    void aDependsOnBDependsOnCLoadsCThenBThenA() {
        ModDescriptor c = mod("c");
        ModDescriptor b = mod("b", dependsOn("c"));
        ModDescriptor a = mod("a", dependsOn("b"));

        List<ModDescriptor> order = DependencyResolver.resolve(List.of(a, b, c));

        assertEquals(List.of(new ModId("c"), new ModId("b"), new ModId("a")), ids(order),
                "each mod's own dependency must load before it");
    }

    @Test
    void missingDependencyNamesBothMods() {
        ModDescriptor a = mod("a", dependsOn("ghost"));

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> DependencyResolver.resolve(List.of(a)));

        assertTrue(thrown.getMessage().contains("'a'"));
        assertTrue(thrown.getMessage().contains("'ghost'"));
    }

    @Test
    void versionRangeMismatchNamesBothTheRequirementAndWhatIsPresent() {
        ModDescriptor b = new ModDescriptor(new ModId("b"), new SemVer(1, 0, 0), null, List.of());
        ModDescriptor a = new ModDescriptor(new ModId("a"), new SemVer(1, 0, 0), null,
                List.of(new ModDependency(new ModId("b"), VersionRange.parse(">=2.0.0"))));

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> DependencyResolver.resolve(List.of(a, b)));

        assertTrue(thrown.getMessage().contains(">=2.0.0"));
        assertTrue(thrown.getMessage().contains("1.0.0"));
    }

    @Test
    void aCycleIsReportedNotStackOverflown() {
        ModDescriptor a = mod("a", dependsOn("b"));
        ModDescriptor b = mod("b", dependsOn("a"));

        ModLoadException thrown = assertThrows(ModLoadException.class, () -> DependencyResolver.resolve(List.of(a, b)));

        assertTrue(thrown.getMessage().contains("cycle"));
        assertTrue(thrown.getMessage().contains("a"));
        assertTrue(thrown.getMessage().contains("b"));
    }

    @Test
    void duplicateModIdIsRejected() {
        ModDescriptor first = mod("dup");
        ModDescriptor second = mod("dup");

        assertThrows(ModLoadException.class, () -> DependencyResolver.resolve(List.of(first, second)));
    }

    private static List<ModId> ids(List<ModDescriptor> mods) {
        return mods.stream().map(ModDescriptor::id).toList();
    }
}

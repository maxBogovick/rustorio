package com.rustorio.mod;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Orders a set of mods so every mod loads after everything it depends on (Kahn's algorithm) —
 * checking first that every named dependency is actually present and that its version satisfies
 * the requested {@link VersionRange}.
 *
 * <p>Ties (two or more mods that are both currently loadable, neither depending on the other) are
 * broken by {@link ModId}'s own lexicographic order, never by the mods' position in the input list
 * — {@code resolve} is a pure function of the SET of mods, not of whatever order a directory
 * listing happened to hand the caller. Simulation determinism demands this: two different physical
 * orderings of the same mod set on disk must produce the same load order, and therefore the same
 * {@code Registry} {@code rawId}s and the same merged recipe list.
 */
public final class DependencyResolver {

    private DependencyResolver() {
    }

    /** @throws ModLoadException on a duplicate id, a missing dependency, an unsatisfied version range, or a dependency cycle. */
    public static List<ModDescriptor> resolve(List<ModDescriptor> mods) {
        Map<ModId, ModDescriptor> byId = indexById(mods);
        checkDependenciesResolvable(byId);
        return topologicalOrder(byId);
    }

    private static Map<ModId, ModDescriptor> indexById(List<ModDescriptor> mods) {
        Map<ModId, ModDescriptor> byId = new LinkedHashMap<>();
        for (ModDescriptor mod : mods) {
            if (byId.put(mod.id(), mod) != null) {
                throw new ModLoadException("duplicate mod id: '" + mod.id() + "'");
            }
        }
        return byId;
    }

    private static void checkDependenciesResolvable(Map<ModId, ModDescriptor> byId) {
        for (ModDescriptor mod : byId.values()) {
            for (ModDependency dependency : mod.dependencies()) {
                ModDescriptor found = byId.get(dependency.modId());
                // Culprit is the DEPENDENT, not the dependency: the missing or wrong-version mod
                // may not be installed at all, and removing the mod that asked for it is what
                // actually makes the set loadable again. This is also how a mod skipped for its own
                // reasons cascades to everything that needed it, without a second cascade pass.
                if (found == null) {
                    throw new ModLoadException("mod '" + mod.id() + "' depends on '" + dependency.modId()
                            + "' (" + dependency.range() + "), which is not present", mod.id());
                }
                if (!dependency.range().matches(found.version())) {
                    throw new ModLoadException("mod '" + mod.id() + "' requires '" + dependency.modId() + "' "
                            + dependency.range() + ", but the present version is " + found.version(), mod.id());
                }
            }
        }
    }

    /** Kahn's algorithm: dependency-free mods first, breaking ties by {@link ModId} order — see the class javadoc. */
    private static List<ModDescriptor> topologicalOrder(Map<ModId, ModDescriptor> byId) {
        Map<ModId, Integer> remainingDependencies = new LinkedHashMap<>();
        Map<ModId, List<ModId>> dependents = new TreeMap<>();
        for (ModDescriptor mod : byId.values()) {
            remainingDependencies.put(mod.id(), mod.dependencies().size());
            dependents.computeIfAbsent(mod.id(), k -> new ArrayList<>());
        }
        for (ModDescriptor mod : byId.values()) {
            for (ModDependency dependency : mod.dependencies()) {
                // Every dependency.modId() is a key of dependents by construction: it's the same
                // key set as byId, and checkDependenciesResolvable (called before this method,
                // from resolve()) already verified every dependency is present in byId.
                List<ModId> dependentsOfThisDependency = Objects.requireNonNull(dependents.get(dependency.modId()));
                dependentsOfThisDependency.add(mod.id());
            }
        }

        TreeSet<ModId> ready = new TreeSet<>();
        remainingDependencies.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        List<ModDescriptor> order = new ArrayList<>(byId.size());
        while (!ready.isEmpty()) {
            ModId next = ready.pollFirst();
            order.add(Objects.requireNonNull(byId.get(next)));
            List<ModId> itsDependents = Objects.requireNonNull(dependents.get(next));
            for (ModId dependent : itsDependents) {
                int remaining = remainingDependencies.merge(dependent, -1, Integer::sum);
                if (remaining == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() < byId.size()) {
            List<ModId> stuck = remainingDependencies.entrySet().stream()
                    .filter(e -> e.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new ModLoadException("dependency cycle among mods: " + stuck);
        }
        return order;
    }
}

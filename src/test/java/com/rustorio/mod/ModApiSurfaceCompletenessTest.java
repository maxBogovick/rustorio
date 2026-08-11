package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.rustorio.architecture.BuildOutputs;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;

/**
 * {@link ModApiSurfaceTest} only checks that every <em>shipped class name</em> is loadable by
 * {@link ModClassLoader}. That missed phase-3 holes where the jar still published types whose
 * method signatures named classes the jar had just removed ({@code PlayerAction.apply(World)},
 * {@code FluidNode.joinNetwork(FluidNetwork)}). A mod compiling against such a jar fails with
 * {@code cannot access … class file for … not found} — the surface looked allowlisted and was
 * still unusable.
 *
 * <p>This test walks the constant-pool dependencies of every class in the built {@code
 * rustorio-api} jar and requires every {@code com.rustorio.*} dependency to also be present in that
 * jar. JDK and third-party types (JSpecify, …) are allowed to resolve outside it.
 *
 * <p>Uses {@link ClassFileImporter#importJar}, not {@code importPath}: ArchUnit's path importer
 * walks a directory tree and treats a plain {@code .jar} file as an empty root, which would make
 * this check pass while measuring nothing.
 */
class ModApiSurfaceCompletenessTest {

    @Test
    void everyRustorioTypeNamedInApiJarSignaturesIsAlsoShipped() {
        JavaClasses shipped = importApiJar();
        assertFalse(shipped.isEmpty(),
                "rustorio-api imported as zero classes — the completeness check measured nothing");

        Set<String> shippedNames = new LinkedHashSet<>();
        for (JavaClass type : shipped) {
            shippedNames.add(type.getName());
        }

        List<String> missing = new ArrayList<>();
        for (JavaClass type : shipped) {
            for (Dependency dependency : type.getDirectDependenciesFromSelf()) {
                JavaClass target = dependency.getTargetClass();
                String name = target.getName();
                if (!name.startsWith("com.rustorio.")) {
                    continue;
                }
                if (shippedNames.contains(name)) {
                    continue;
                }
                missing.add(type.getName() + " -> " + name);
            }
        }

        assertEquals(List.of(), missing,
                "rustorio-api names these engine types in signatures but does not ship them — a mod "
                        + "cannot compile against the jar. Either include the missing class or stop "
                        + "shipping the referencing type / redesign the signature");
    }

    private static JavaClasses importApiJar() {
        try (JarFile jar = new JarFile(BuildOutputs.apiJar().toFile())) {
            return new ClassFileImporter().importJar(jar);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

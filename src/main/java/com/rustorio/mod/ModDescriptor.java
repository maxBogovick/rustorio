package com.rustorio.mod;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A parsed {@code mod.json} — everything the loader needs to resolve this mod's place in the load
 * order and, if it has one, find its code entry point. {@code entryPoint} is the fully-qualified
 * class name of this mod's {@code RustorioMod} implementation, {@code null} for a data-only mod
 * (JSON content, no jar) — {@code null} rather than {@code Optional} because this is a record
 * component (Effective Java Item 55, already this project's convention — see {@code Recipe.input2}).
 */
public record ModDescriptor(ModId id, SemVer version, @Nullable String entryPoint, List<ModDependency> dependencies) {

    public ModDescriptor {
        dependencies = List.copyOf(dependencies);
    }
}

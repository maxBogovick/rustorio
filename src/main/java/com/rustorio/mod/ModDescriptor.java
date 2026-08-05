package com.rustorio.mod;

import com.rustorio.api.content.ContentId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A parsed {@code mod.json} — everything the loader needs to resolve this mod's place in the load
 * order and, if it has one, find its code entry point. {@code entryPoint} is the fully-qualified
 * class name of this mod's {@code RustorioMod} implementation, {@code null} for a data-only mod
 * (JSON content, no jar) — {@code null} rather than {@code Optional} because this is a record
 * component (Effective Java Item 55, already this project's convention — see {@code Recipe.input2}).
 *
 * <p>{@code prototypeRenames} is {@code oldId -> newId} for every building prototype this mod has
 * renamed since a previous release of itself — the declaration a save loaded later needs so a
 * merely-renamed prototype isn't reported as missing content. Declared per mod rather than
 * collected globally because only the mod that performed a rename knows it happened; {@link
 * ModLoader} merges every mod's map and hands the result to whoever opens the save.
 *
 * <p>{@link LinkedHashMap}, not {@code Map.copyOf}: the merge in {@link ModLoader} reports the
 * FIRST conflicting rename it meets, so which one that is has to be the file's own declaration
 * order rather than a hash order that changes between JVM runs.
 *
 * <p>{@code minEngineVersion} is the oldest engine this mod claims to work against, or {@code null}
 * for a mod that makes no claim. A mod written for a newer engine is refused with a sentence naming
 * both versions, instead of loading and failing later against an API that has moved underneath it.
 */
public record ModDescriptor(ModId id, SemVer version, @Nullable String entryPoint,
        List<ModDependency> dependencies, Map<ContentId, ContentId> prototypeRenames,
        ModMetadata metadata, @Nullable SemVer minEngineVersion) {

    public ModDescriptor {
        dependencies = List.copyOf(dependencies);
        prototypeRenames = Collections.unmodifiableMap(new LinkedHashMap<>(prototypeRenames));
    }

    /** A mod with no renames, no metadata beyond its id, and no engine floor — the shortest thing a {@code mod.json} can be. */
    public ModDescriptor(ModId id, SemVer version, @Nullable String entryPoint, List<ModDependency> dependencies) {
        this(id, version, entryPoint, dependencies, Map.of(), ModMetadata.unnamed(id), null);
    }
}

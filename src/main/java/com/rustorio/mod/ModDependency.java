package com.rustorio.mod;

/** One entry of {@code mod.json}'s {@code dependencies} array: another mod this one needs, and which of its versions are acceptable. */
public record ModDependency(ModId modId, VersionRange range) {
}

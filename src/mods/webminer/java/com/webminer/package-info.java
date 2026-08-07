/**
 * A mod: three building archetypes ({@link com.webminer.WebMiner}, {@link com.webminer.Monitor},
 * {@link com.webminer.Interpreter}), the capability they run on ({@link
 * com.webminer.FetchService}), its real HTTP implementation, and the entry point that hands all of
 * it to the engine ({@link com.webminer.WebMinerMod}).
 *
 * <p><b>Not part of the engine, and no longer compiled with it.</b> This package is its own Gradle
 * source set ({@code src/mods/webminer}), packaged by the {@code webminerModJar} task into {@code
 * resources/mods/webminer/webminer.jar} — the file {@code ModLoader} discovers through {@code
 * ServiceLoader} at runtime, exactly as it would any third-party mod's. Nothing in {@code
 * com.rustorio} or {@code com.graphics} names anything here; {@code
 * PackageBoundaryRulesTest.theEngineDoesNotDependOnAnyMod} checks that, and the source-set split
 * makes it impossible to compile in the first place.
 *
 * <p>Everything about that arrangement had to be built, and the history is the argument for it:
 * these archetypes began inside {@code com.rustorio.domain.building}, reached the world through
 * three {@code TickContext} methods written for them alone, and were described by a branch in the
 * renderer that named this package. Each is gone — replaced by {@link
 * com.rustorio.domain.building.ServiceKey}, one generic {@code service} lookup, and {@link
 * com.rustorio.domain.building.InspectableBuilding}. None of it was noticed while this code shared
 * a source set with the engine, because sharing one is what made it invisible.
 *
 * <p>NullAway does not run here, deliberately: a mod is compiled by whoever wrote it, under
 * whatever checks they chose, and this one exists to demonstrate that path honestly (see
 * {@code build.gradle}). The {@code @NullMarked} below is this mod's own contract with itself.
 */
@NullMarked
package com.webminer;

import org.jspecify.annotations.NullMarked;

/**
 * The mod loader's own implementation: parsing {@code mod.json}, resolving dependencies, isolating
 * classloading, running a mod's three-round lifecycle, and loading JSON content. Depends on {@code
 * com.rustorio.api.*} and {@code com.rustorio.domain}/{@code domain.building} — same reasoning as
 * {@code com.rustorio.api.mod}'s own package-info (no standalone {@code rustorio-api} artifact
 * exists yet).
 *
 * <p>Like {@code com.rustorio.persistence}, this package is allowed to import Jackson: it reads
 * arbitrary JSON off disk ({@code mod.json}, {@code content/**}<!---->{@code .json}), not the save
 * format — see {@code PackageBoundaryRulesTest.onlyPersistenceOrModImportJackson}, extended for
 * this package rather than kept persistence-only.
 */
@NullMarked
package com.rustorio.mod;

import org.jspecify.annotations.NullMarked;

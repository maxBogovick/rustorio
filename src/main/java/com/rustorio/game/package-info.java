/**
 * The composition root: the one place that assembles a playable game out of the mod loader, the
 * domain and the save layer. Depends on {@code com.rustorio.mod}, {@code com.rustorio.domain}/
 * {@code domain.building}/{@code domain.world} and {@code com.rustorio.persistence}; nothing
 * depends on it except an entry point ({@code com.graphics.screen.GameScreen}, {@code
 * com.rustorio.Main}).
 *
 * <p>Exists because its absence was a real defect, not for symmetry: every caller used to assemble
 * these parts itself, and each one forgot a different piece — the windowed game never attached the
 * mod event bus to the world and built its save repository against the vanilla-only item registry,
 * while the headless demo never went through the mod loader at all. One assembly with one test is
 * what makes "the mod system is wired into the game" a checkable claim instead of a hopeful one.
 *
 * <p>Deliberately NOT inside {@code com.rustorio.mod}: assembling a game means knowing about the
 * save layer, and the mod loader has no business importing {@code com.rustorio.persistence}.
 */
@NullMarked
package com.rustorio.game;

import org.jspecify.annotations.NullMarked;

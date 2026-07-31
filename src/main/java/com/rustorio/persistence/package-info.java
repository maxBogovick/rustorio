/**
 * Save/load, isolated behind {@link com.rustorio.persistence.SaveRepository} (Repository
 * pattern). Together with {@code com.rustorio.mod} (the mod loader, which reads {@code mod.json}
 * and content JSON off disk), this is one of the only two packages allowed to import Jackson —
 * {@code com.rustorio.domain} and {@code com.rustorio.domain.building} know nothing about JSON or
 * files; every building's own {@code Codec} converts its state record to plain JDK types ({@code
 * Map}/{@code List}/{@code String}/numbers) that Jackson can serialize generically, so no domain
 * type ever carries a Jackson annotation itself.
 */
@NullMarked
package com.rustorio.persistence;

import org.jspecify.annotations.NullMarked;

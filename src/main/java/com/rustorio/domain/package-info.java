/**
 * Pure game domain: items, directions, technologies, recipes and world generation rules.
 *
 * <p>Nothing here depends on {@code com.rustorio.domain.building}, {@code
 * com.rustorio.domain.world}, {@code com.rustorio.persistence} or {@code com.graphics} — this is
 * the innermost ring of the architecture (ports-and-adapters / hexagonal style): value types and
 * small strategies that everything else is built from, depending on nothing themselves.
 */
@NullMarked
package com.rustorio.domain;

import org.jspecify.annotations.NullMarked;

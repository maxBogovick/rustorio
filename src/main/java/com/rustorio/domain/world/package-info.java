/**
 * The play field itself: {@link com.rustorio.domain.world.World}, production tracking, and the
 * observer contract buildings publish events through. Depends on {@code com.rustorio.domain} and
 * {@code com.rustorio.domain.building} (to implement {@link
 * com.rustorio.domain.building.TickContext} and to hold {@link com.rustorio.domain.building.Building}
 * instances). Nothing in {@code com.rustorio.domain.building} depends back — the dependency is
 * one-way (P3-01, BUG_FIX_PROGRESS.md). Also depends on {@code com.rustorio.api.content} for
 * {@link com.rustorio.api.content.ContentId} — {@link com.rustorio.domain.world.World#place} places
 * any registered prototype, not just the closed {@link com.rustorio.domain.BuildingType} set.
 */
@NullMarked
package com.rustorio.domain.world;

import org.jspecify.annotations.NullMarked;

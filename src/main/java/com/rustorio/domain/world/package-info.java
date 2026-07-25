/**
 * The play field itself: {@link com.rustorio.domain.world.World}, production tracking, and the
 * observer contract buildings publish events through. Depends on {@code com.rustorio.domain} and
 * {@code com.rustorio.domain.building}; nothing in {@code com.rustorio.domain.building} depends
 * back on this package except through the {@code World} parameter each building already receives.
 */
@NullMarked
package com.rustorio.domain.world;

import org.jspecify.annotations.NullMarked;

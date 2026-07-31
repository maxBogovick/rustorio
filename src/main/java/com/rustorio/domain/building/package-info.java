/**
 * Every kind of building, the open {@link com.rustorio.domain.building.Building} type they all
 * implement (any class, anywhere, can implement it — not just the ones living in this package),
 * and the {@link com.rustorio.domain.building.BuildingFactory} that creates and restores the
 * vanilla ones. Depends only on {@code com.rustorio.domain} (items, directions, recipes) — never
 * on the world package one level up, on persistence, or on the rendering/input layer.
 *
 * <p>Buildings never see {@code World} itself: {@link com.rustorio.domain.building.TickContext},
 * declared right here, is the full vocabulary a building has for calling back into the world
 * during a tick — six methods, not the whole aggregate root. {@code World} implements it from the
 * other side of the package boundary (P3-01, BUG_FIX_PROGRESS.md); this package doesn't know or
 * care that it does.
 */
@NullMarked
package com.rustorio.domain.building;

import org.jspecify.annotations.NullMarked;

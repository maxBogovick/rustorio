/**
 * Every kind of building, the sealed {@link com.rustorio.domain.building.Building} type they all
 * implement, and the {@link com.rustorio.domain.building.BuildingFactory} that creates and
 * restores them. Depends only on {@code com.rustorio.domain} (items, directions, recipes) and
 * {@code com.rustorio.domain.world} (to call back into {@code World} during a tick) — never on
 * {@code com.rustorio.persistence} or {@code com.graphics}.
 */
@NullMarked
package com.rustorio.domain.building;

import org.jspecify.annotations.NullMarked;

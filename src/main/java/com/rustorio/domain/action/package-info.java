/**
 * Command pattern: player intents reified as objects ({@link
 * com.rustorio.domain.action.PlayerAction} and its implementations), plus the undo/redo stack
 * ({@link com.rustorio.domain.action.ActionHistory}) that plays them back against a {@code
 * World}. Depends on {@code com.rustorio.domain.world} and {@code com.rustorio.domain.building};
 * knows nothing about input devices or rendering.
 */
@NullMarked
package com.rustorio.domain.action;

import org.jspecify.annotations.NullMarked;

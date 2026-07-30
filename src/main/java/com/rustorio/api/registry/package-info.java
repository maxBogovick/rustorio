/**
 * {@link com.rustorio.api.registry.Registry} — the generic, freeze-once content container every
 * future registry (ores, building prototypes, recipes, …) will be an instance of. Depends only on
 * {@code com.rustorio.api.content} ({@link com.rustorio.api.content.ContentId}); nothing here
 * knows about any concrete content type.
 */
@NullMarked
package com.rustorio.api.registry;

import org.jspecify.annotations.NullMarked;

/**
 * Engine-side implementations of {@link com.rustorio.api.dsl.ContentDsl} — not part of the
 * published API surface a mod compiles against; a mod only sees the interfaces in
 * {@code com.rustorio.api.dsl} and reaches them through {@link
 * com.rustorio.api.mod.RegistrationContext#content()}.
 *
 * <p>Depends on {@code com.rustorio.api.*} and {@code com.rustorio.domain}/{@code domain.building}
 * the same way the rest of {@code com.rustorio.mod} does.
 */
@NullMarked
package com.rustorio.mod.dsl;

import org.jspecify.annotations.NullMarked;

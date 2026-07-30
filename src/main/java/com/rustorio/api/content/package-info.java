/**
 * The stable, mod-facing identity type — {@link com.rustorio.api.content.ContentId} — that every
 * future content registry keys on. Lives under {@code com.rustorio.api} so it can move into a
 * standalone API artifact later without another package rename: nothing here depends on anything
 * outside this package.
 */
@NullMarked
package com.rustorio.api.content;

import org.jspecify.annotations.NullMarked;

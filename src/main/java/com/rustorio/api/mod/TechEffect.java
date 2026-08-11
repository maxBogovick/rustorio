package com.rustorio.api.mod;

import com.rustorio.api.content.ContentId;

/**
 * A named gameplay effect a {@link com.rustorio.domain.TechType} may grant when unlocked.
 *
 * <p>Effects are addressable content: a mod registers one here, lists its id on a technology's
 * {@code effects} field, and buildings/UI ask {@link com.rustorio.domain.ResearchView#hasEffect}
 * whether any unlocked tech granted it. Phase 1 ships markers only — the object is an identity for
 * load validation and {@code hasEffect}; side-effect hooks at unlock time are deliberately not part
 * of this surface yet (publishing an unused {@code onUnlock} would be harder to take back than to
 * add later).
 */
public interface TechEffect {

    ContentId id();
}

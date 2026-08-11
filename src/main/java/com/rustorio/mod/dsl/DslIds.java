package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;

/** Shared path → {@link ContentId} rules for drafts under one mod namespace. */
final class DslIds {

    private DslIds() {
    }

    /**
     * Bare path → current mod; full {@code namespace:path} as-is. Placement rules are special-cased
     * in {@link BuildingDslImpl} so {@code needs_passable_terrain} resolves under {@code rustorio:}.
     */
    static ContentId resolve(RegistrationContext context, String pathOrId) {
        return pathOrId.indexOf(':') >= 0
                ? ContentId.of(pathOrId)
                : new ContentId(context.modNamespace(), pathOrId);
    }

    /**
     * Bare placement name: try {@code rustorio:<name>} first (the four shipped rules), then this
     * mod's namespace — so {@code needs_passable_terrain} and a mod's own short rule id both work.
     * Full {@code namespace:path} is taken as-is.
     */
    static ContentId resolvePlacement(RegistrationContext context, String pathOrId) {
        if (pathOrId.indexOf(':') >= 0) {
            return ContentId.of(pathOrId);
        }
        ContentId vanilla = new ContentId("rustorio", pathOrId);
        if (context.placementRules().peek(vanilla).isPresent()) {
            return vanilla;
        }
        return new ContentId(context.modNamespace(), pathOrId);
    }
}

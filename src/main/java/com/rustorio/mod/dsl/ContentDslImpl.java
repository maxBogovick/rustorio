package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.dsl.BuildingDsl;
import com.rustorio.api.dsl.ContentDsl;
import com.rustorio.api.dsl.FluidDraft;
import com.rustorio.api.dsl.ItemDraft;
import com.rustorio.api.dsl.MapDraft;
import com.rustorio.api.dsl.RecipeDraft;
import com.rustorio.api.mod.RegistrationContext;

/**
 * {@link ContentDsl} backed by a mod-scoped {@link RegistrationContext} — one instance per
 * {@code content()} call site (the scoped context holds it).
 *
 * <p>Public only so {@code com.rustorio.mod.ModScopedRegistrationContext} can construct it; not
 * part of the published API jar (that jar ships {@code api.dsl} interfaces, not this package).
 */
public final class ContentDslImpl implements ContentDsl {

    private final RegistrationContext context;

    public ContentDslImpl(RegistrationContext context) {
        this.context = context;
    }

    @Override
    public ItemDraft item(String path) {
        return new ItemDraftImpl(context, resolveOwn(path));
    }

    @Override
    public RecipeDraft recipe(String path) {
        return new RecipeDraftImpl(context, resolveOwn(path));
    }

    @Override
    public MapDraft map(String path) {
        return new MapDraftImpl(context, resolveOwn(path));
    }

    @Override
    public FluidDraft fluid(String path) {
        return new FluidDraftImpl(context, resolveOwn(path));
    }

    @Override
    public BuildingDsl building(String path) {
        return new BuildingDslImpl(context, resolveOwn(path));
    }

    private ContentId resolveOwn(String path) {
        if (path.indexOf(':') >= 0) {
            throw new IllegalArgumentException(
                    "content path must be a bare path under this mod (got '" + path
                            + "') — the namespace is taken from mod.json");
        }
        return new ContentId(context.modNamespace(), path);
    }
}

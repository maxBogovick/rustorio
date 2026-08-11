package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.dsl.MapDraft;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.AuthoredMap;
import com.rustorio.domain.OrePatch;
import com.rustorio.domain.TerrainPatch;
import java.util.ArrayList;
import java.util.List;

final class MapDraftImpl implements MapDraft {

    private final RegistrationContext context;
    private final ContentId id;
    private final List<OrePatch> orePatches = new ArrayList<>();
    private final List<TerrainPatch> terrainPatches = new ArrayList<>();

    MapDraftImpl(RegistrationContext context, ContentId id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public MapDraft label(String label) {
        // AuthoredMap has no label field yet (JSON accepts and ignores it the same way) — kept so
        // the fluent API matches content/maps/*.json without inventing a save-facing field early.
        return this;
    }

    @Override
    public MapDraft ore(String itemPathOrId, int cx, int cy, int radius) {
        requirePositiveRadius(radius);
        orePatches.add(new OrePatch(cx, cy, radius, context.requireItem(itemPathOrId)));
        return this;
    }

    @Override
    public MapDraft terrain(String itemPathOrId, int cx, int cy, int radius) {
        requirePositiveRadius(radius);
        terrainPatches.add(new TerrainPatch(cx, cy, radius, context.requireItem(itemPathOrId)));
        return this;
    }

    @Override
    public ContentId register() {
        context.maps().register(id, new AuthoredMap(id, orePatches, terrainPatches));
        return id;
    }

    private static void requirePositiveRadius(int radius) {
        if (radius <= 0) {
            throw new IllegalArgumentException("map patch radius must be positive, was " + radius);
        }
    }
}

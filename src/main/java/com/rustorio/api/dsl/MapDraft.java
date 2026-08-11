package com.rustorio.api.dsl;

import com.rustorio.api.content.ContentId;

/** Fluent registration of one {@link com.rustorio.domain.AuthoredMap}. */
public interface MapDraft {

    MapDraft label(String label);

    MapDraft ore(String itemPathOrId, int cx, int cy, int radius);

    MapDraft terrain(String itemPathOrId, int cx, int cy, int radius);

    ContentId register();
}

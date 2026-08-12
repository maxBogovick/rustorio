package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;

/** Cross-reference checks for a {@link VisibilityRule} at mod-load time. */
public interface VisibilityReferenceCheck {

    void requireItem(ContentId itemId, ContentId owner);

    void requireBuilding(ContentId buildingId, ContentId owner);

    void requireTech(ContentId techId, ContentId owner);

    void requireEffect(ContentId effectId, ContentId owner);
}

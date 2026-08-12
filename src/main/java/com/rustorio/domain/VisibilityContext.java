package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.ItemType;

/**
 * Read-only snapshot of progression inputs for {@link com.rustorio.domain.building.VisibilityRule}
 * — lives in the inner domain ring so building code never imports {@code domain.world}.
 */
public interface VisibilityContext {

    boolean hasProduced(ContentId itemId);

    boolean hasPlaced(ContentId buildingId);

    boolean isUnlocked(ContentId techId);

    boolean hasEffect(ContentId effectId);

    /** Resolves a label for a produced-item gate — implemented where item registries live. */
    String itemLabel(ContentId itemId);

    String buildingLabel(ContentId buildingId);

    String techLabel(ContentId techId);

    String effectLabel(ContentId effectId);
}

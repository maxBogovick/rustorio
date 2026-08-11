package com.rustorio.api.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.ItemShape;

/** Fluent registration of one {@link com.rustorio.domain.ItemType}. */
public interface ItemDraft {

    ItemDraft label(String label);

    ItemDraft color(int rgb);

    ItemDraft shape(ItemShape shape);

    /** Marks the item as research-grade (feeds a lab). */
    ItemDraft researchGrade();

    /** Registers into the items registry and returns the assigned id. */
    ContentId register();
}

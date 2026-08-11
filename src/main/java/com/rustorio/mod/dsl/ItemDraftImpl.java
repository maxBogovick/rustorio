package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.dsl.ItemDraft;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.model.ItemType;
import org.jspecify.annotations.Nullable;

final class ItemDraftImpl implements ItemDraft {

    private final RegistrationContext context;
    private final ContentId id;
    private @Nullable String label;
    private int colorRgb = 0x888888;
    private ItemShape shape = ItemShape.CIRCLE;
    private boolean researchGrade;

    ItemDraftImpl(RegistrationContext context, ContentId id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public ItemDraft label(String label) {
        this.label = label;
        return this;
    }

    @Override
    public ItemDraft color(int rgb) {
        this.colorRgb = rgb;
        return this;
    }

    @Override
    public ItemDraft shape(ItemShape shape) {
        this.shape = shape;
        return this;
    }

    @Override
    public ItemDraft researchGrade() {
        this.researchGrade = true;
        return this;
    }

    @Override
    public ContentId register() {
        String resolvedLabel = label != null ? label : id.path();
        context.items().register(id, new ItemType(id, resolvedLabel, researchGrade, colorRgb, shape));
        return id;
    }
}

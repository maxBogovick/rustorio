package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.dsl.FluidDraft;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.content.model.FluidType;
import org.jspecify.annotations.Nullable;

final class FluidDraftImpl implements FluidDraft {

    private final RegistrationContext context;
    private final ContentId id;
    private @Nullable String label;
    private int colorRgb = 0x4488CC;

    FluidDraftImpl(RegistrationContext context, ContentId id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public FluidDraft label(String label) {
        this.label = label;
        return this;
    }

    @Override
    public FluidDraft color(int rgb) {
        this.colorRgb = rgb;
        return this;
    }

    @Override
    public ContentId register() {
        String resolvedLabel = label != null ? label : id.path();
        context.fluids().register(id, new FluidType(id, resolvedLabel, colorRgb));
        return id;
    }
}

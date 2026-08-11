package com.rustorio.api.dsl;

import com.rustorio.api.content.ContentId;

/** Fluent registration of one {@link com.rustorio.domain.FluidType}. */
public interface FluidDraft {

    FluidDraft label(String label);

    /** Packed {@code 0xRRGGBB}. */
    FluidDraft color(int rgb);

    ContentId register();
}

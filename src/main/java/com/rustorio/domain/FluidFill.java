package com.rustorio.domain;

import com.rustorio.api.content.model.FluidType;

/**
 * A fill bar to draw on a fluid tile: which fluid it holds and how full its network is, {@code
 * percent} from 0 to 100. Carried on {@link Appearance} so the building decides its own look and
 * the renderer stays dumb — the same split every other field of {@code Appearance} follows.
 *
 * <p><b>Why a pair, not two loose fields on {@code Appearance}.</b> The two are meaningless apart:
 * a percentage with no fluid has no colour to draw in, and a fluid with no percentage has no bar to
 * draw at all. Bundling them makes "this tile shows no fill" a single {@code null} on {@code
 * Appearance}, not two sentinels that each have to agree on meaning — the same argument {@code
 * PowerSpec} spells out for grouping its three power fields.
 *
 * <p>The colour comes from {@link FluidType#colorRgb()}; this record carries the whole {@link
 * FluidType} rather than a bare colour int so the domain never names a rendering-library colour and
 * the renderer, not the domain, decides how to turn {@code 0xRRGGBB} into pixels.
 */
public record FluidFill(FluidType fluid, int percent) {

    public FluidFill {
        if (percent < 0 || percent > 100) {
            throw new IllegalArgumentException("FluidFill percent must be 0..100, was " + percent);
        }
    }
}

package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;

/**
 * Data for {@link SimpleCrafter}: which item goes in, which comes out, how long a batch takes, how
 * many inputs fit in the buffer. Public API (apiJar) so a code mod can wire a machine without
 * copying ElectroCracker boilerplate.
 *
 * <p>Item ids are {@link ContentId}s resolved at building create/restore time against the world's
 * item registry — never captured {@link com.rustorio.domain.ItemType} instances from registration
 * (those would be wrong identities after a fresh load).
 */
public record SimpleCrafterSpec(ContentId input, ContentId output, int workTicks, int inputMax) {

    public SimpleCrafterSpec {
        if (workTicks < 1) {
            throw new IllegalArgumentException("workTicks must be >= 1: " + workTicks);
        }
        if (inputMax < 1) {
            throw new IllegalArgumentException("inputMax must be >= 1: " + inputMax);
        }
    }

    /** One-in one-out crafter with a buffer of 5. */
    public static SimpleCrafterSpec of(ContentId input, ContentId output, int workTicks) {
        return new SimpleCrafterSpec(input, output, workTicks, 5);
    }

    /** Paths resolved by the caller (DSL) into full ids before this record is built. */
    public static SimpleCrafterSpec of(String inputId, String outputId, int workTicks) {
        return of(ContentId.of(inputId), ContentId.of(outputId), workTicks);
    }
}

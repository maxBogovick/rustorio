package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.Objects;

/**
 * A fluid prototype: a continuous substance measured in a volume, not in whole units — water,
 * steam, and whatever a mod registers next to them.
 *
 * <p><b>Why a type of its own instead of a flag on {@link ItemType}.</b> A fluid is a volume, and
 * an item is a count; an item carrying an "actually, I'm a fluid" flag would drag an is-it-a-fluid
 * branch into every path an {@code ItemType} already travels — a belt, a chest, an inserter, a
 * recipe — which is exactly the switch-by-content-kind this codebase spends its ratchet test
 * forbidding. Separate types keep the two flows unmixable at compile time instead: a pipe carries
 * {@code FluidType}, a belt carries {@code ItemType}, and no signature accepts both.
 *
 * <p>{@code colorRgb} is a packed {@code 0xRRGGBB} int for the same reason {@link ItemType}'s is —
 * the domain can't name a rendering-library color type. Temperature, pressure and flow rate are
 * deliberately absent: a fluid is a name and a color until something in the game actually reads
 * more than that.
 *
 * <p><b>Identity is {@link #id} alone</b>, exactly like {@link ItemType} — see that record's own
 * javadoc for why every-field equality would be wrong here: a mod re-skinning another mod's fluid
 * must not turn a {@code HashMap} key and a {@code TreeMap} key into two different fluids.
 */
public record FluidType(ContentId id, String label, int colorRgb) implements Comparable<FluidType> {

    public FluidType {
        if (label.isEmpty()) {
            throw new IllegalArgumentException("FluidType label must not be empty (id: " + id + ")");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FluidType that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public int compareTo(FluidType other) {
        return id.compareTo(other.id);
    }

    /** The display name, not the record's every-field dump — same reasoning as {@link ItemType#toString()}. */
    @Override
    public String toString() {
        return label;
    }
}

package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.Objects;

/**
 * An item prototype: everything content-facing about an item, addressed by {@link ContentId}
 * instead of being a fixed enum constant.
 *
 * <p>{@code colorRgb} is a packed {@code 0xRRGGBB} int, not a graphics-library color type — the
 * domain doesn't depend on the rendering layer, so it can't hold one directly; the renderer
 * converts this int into whatever color type it needs.
 *
 * <p>{@code label} must be non-empty — {@code ItemRenderer} takes its first character for the
 * on-belt letter badge, and an enum constant could never have been blank, so nothing downstream
 * ever had to guard against it.
 *
 * <p><b>Identity is {@link #id} alone</b> — {@link #equals}, {@link #hashCode} and {@link
 * #compareTo} all delegate to it, deliberately NOT the record's default (every-field) equality.
 * The same {@link ContentId} always means the same item, even if its label/color/shape changed
 * underneath via {@code Registry.update()} (a mod re-skinning another mod's item): a {@code
 * HashMap<ItemType, …>} entry and a {@code TreeMap<ItemType, …>} entry keyed by two instances that
 * share an id must agree on being the same key, or one of the two map kinds silently drops a
 * count — an instance from before the update and one fetched after it are still one and the same
 * item, not two.
 *
 * <p>{@link #toString} returns {@link #label}, not the record's default every-field dump — display
 * code (HUD, logs) wants "Iron Plate", not {@code ItemType[id=..., label=..., researchGrade=...]}.
 * Prefer calling {@link #label} explicitly at a display call site anyway (it says what you mean);
 * this override exists so an accidental {@code "" + item} still reads right instead of leaking the
 * record's internals (code review finding S3).
 */
public record ItemType(ContentId id, String label, boolean researchGrade, int colorRgb, ItemShape shape)
        implements Comparable<ItemType> {

    public ItemType {
        if (label.isEmpty()) {
            throw new IllegalArgumentException("ItemType label must not be empty (id: " + id + ")");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ItemType that && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public int compareTo(ItemType other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return label;
    }
}

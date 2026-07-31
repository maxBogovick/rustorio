package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import org.jspecify.annotations.Nullable;

/**
 * How a building looks right now: which sprite (a {@link ContentId}), an optional numeric badge (how much is
 * queued in a chest, how hot a furnace's buffer is), its {@link BuildingStatus} (F-01,
 * DEV_TASKS.md), and an optional recipe hint (F-03, DEV_TASKS.md). Lives in the domain, not the
 * renderer: the building decides its own appearance; drawing it is dumb, uniform work for every
 * building.
 *
 * <p>{@code badge} is a plain {@code int} with a sentinel, not {@code OptionalInt} — and {@code
 * recipeHint} is a plain {@code @Nullable ItemType}, not {@code Optional<ItemType>}: Effective Java
 * Item 55 says {@code Optional} belongs on method return types, never a field or record component —
 * see {@code Recipe#input2} for the same reasoning spelled out in full.
 *
 * <p>{@code recipeHint} is which item a furnace or press is currently set to produce — the
 * committed batch's recipe if one is running, else the player's standing preference ({@code
 * Furnace#cycleRecipe}), else {@code null}. Every other building leaves it {@code null}: nothing
 * else in the game has a recipe to pick. {@code com.graphics.render.BuildingRenderer} draws it as
 * a small colored icon directly on the sprite, so the choice is visible without opening the
 * inspection panel — the card's own "не только в панели" requirement.
 *
 * <p>The two/three-argument {@link #of(ContentId)}/{@link #of(ContentId, int)}/{@link #of(ContentId,
 * BuildingStatus)} overloads default {@code status} to {@link BuildingStatus#WORKING} and {@code
 * recipeHint} to {@code null} — most buildings ({@code Belt}, {@code Splitter}, {@code
 * UndergroundBelt}, {@code Lab}) have no meaningful "stuck" state or recipe choice of their own to
 * report and keep calling these unchanged; only {@code Miner}, {@code Chest} needed a real status,
 * and only {@code Furnace} needs both status and a recipe hint.
 */
public record Appearance(ContentId sprite, int badge, BuildingStatus status, @Nullable ItemType recipeHint) {

    private static final int NO_BADGE = -1;

    public static Appearance of(ContentId sprite) {
        return new Appearance(sprite, NO_BADGE, BuildingStatus.WORKING, null);
    }

    public static Appearance of(ContentId sprite, int badge) {
        return new Appearance(sprite, badge, BuildingStatus.WORKING, null);
    }

    public static Appearance of(ContentId sprite, BuildingStatus status) {
        return new Appearance(sprite, NO_BADGE, status, null);
    }

    public static Appearance of(ContentId sprite, BuildingStatus status, @Nullable ItemType recipeHint) {
        return new Appearance(sprite, NO_BADGE, status, recipeHint);
    }

    public static Appearance of(ContentId sprite, int badge, BuildingStatus status) {
        return new Appearance(sprite, badge, status, null);
    }

    public static Appearance of(ContentId sprite, int badge, BuildingStatus status, @Nullable ItemType recipeHint) {
        return new Appearance(sprite, badge, status, recipeHint);
    }

    public boolean hasBadge() {
        return badge != NO_BADGE;
    }
}

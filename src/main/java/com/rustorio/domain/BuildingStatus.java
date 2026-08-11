package com.rustorio.domain;

import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * What's actually happening inside a building right now — the fix for §3.3/§6.5 of the design
 * audit (F-01, DEV_TASKS.md): {@code Miner.tick} idling on an ore-less tile used to be a silent
 * {@code return;} with no visible difference from a miner whose OUTPUT was simply blocked — on
 * screen, a stalled machine and a healthy one looked identical. A building computes and caches its
 * own status once per {@link com.rustorio.domain.building.Building#tick}, not once per render
 * frame (see that method's own javadoc for why) — {@link Appearance} just carries whatever the
 * building last decided.
 *
 * <p><b>Each constant carries how it is shown</b> — an alert caption and a marker colour — rather
 * than leaving the renderer to switch over the whole set. Two exhaustive {@code switch}es used to
 * do that, one for the colour and one for the caption, so adding a status meant compile errors in
 * two files in another package, and nothing but a reviewer's memory to catch a third place that
 * also needed it. This is the same move already made for {@link
 * com.rustorio.api.content.model.ItemType} and {@link
 * com.rustorio.api.content.model.FluidType},
 * whose colours live on the content rather than in a palette's case list: a presentation DETAIL
 * belongs to the thing it describes, while presentation POLICY — what a marker is shaped like,
 * where on the tile it goes — stays in {@code com.graphics}.
 */
public enum BuildingStatus {
    /** Nothing wrong — producing, or on its way to producing, at its normal pace. */
    WORKING(null, 0),
    /** Waiting on an ingredient that hasn't arrived (a furnace with no ore buffered at all, say). */
    NO_INPUT("no input", 0xE8C45A),
    /** A miner sitting on a cell with no ore to extract — the exact silent failure §3.3 names. */
    NO_ORE("no ore", 0xD64040),
    /** A {@code FURNACE} with ingredients buffered but no coal to burn (D-05). */
    NO_FUEL("no fuel", 0xC9502F),
    /** Finished a batch (or a chest reached capacity) but the neighbor it needs to hand off to won't take it. */
    OUTPUT_FULL("output full", 0x9E64C7),
    /**
     * A machine that declared a power demand and did not get it — either no pole covers it, or its
     * network is producing less than the machines on it are asking for. Only a building that
     * declares a demand can ever report this (owner decision: electricity is opt-in), so a factory
     * built before there was any electricity never shows it.
     */
    NO_POWER("no power", 0x60B0E8);

    /**
     * Every constant in declaration order, without the per-call array clone {@link #values()}
     * allocates. HUD alert summation walks this every frame — prefer this over {@code values()} on
     * any hot path.
     */
    public static final List<BuildingStatus> ALL = List.of(values());

    private final @Nullable String alertLabel;
    private final int colorRgb;

    BuildingStatus(@Nullable String alertLabel, int colorRgb) {
        this.alertLabel = alertLabel;
        this.colorRgb = colorRgb;
    }

    /**
     * The caption an alert row shows for this status, or empty for {@link #WORKING} — which is not
     * a problem and so never earns a row. Empty rather than an exception the caller has to know to
     * avoid: "nothing to report" is an ordinary answer here, not a misuse.
     *
     * <p>ASCII only, like every other on-screen caption: the bitmap font has no Cyrillic glyphs.
     */
    public Optional<String> alertLabel() {
        return Optional.ofNullable(alertLabel);
    }

    /**
     * The marker colour for this status as packed {@code 0xRRGGBB}, meaningless for {@link
     * #WORKING} — guard with {@link #isAlert}. Which hue and why: red for ore run out, rust for
     * needs coal, light amber for waiting on material, violet for nowhere to hand off, cold blue for
     * unpowered. Picked apart from one another so two different problems are never the same colour
     * on screen; that constraint is the reason to keep them in one list rather than scattered.
     */
    public int colorRgb() {
        return colorRgb;
    }

    /** Whether this is a problem worth marking at all — everything except {@link #WORKING}. */
    public boolean isAlert() {
        return alertLabel != null;
    }
}

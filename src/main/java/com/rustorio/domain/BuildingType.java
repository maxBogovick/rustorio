package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.Locale;

/**
 * The building kind a player can pick in the hotbar. Declaration order is hotbar order and
 * keyboard-shortcut order (1, 2, 3…).
 */
public enum BuildingType {
    MINER("Miner"),
    CHEST("Chest"),
    FURNACE("Furnace"),
    BELT("Belt"),
    SPLITTER("Splitter"),
    PRESS("Press"),
    UNDERGROUND_IN("Tunnel in"),
    UNDERGROUND_OUT("Tunnel out"),
    LAB("Lab"),
    // Appended, not inserted (X-01, DEV_TASKS.md): the first 9 keep keys 1-9 (InputHandler's
    // `type.ordinal() < 9` limit, P4-08) — these two are mouse-only from the hotbar, same
    // already-accepted tradeoff a 10th building always was.
    FILTER("Filter"),
    INSERTER("Inserter"),
    // Same mouse-only tradeoff as FILTER/INSERTER above — occupies 2x2 cells (X-03, DEV_TASKS.md,
    // see Building#footprintWidth), the first building kind for which that's true.
    ASSEMBLER("Assembler"),
    // Appended for the same reason and with the same tradeoff as everything above the first nine:
    // plumbing is mouse-only from the hotbar. Both are the Pipe archetype and differ only in how
    // much they hold — see that class and BuildingPrototype#bufferMax.
    PIPE("Pipe"),
    TANK("Tank"),
    // The two ends of the fluid chain: one lifts water off the map into plumbing, the other burns
    // fuel to turn it into steam. Appended for the same hotbar reason as everything above.
    PUMP("Pump"),
    BOILER("Boiler"),
    // Electricity, appended for the same hotbar reason. ELECTRIC_MINER reuses the Miner archetype
    // outright: it differs from the plain one only in declaring a power demand, which is data.
    POLE("Pole"),
    GENERATOR("Generator"),
    ELECTRIC_MINER("El. miner");

    private final String label;

    BuildingType(String label) {
        this.label = label;
    }

    /** Hotbar caption (ASCII only — the bitmap font has no Cyrillic glyphs). */
    public String label() {
        return label;
    }

    /**
     * The footprint {@link com.rustorio.domain.building.BuildingFactory#create} would build for
     * this kind, without actually constructing one — the single source of truth {@code Building}
     * instances themselves defer to (see {@code Furnace#footprintWidth}). Lets code that only has
     * a {@code BuildingType} (no {@code Building} instance yet, e.g. a placement ghost) ask the
     * same question {@code World.place} answers, without paying for a throwaway allocation on
     * every frame.
     */
    public int footprintWidth() {
        return this == ASSEMBLER ? 2 : 1;
    }

    /** The height counterpart to {@link #footprintWidth} — see its javadoc. */
    public int footprintHeight() {
        return this == ASSEMBLER ? 2 : 1;
    }

    /**
     * This constant's own name, lowercased, under the {@code rustorio} namespace — the same
     * formula {@code com.rustorio.domain.building.VanillaBuildings#idFor} exposes to callers that
     * already depend on that package, duplicated here (not called FROM there) because {@code
     * com.rustorio.domain} is architecturally forbidden from depending on {@code
     * com.rustorio.domain.building} at all ({@code PackageBoundaryRulesTest}) — {@code
     * VanillaBuildings.idFor} delegates to THIS method instead, so the formula still has exactly
     * one real implementation. Lets {@link Recipe}/{@code RecipeBook} (both in this package)
     * convert a {@code BuildingType} to the open {@link ContentId} a recipe's governing "kind" is
     * now keyed by, without a forbidden import.
     */
    public ContentId contentId() {
        return new ContentId("rustorio", name().toLowerCase(Locale.ROOT));
    }
}

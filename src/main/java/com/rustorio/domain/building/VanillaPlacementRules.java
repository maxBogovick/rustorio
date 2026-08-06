package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;

/**
 * The placement rules the base game ships, as registered content addressed by {@link ContentId}.
 *
 * <p>They used to be a fixed {@code Map} inside the JSON building loader, which made "where may
 * this stand" the one property of a building a mod could not extend: cost, texture, footprint and
 * behaviour were all open, but a genuinely new CONDITION — next to lava, on a cliff, only
 * underground — had no way in short of editing the engine. A rule is a function of a cell, which is
 * code, so a data mod still cannot invent one; a CODE mod now can, and every data mod can then name
 * it from JSON exactly like a vanilla one.
 *
 * <p>Ids are the constant's own name, lowercased, under {@code rustorio} — the same formula {@code
 * VanillaBuildings#idFor} uses, so {@code "NEEDS_ORE"} in a JSON file and {@code
 * rustorio:needs_ore} in a registry are visibly the same thing.
 */
public final class VanillaPlacementRules {

    public static final ContentId ALWAYS = ContentId.of("rustorio:always");
    public static final ContentId NEEDS_ORE = ContentId.of("rustorio:needs_ore");
    public static final ContentId NEEDS_PASSABLE_TERRAIN = ContentId.of("rustorio:needs_passable_terrain");
    public static final ContentId ADJACENT_TO_WATER = ContentId.of("rustorio:adjacent_to_water");

    private VanillaPlacementRules() {
    }

    /**
     * Registers all four into {@code rules}. Called before any mod's own round, so a mod sees them
     * already there and can name, replace or extend them like any other registered content.
     */
    public static void registerAll(Registry<PlacementRule> rules) {
        rules.register(ALWAYS, PlacementRule.ALWAYS);
        rules.register(NEEDS_ORE, PlacementRule.NEEDS_ORE);
        rules.register(NEEDS_PASSABLE_TERRAIN, PlacementRule.NEEDS_PASSABLE_TERRAIN);
        rules.register(ADJACENT_TO_WATER, PlacementRule.ADJACENT_TO_WATER);
    }
}

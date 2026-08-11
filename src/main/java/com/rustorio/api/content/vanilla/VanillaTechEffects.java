package com.rustorio.api.content.vanilla;

import com.rustorio.api.content.ContentId;

/**
 * Ids of the marker tech effects the base game ships. The {@link com.rustorio.api.mod.TechEffect}
 * objects themselves are registered by the loader ({@code GameRegistrationContext}), not here —
 * keeping this class free of {@code api.mod} imports so the catalog package stays a thin
 * ContentId holder, not a registration kitchen.
 *
 * <p><b>Which effects actually change gameplay today</b> (via
 * {@link com.rustorio.domain.ResearchView#hasEffect}):
 * {@link #BIG_BUFFER} (chest + furnace input buffer) and {@link #LONG_TUNNEL} (underground belt
 * range). {@link #FAST_MINING}, {@link #FAST_SMELTING} and {@link #FAST_LAB} are registered and
 * listed on the matching techs so data mods can name them, but speed still goes through
 * {@code BuildingPrototype.speedTech()} + {@code fasterIfUnlocked} — granting only the
 * {@code *_effect} id from a foreign tech does <em>not</em> speed those machines yet.
 *
 * <p>Ids mirror the tech that historically granted the bonus, under an {@code _effect} path — not
 * the tech id itself — so a typo that lists a tech as an effect fails validation instead of
 * silently treating "the tech exists" as "the effect exists".
 */
public final class VanillaTechEffects {

    public static final ContentId FAST_MINING = ContentId.of("rustorio:fast_mining_effect");
    public static final ContentId FAST_SMELTING = ContentId.of("rustorio:fast_smelting_effect");
    public static final ContentId BIG_BUFFER = ContentId.of("rustorio:big_buffer_effect");
    public static final ContentId LONG_TUNNEL = ContentId.of("rustorio:long_tunnel_effect");
    public static final ContentId FAST_LAB = ContentId.of("rustorio:fast_lab_effect");

    private VanillaTechEffects() {
    }
}

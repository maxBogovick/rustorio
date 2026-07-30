package com.rustorio.domain;

import com.rustorio.api.content.ContentId;

/**
 * The game's own built-in sprite names — replaces the {@code Sprite} enum with plain {@link
 * ContentId} constants. Unlike {@link VanillaItems}, these aren't backed by a {@link
 * com.rustorio.api.registry.Registry}: {@link Appearance} is never persisted (it's recomputed
 * fresh from a building's live state every tick), so there's no save-compatibility reason to need
 * a deterministic numeric {@code rawId}, and nothing else needs to look a sprite name up by
 * anything other than the constant itself — a registry's freeze/rawId machinery would be
 * unused ceremony here.
 */
public final class VanillaSprites {

    public static final ContentId MINER = ContentId.of("rustorio:miner");
    public static final ContentId CHEST = ContentId.of("rustorio:chest");
    public static final ContentId FURNACE_HOT = ContentId.of("rustorio:furnace_hot");
    public static final ContentId FURNACE_COLD = ContentId.of("rustorio:furnace_cold");
    public static final ContentId BELT_EMPTY = ContentId.of("rustorio:belt_empty");
    public static final ContentId BELT_FULL = ContentId.of("rustorio:belt_full");
    public static final ContentId SPLITTER = ContentId.of("rustorio:splitter");
    public static final ContentId FILTER = ContentId.of("rustorio:filter");
    public static final ContentId INSERTER = ContentId.of("rustorio:inserter");
    public static final ContentId UNDERGROUND_IN = ContentId.of("rustorio:underground_in");
    public static final ContentId UNDERGROUND_OUT = ContentId.of("rustorio:underground_out");
    public static final ContentId LAB = ContentId.of("rustorio:lab");
    public static final ContentId ASSEMBLER = ContentId.of("rustorio:assembler");

    private VanillaSprites() {
    }
}

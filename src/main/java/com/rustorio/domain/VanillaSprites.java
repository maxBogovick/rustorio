package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import java.util.List;

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
    public static final ContentId PIPE = ContentId.of("rustorio:pipe");
    public static final ContentId TANK = ContentId.of("rustorio:tank");
    public static final ContentId PUMP = ContentId.of("rustorio:pump");
    public static final ContentId BOILER = ContentId.of("rustorio:boiler");
    public static final ContentId POLE = ContentId.of("rustorio:pole");
    public static final ContentId GENERATOR = ContentId.of("rustorio:generator");
    public static final ContentId ELECTRIC_MINER = ContentId.of("rustorio:electric_miner");

    /**
     * Every constant above, in declaration order — what the renderer's texture index builds itself
     * from, so a new vanilla sprite is one line HERE and nowhere else. It used to have to be added
     * twice, once as a constant and once as a file path in {@code TextureIndex}, and the two lists
     * had nothing but a reviewer's attention holding them together.
     *
     * <p>A {@code List}, not a {@code Set.of}: this is iterated to build that index, and a set's
     * per-run iteration order is exactly the kind of thing this project keeps a rule about.
     */
    public static List<ContentId> all() {
        return List.of(MINER, CHEST, FURNACE_HOT, FURNACE_COLD, BELT_EMPTY, BELT_FULL, SPLITTER, FILTER,
                INSERTER, UNDERGROUND_IN, UNDERGROUND_OUT, LAB, ASSEMBLER, PIPE, TANK, PUMP, BOILER, POLE,
                GENERATOR, ELECTRIC_MINER);
    }

    private VanillaSprites() {
    }
}

package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import java.util.List;

/**
 * The game's own built-in technologies, mirroring {@code resources/mods/rustorio/content/techs}
 * exactly — the same relationship {@link VanillaItems} has to {@code content/items}, and held to it
 * by {@code VanillaAsModParityTest}.
 *
 * <p>The ids are constants because the game's own code used to read them by name outright. Speed
 * bonuses now go through {@code BuildingPrototype.speedTech()} (a trait). Buffer / tunnel range
 * bonuses go through {@link VanillaTechEffects} + {@link ResearchView#hasEffect}: unlocking the
 * matching tech lists that effect, so a JSON-only mod can grant the same named bonus from its own
 * technology without teaching {@code Chest} / {@code UndergroundBelt} a new constant. See
 * {@link TechType}.
 *
 * <p>Shape below is the vanilla balance: {@link #FAST_MINING} is the one root, {@link
 * #FAST_SMELTING} and {@link #BIG_BUFFER} branch independently off it, {@link #LONG_TUNNEL} extends
 * the buffer branch, and {@link #FAST_LAB} needs both branches — a real tree, not a straight line.
 */
public final class VanillaTechs {

    public static final ContentId FAST_MINING = ContentId.of("rustorio:fast_mining");
    public static final ContentId FAST_SMELTING = ContentId.of("rustorio:fast_smelting");
    public static final ContentId BIG_BUFFER = ContentId.of("rustorio:big_buffer");
    public static final ContentId LONG_TUNNEL = ContentId.of("rustorio:long_tunnel");
    public static final ContentId FAST_LAB = ContentId.of("rustorio:fast_lab");

    // Declared after the ids above and before frozen() is ever called, for the same top-to-bottom
    // static-initializer reason VanillaItems documents on its own FROZEN field.
    private static final Registry<TechType> FROZEN = buildFrozen();

    private VanillaTechs() {
    }

    /** The shared, already-frozen registry of built-in technologies — what a game running no mods researches through. */
    public static Registry<TechType> frozen() {
        return FROZEN;
    }

    /** Registers the built-in technologies into {@code techs}, so the mod loader can seed a fresh registry with them. */
    public static void registerAll(Registry<TechType> techs) {
        techs.register(FAST_MINING, new TechType(FAST_MINING, "Fast mining", 80, List.of(),
                List.of(VanillaTechEffects.FAST_MINING)));
        techs.register(FAST_SMELTING, new TechType(FAST_SMELTING, "Fast smelting", 200, List.of(FAST_MINING),
                List.of(VanillaTechEffects.FAST_SMELTING)));
        techs.register(BIG_BUFFER, new TechType(BIG_BUFFER, "Big buffers", 350, List.of(FAST_MINING),
                List.of(VanillaTechEffects.BIG_BUFFER)));
        techs.register(LONG_TUNNEL, new TechType(LONG_TUNNEL, "Long tunnels", 550, List.of(BIG_BUFFER),
                List.of(VanillaTechEffects.LONG_TUNNEL)));
        techs.register(FAST_LAB, new TechType(FAST_LAB, "Fast research", 800, List.of(FAST_SMELTING, BIG_BUFFER),
                List.of(VanillaTechEffects.FAST_LAB)));
    }

    private static Registry<TechType> buildFrozen() {
        Registry<TechType> techs = new Registry<>();
        registerAll(techs);
        techs.freeze();
        return techs;
    }
}

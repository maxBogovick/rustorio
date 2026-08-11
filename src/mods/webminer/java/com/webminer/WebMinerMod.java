package com.webminer;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.building.BeltState;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.Traits;
import com.rustorio.domain.building.VanillaPlacementRules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * This mod's entry point: the class {@code ServiceLoader} finds inside {@code webminer.jar},
 * declared by {@code mod.json}'s own {@code entryPoint} and named nowhere in the engine.
 *
 * <p>{@link #registerAll} stays public and static alongside {@link #registerContent} for this mod's
 * own unit tests, which build a registry directly instead of going through a full mod load. It
 * reads {@code webminer:web_ok}/{@code webminer:web_error} out of the registry it is handed, so
 * this mod's {@code content/items/*.json} must have loaded first — which the loader guarantees:
 * a mod's JSON content is read before its jar runs.
 */
public final class WebMinerMod implements RustorioMod {

    public static final ContentId WEB_MINER_ID = ContentId.of("webminer:web_miner");
    public static final ContentId MONITOR_ID = ContentId.of("webminer:monitor");
    public static final ContentId INTERPRETER_ID = ContentId.of("webminer:interpreter");

    /**
     * This mod's own sprites — one per PNG in {@code resources/mods/webminer/textures/}, which the
     * renderer indexes under the mod folder's own name, so {@code monitor.png} is the sprite {@code
     * webminer:monitor}. No engine change was needed for that: the same mechanism already carries
     * any third-party mod's art.
     *
     * <p>Separate constants from the building ids above, even though the two spell the same thing
     * today: one names a file on disk, the other names what goes into the player's save, and
     * renaming either must not silently move the other. All three archetypes used to point at
     * vanilla sprites instead ({@code rustorio:miner}, {@code rustorio:belt_empty} twice), which
     * drew a web miner as a drill and made the monitor and the interpreter indistinguishable from
     * each other and from an ordinary belt.
     */
    private static final ContentId WEB_MINER_SPRITE = ContentId.of("webminer:web_miner");
    private static final ContentId MONITOR_SPRITE = ContentId.of("webminer:monitor");
    private static final ContentId INTERPRETER_SPRITE = ContentId.of("webminer:interpreter");

    /**
     * Discovered by {@code ServiceLoader} out of this mod's own jar — the mod loader instantiates
     * this class reflectively, so the constructor has to be public and take no arguments. It was a
     * private constructor on a static-only utility class for as long as the game's own startup code
     * had to call {@link #registerAll} by name; nothing calls it by name any more.
     */
    public WebMinerMod() {
    }

    /**
     * Everything this mod adds to a game, in one place: three archetypes and the capability they
     * run on. Both halves arrive through the ordinary {@link RegistrationContext} — the engine
     * neither names this class nor knows what a fetch is.
     *
     * <p>The service is registered here rather than being constructed by the game's startup code,
     * which is what it used to require: {@code GameScreen} built the HTTP executor itself, by its
     * concrete type, so this mod could not have worked as a drop-in folder at all.
     */
    @Override
    public void registerContent(RegistrationContext context) {
        // Peek the live registry value so a balance mod's update() replaces what we bind — binding
        // PlacementRule.NEEDS_PASSABLE_TERRAIN directly would ignore that replacement.
        PlacementRule passable = context.placementRules()
                .peek(VanillaPlacementRules.NEEDS_PASSABLE_TERRAIN)
                .orElseThrow();
        registerAll(context.buildings(), context.items(), passable);
        context.registerService(FetchService.KEY, () -> new FetchService(new HttpFetchExecutor()));
    }

    private static final Codec<InterpreterState> INTERPRETER_CODEC = new Codec<>() {
        @Override
        public Object encode(InterpreterState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("held", Codec.encodeItem(state.held()));
            data.put("fields", List.copyOf(state.fields()));
            return data;
        }

        @Override
        public InterpreterState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            List<?> rawFields = Codec.requireField(data, "fields");
            List<String> fields = new ArrayList<>();
            for (Object field : rawFields) {
                fields.add((String) field);
            }
            return new InterpreterState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.decodeItem(data.get("held"), items),
                    List.copyOf(fields));
        }
    };

    private static final Codec<WebMinerState> WEB_MINER_CODEC = new Codec<>() {
        @Override
        public Object encode(WebMinerState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("url", state.url());
            data.put("held", Codec.encodeItem(state.held()));
            data.put("intervalTicks", state.intervalTicks());
            data.put("backoffTicks", state.backoffTicks());
            data.put("cooldownRemaining", state.cooldownRemaining());
            data.put("consecutiveFailures", state.consecutiveFailures());
            data.put("backoffRemaining", state.backoffRemaining());
            return data;
        }

        @Override
        public WebMinerState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new WebMinerState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "url"),
                    Codec.decodeItem(data.get("held"), items),
                    Codec.requireField(data, "intervalTicks"),
                    Codec.requireField(data, "backoffTicks"),
                    Codec.requireField(data, "cooldownRemaining"),
                    Codec.requireField(data, "consecutiveFailures"),
                    Codec.requireField(data, "backoffRemaining"));
        }
    };

    public static void registerAll(Registry<BuildingPrototype> prototypes, Registry<ItemType> items) {
        registerAll(prototypes, items, PlacementRule.NEEDS_PASSABLE_TERRAIN);
    }

    public static void registerAll(Registry<BuildingPrototype> prototypes, Registry<ItemType> items,
            PlacementRule passableTerrain) {
        ItemType successItem = requireItem(items, "webminer:web_ok");
        ItemType errorItem = requireItem(items, "webminer:web_error");
        Map<com.rustorio.domain.building.TraitKey<?>, Object> traits = new LinkedHashMap<>();
        traits.put(WebMiner.SUCCESS_ITEM, successItem);
        traits.put(WebMiner.ERROR_ITEM, errorItem);

        prototypes.register(WEB_MINER_ID, new BuildingPrototype(
                WEB_MINER_ID,
                "Web Miner",
                new BuildingCost(VanillaItems.IRON_PLATE, 5),
                passableTerrain,
                WEB_MINER_SPRITE,
                1, 1,
                0, 1, false,
                // A real, stable, no-auth JSON endpoint — so a freshly placed miner works immediately
                // instead of demonstrating nothing until the player learns to set a URL at all.
                // SetWebMinerUrlAction is the player's way to point it elsewhere afterward.
                (self, direction, factory) -> new WebMiner(direction, "https://api.github.com", self),
                (self, decodedState, factory) -> {
                    WebMinerState state = (WebMinerState) decodedState;
                    return new WebMiner(state.direction(), state.url(), state.held(), state.intervalTicks(),
                            state.backoffTicks(), state.cooldownRemaining(), state.consecutiveFailures(),
                            state.backoffRemaining(), self);
                },
                WEB_MINER_CODEC,
                WEB_MINER_ID, null, Traits.of(traits)));

        // Both borrow BELT's own codec outright (their state IS a BeltState — direction + held,
        // nothing else): a pass-through has no player-configurable data of its own beyond what
        // Belt already persists, only Interpreter adds one more field on top of that shape.
        Codec<BeltState> beltCodec = beltCodec(prototypes);
        prototypes.register(MONITOR_ID, new BuildingPrototype(
                MONITOR_ID,
                "Monitor",
                new BuildingCost(VanillaItems.IRON_PLATE, 3),
                passableTerrain,
                MONITOR_SPRITE,
                0, 1, false,
                (self, direction, factory) -> new Monitor(direction, self),
                (self, decodedState, factory) -> {
                    BeltState state = (BeltState) decodedState;
                    return new Monitor(state.direction(), state.held(), self);
                },
                beltCodec));

        prototypes.register(INTERPRETER_ID, new BuildingPrototype(
                INTERPRETER_ID,
                "Interpreter",
                new BuildingCost(VanillaItems.IRON_PLATE, 3),
                passableTerrain,
                INTERPRETER_SPRITE,
                0, 1, false,
                (self, direction, factory) -> new Interpreter(direction, self),
                (self, decodedState, factory) -> {
                    InterpreterState state = (InterpreterState) decodedState;
                    return new Interpreter(state.direction(), state.held(), state.fields(), self);
                },
                INTERPRETER_CODEC));
    }

    /**
     * This mod's own item, resolved out of whatever registry it is being registered into.
     *
     * <p>{@link Registry#peek}, not {@code get} and not {@code getOrUnknown}: {@link
     * #registerContent} runs while registration is still OPEN, and both of those refuse to answer
     * before {@code freeze()} because they need the {@code rawId} index, which does not exist yet.
     * {@code peek} is the one read legal at any point. Getting this wrong does not fail loudly — the
     * mod is skipped and the only trace is a WARNING in the log, which is how it survived a green
     * build twice ("cannot get() before freeze()", then "cannot getOrUnknown() before freeze()").
     *
     * <p>The message names the mod, the item and the file a modder would have to fix, because that
     * is who reads it — they have neither the engine's stack trace nor its sources.
     */
    private static ItemType requireItem(Registry<ItemType> items, String id) {
        ContentId contentId = ContentId.of(id);
        return items.peek(contentId).orElseThrow(() -> new IllegalStateException(
                "mod 'webminer': item " + contentId + " is not registered — it is declared in "
                        + "content/items/" + contentId.path() + ".json, which must load before this mod's jar runs"));
    }

    /** {@link BuildingType#BELT}'s own registered {@link Codec} — see the class javadoc for why {@link Monitor} borrows it outright. */
    @SuppressWarnings("unchecked")
    private static Codec<BeltState> beltCodec(Registry<BuildingPrototype> prototypes) {
        ContentId beltId = BuildingType.BELT.contentId();
        BuildingPrototype belt = prototypes.peek(beltId).orElseThrow(() -> new IllegalStateException(
                "mod 'webminer': vanilla belt " + beltId + " is not registered yet — depend on rustorio"));
        return (Codec<BeltState>) belt.codec();
    }

}

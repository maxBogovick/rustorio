package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The game's own built-in building prototypes — one per {@link BuildingType} constant, matching
 * today's {@code BuildingCost.forType}/{@code PlacementRule.forType}/{@code
 * Textures.forBuildingType} number-for-number. {@link #frozen()} is the shared, already-frozen
 * {@link Registry} that code without an injected one falls back to — same role {@link
 * VanillaItems#frozen()} plays for items.
 *
 * <p>Also the one place that pairs each prototype with the EXISTING Java class that implements it
 * ({@link BehaviorFactory}/{@link RestoreFactory}, registered alongside cost/placement/texture) —
 * {@code BuildingFactory} itself contains no {@code new Miner(...)}/{@code new Chest(...)} calls
 * at all anymore; every one of them lives here, next to the prototype it belongs to. Same for
 * {@link Codec} — each archetype's own knowledge of how to turn its state into a plain JSON-shaped
 * value lives here too, not in the persistence layer.
 */
public final class VanillaBuildings {

    /**
     * Shared across UNDERGROUND_IN/UNDERGROUND_OUT: {@link UndergroundBeltState} already carries
     * its own {@code kind}, so restoring either half needs no per-registration lambda. Declared
     * BEFORE {@link #FROZEN} — {@link #buildFrozen()} calls {@link #registerAll} during {@link
     * #FROZEN}'s own initializer, which captures this field into both underground-belt
     * prototypes; a field referenced before its own top-to-bottom initialization point is still
     * {@code null}, the same trap {@link #FROZEN}'s own comment already warns about, just in the
     * other direction (caught here by a failing test, not guessed at in advance). {@link Furnace}
     * has no equivalent shared constant — see {@link #registerFurnaceLike}'s own javadoc for why.
     */
    private static final RestoreFactory UNDERGROUND_BELT_RESTORE = (self, decodedState, factory) -> {
        UndergroundBeltState state = (UndergroundBeltState) decodedState;
        return new UndergroundBelt(state.kind(), state.direction(), state.held());
    };

    private static final Codec<MinerState> MINER_CODEC = new Codec<>() {
        @Override
        public Object encode(MinerState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("cooldown", state.cooldown());
            data.put("held", Codec.encodeItem(state.held()));
            data.put("speedLevel", state.speedLevel());
            return data;
        }

        @Override
        public MinerState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new MinerState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "cooldown"),
                    Codec.decodeItem(data.get("held"), items),
                    Codec.requireField(data, "speedLevel"));
        }
    };

    private static final Codec<ChestState> CHEST_CODEC = new Codec<>() {
        @Override
        public Object encode(ChestState state) {
            Map<String, Object> contents = new LinkedHashMap<>();
            state.contents().forEach((item, count) -> contents.put(item.id().toString(), count));
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("contents", contents);
            data.put("speedLevel", state.speedLevel());
            return data;
        }

        @Override
        public ChestState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            Map<ItemType, Integer> contents = new LinkedHashMap<>();
            Map<?, ?> rawContents = Codec.requireField(data, "contents");
            rawContents.forEach(
                    (id, count) -> contents.put(items.get(ContentId.of((String) id)), (Integer) count));
            return new ChestState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    contents,
                    Codec.requireField(data, "speedLevel"));
        }
    };

    private static final Codec<FurnaceState> FURNACE_CODEC = new Codec<>() {
        @Override
        public Object encode(FurnaceState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("buffers", new ArrayList<>(state.buffers()));
            data.put("cooldown", state.cooldown());
            data.put("recipeOutput", Codec.encodeItem(state.recipeOutput()));
            data.put("pendingOutput", Codec.encodeItem(state.pendingOutput()));
            data.put("fuelBuffer", state.fuelBuffer());
            data.put("selectedRecipeOutput", Codec.encodeItem(state.selectedRecipeOutput()));
            data.put("speedLevel", state.speedLevel());
            return data;
        }

        @Override
        public FurnaceState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            List<?> rawBuffers = Codec.requireField(data, "buffers");
            List<Integer> buffers = rawBuffers.stream().map(v -> (Integer) v).toList();
            return new FurnaceState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    buffers,
                    Codec.requireField(data, "cooldown"),
                    Codec.decodeItem(data.get("recipeOutput"), items),
                    Codec.decodeItem(data.get("pendingOutput"), items),
                    Codec.requireField(data, "fuelBuffer"),
                    Codec.decodeItem(data.get("selectedRecipeOutput"), items),
                    Codec.requireField(data, "speedLevel"));
        }
    };

    private static final Codec<BeltState> BELT_CODEC = new Codec<>() {
        @Override
        public Object encode(BeltState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("held", Codec.encodeItem(state.held()));
            return data;
        }

        @Override
        public BeltState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new BeltState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.decodeItem(data.get("held"), items));
        }
    };

    private static final Codec<SplitterState> SPLITTER_CODEC = new Codec<>() {
        @Override
        public Object encode(SplitterState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("facing", state.facing().name());
            data.put("held", Codec.encodeItem(state.held()));
            data.put("nextIsForward", state.nextIsForward());
            return data;
        }

        @Override
        public SplitterState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new SplitterState(
                    Direction.valueOf(Codec.requireField(data, "facing")),
                    Codec.decodeItem(data.get("held"), items),
                    Codec.requireField(data, "nextIsForward"));
        }
    };

    private static final Codec<FilterState> FILTER_CODEC = new Codec<>() {
        @Override
        public Object encode(FilterState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("facing", state.facing().name());
            data.put("held", Codec.encodeItem(state.held()));
            data.put("filterItem", state.filterItem().id().toString());
            return data;
        }

        @Override
        public FilterState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            String filterItemId = Codec.requireField(data, "filterItem");
            ItemType filterItem = items.get(ContentId.of(filterItemId));
            return new FilterState(
                    Direction.valueOf(Codec.requireField(data, "facing")),
                    Codec.decodeItem(data.get("held"), items),
                    filterItem);
        }
    };

    private static final Codec<InserterState> INSERTER_CODEC = new Codec<>() {
        @Override
        public Object encode(InserterState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("held", Codec.encodeItem(state.held()));
            return data;
        }

        @Override
        public InserterState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new InserterState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.decodeItem(data.get("held"), items));
        }
    };

    private static final Codec<LabState> LAB_CODEC = new Codec<>() {
        @Override
        public Object encode(LabState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("buffer", state.buffer().stream().map(item -> item.id().toString()).toList());
            data.put("cooldown", state.cooldown());
            data.put("speedLevel", state.speedLevel());
            return data;
        }

        @Override
        public LabState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            List<?> rawBuffer = Codec.requireField(data, "buffer");
            List<ItemType> buffer = rawBuffer.stream()
                    .map(id -> items.get(ContentId.of((String) id)))
                    .toList();
            return new LabState(buffer, Codec.requireField(data, "cooldown"), Codec.requireField(data, "speedLevel"));
        }
    };

    private static final Codec<UndergroundBeltState> UNDERGROUND_BELT_CODEC = new Codec<>() {
        @Override
        public Object encode(UndergroundBeltState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("kind", state.kind().name());
            data.put("direction", state.direction().name());
            data.put("held", Codec.encodeItem(state.held()));
            return data;
        }

        @Override
        public UndergroundBeltState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new UndergroundBeltState(
                    UndergroundBelt.Kind.valueOf(Codec.requireField(data, "kind")),
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.decodeItem(data.get("held"), items));
        }
    };

    // FROZEN must be declared (and therefore initialized) after every shared RestoreFactory/Codec
    // constant above and before anything that calls frozen() — same top-to-bottom
    // initialization-order trap already caught once in VanillaItems.
    private static final Registry<BuildingPrototype> FROZEN = buildFrozen();

    private VanillaBuildings() {
    }

    /**
     * {@code BuildingType}'s constant name, lowercased, under the {@code rustorio} namespace — the
     * one place this conversion happens, so {@link #registerAll} and any later lookup (a future
     * card) always agree on the same id instead of each retyping the convention independently.
     */
    public static ContentId idFor(BuildingType type) {
        return new ContentId("rustorio", type.name().toLowerCase(Locale.ROOT));
    }

    /** The canonical, already-frozen registry backing every building's default data. */
    public static Registry<BuildingPrototype> frozen() {
        return FROZEN;
    }

    /**
     * Registers all 12 vanilla building prototypes into {@code prototypes}. For tests/custom
     * assemblies that want their own isolated (unfrozen) copy instead of sharing {@link #frozen()}.
     *
     * <p>{@code acceptsSpeedEffects} is {@code true} exactly for the six kinds {@code
     * UpgradeSpeedAction} already accepts today ({@code MINER}/{@code CHEST}/{@code FURNACE}/
     * {@code PRESS}/{@code ASSEMBLER}/{@code LAB}) — {@code CHEST} included, not an oversight:
     * {@link Chest#tick} genuinely pushes one item per tick, so doubling that call is a real speed
     * effect, unlike the six single-slot/segment-joining kinds refused below (each doubles a {@code
     * tick()} call that provably does nothing the second time — see {@code UpgradeSpeedAction}'s
     * own javadoc).
     */
    public static void registerAll(Registry<BuildingPrototype> prototypes) {
        register(prototypes, BuildingType.MINER, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_ORE, VanillaSprites.MINER, true,
                (self, direction, factory) -> new Miner(factory.oreLayout(), direction),
                (self, decodedState, factory) -> {
                    MinerState state = (MinerState) decodedState;
                    return new Miner(factory.oreLayout(), state.direction(), state.cooldown(), state.held(), state.speedLevel());
                },
                MINER_CODEC);
        register(prototypes, BuildingType.CHEST, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.CHEST, true,
                (self, direction, factory) -> new Chest(direction),
                (self, decodedState, factory) -> {
                    ChestState state = (ChestState) decodedState;
                    return new Chest(state.direction(), state.contents(), state.speedLevel());
                },
                CHEST_CODEC);
        registerFurnaceLike(prototypes, BuildingType.FURNACE, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                VanillaSprites.FURNACE_COLD, 5, 1);
        register(prototypes, BuildingType.BELT, new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.BELT_EMPTY, false,
                (self, direction, factory) -> new Belt(direction),
                (self, decodedState, factory) -> {
                    BeltState state = (BeltState) decodedState;
                    return new Belt(state.direction(), state.held());
                },
                BELT_CODEC);
        register(prototypes, BuildingType.SPLITTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.SPLITTER, false,
                (self, direction, factory) -> new Splitter(direction),
                (self, decodedState, factory) -> {
                    SplitterState state = (SplitterState) decodedState;
                    return new Splitter(state.facing(), state.held(), state.nextIsForward());
                },
                SPLITTER_CODEC);
        registerFurnaceLike(prototypes, BuildingType.PRESS, new BuildingCost(VanillaItems.IRON_PLATE, 8),
                VanillaSprites.FURNACE_COLD, 5, 1);
        register(prototypes, BuildingType.UNDERGROUND_IN, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_IN, false,
                (self, direction, factory) -> new UndergroundBelt(UndergroundBelt.Kind.IN, direction),
                UNDERGROUND_BELT_RESTORE,
                UNDERGROUND_BELT_CODEC);
        register(prototypes, BuildingType.UNDERGROUND_OUT, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_OUT, false,
                (self, direction, factory) -> new UndergroundBelt(UndergroundBelt.Kind.OUT, direction),
                UNDERGROUND_BELT_RESTORE,
                UNDERGROUND_BELT_CODEC);
        register(prototypes, BuildingType.LAB, new BuildingCost(VanillaItems.GEAR, 10),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.LAB, true,
                (self, direction, factory) -> new Lab(factory.recipeBook()),
                (self, decodedState, factory) -> {
                    LabState state = (LabState) decodedState;
                    return new Lab(factory.recipeBook(), state.buffer(), state.cooldown(), state.speedLevel());
                },
                LAB_CODEC);
        register(prototypes, BuildingType.FILTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.FILTER, false,
                // Default filterItem is IRON_ORE — the more common ore, and a reasonable starting
                // point for a newly-placed FILTER. NOT full parity with the old (deleted)
                // SortRule.ORE_FORWARD, which forwarded BOTH IRON_ORE and BRONZE_ORE: Filter passes
                // exactly ONE item identity by design (see Filter's own class javadoc — that's the
                // whole point of replacing a fixed multi-item rule with player-cyclable data), so no
                // single default can replicate a two-item rule. A default Filter on a bronze line
                // will route bronze ore to the side lane until the player cycles it (F) to
                // BRONZE_ORE.
                (self, direction, factory) -> new Filter(direction, VanillaItems.IRON_ORE, factory.items()),
                (self, decodedState, factory) -> {
                    FilterState state = (FilterState) decodedState;
                    return new Filter(state.facing(), state.filterItem(), state.held(), factory.items());
                },
                FILTER_CODEC);
        register(prototypes, BuildingType.INSERTER, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.INSERTER, false,
                (self, direction, factory) -> new Inserter(direction),
                (self, decodedState, factory) -> {
                    InserterState state = (InserterState) decodedState;
                    return new Inserter(state.direction(), state.held());
                },
                INSERTER_CODEC);
        registerFurnaceLike(prototypes, BuildingType.ASSEMBLER, new BuildingCost(VanillaItems.GEAR, 15),
                VanillaSprites.ASSEMBLER, 5, 1);
    }

    private static Registry<BuildingPrototype> buildFrozen() {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        registerAll(prototypes);
        prototypes.freeze();
        return prototypes;
    }

    /** Every non-{@link Furnace} archetype: {@code bufferMax}/{@code speedMultiplier} are meaningless to it, registered as {@code 0}/{@code 1}. */
    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, boolean acceptsSpeedEffects,
            BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec) {
        register(prototypes, type, cost, placementRule, texture, 0, 1, acceptsSpeedEffects, behavior,
                restoreBehavior, codec);
    }

    /**
     * A {@link Furnace}-kind archetype (also {@code PRESS}/{@code ASSEMBLER}, which reuse the same
     * class) — always {@link PlacementRule#NEEDS_PASSABLE_TERRAIN}, same as every non-tunnel/miner
     * building, and always accepts speed effects — every {@link Furnace}-kind building does today.
     * Both the create AND restore lambdas capture {@code type} — {@link FurnaceState} carries no
     * {@code kind} of its own at all (see its own javadoc for why: the save envelope's explicit
     * {@code prototypeId} already resolves to exactly one of these three registrations before this
     * lambda ever runs, so which one is running already IS the kind). {@link #FURNACE_CODEC},
     * unlike the restore behavior, genuinely IS shared across all three — the encoded shape is
     * identical regardless of kind.
     */
    private static void registerFurnaceLike(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, ContentId texture, int bufferMax, int speedMultiplier) {
        register(prototypes, type, cost, PlacementRule.NEEDS_PASSABLE_TERRAIN, texture, bufferMax, speedMultiplier, true,
                (self, direction, factory) -> new Furnace(type, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(type, state, factory.recipeBook(), self);
                },
                FURNACE_CODEC);
    }

    /** {@code label}/{@code footprintWidth}/{@code footprintHeight} come from {@code type} itself — the single source of truth for every vanilla prototype's data, not retyped here. */
    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec) {
        ContentId id = idFor(type);
        prototypes.register(id, new BuildingPrototype(id, type.label(), cost, placementRule, texture,
                type.footprintWidth(), type.footprintHeight(), bufferMax, speedMultiplier,
                acceptsSpeedEffects, behavior, restoreBehavior, codec));
    }
}

package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaFluids;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.VanillaTechs;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

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
        return new UndergroundBelt(state.kind(), state.direction(), state.held(), self);
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

    private static final Codec<PipeState> PIPE_CODEC = new Codec<>() {
        @Override
        public Object encode(PipeState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            ContentId fluid = state.fluid();
            data.put("fluid", fluid == null ? null : fluid.toString());
            data.put("amount", state.amount());
            return data;
        }

        @Override
        public PipeState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            Object fluid = data.get("fluid");
            // Read as a Number, not cast straight to Long: a JSON writer stores 5 as an int and a
            // reader hands it back as an Integer, so a save written with a small volume would fail
            // a Long cast while one written with a large volume passed.
            Number amount = Codec.requireField(data, "amount");
            return new PipeState(fluid == null ? null : ContentId.of((String) fluid), amount.longValue());
        }
    };

    private static final Codec<PumpState> PUMP_CODEC = new Codec<>() {
        @Override
        public Object encode(PumpState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("cooldown", state.cooldown());
            return data;
        }

        @Override
        public PumpState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new PumpState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "cooldown"));
        }
    };

    private static final Codec<BoilerState> BOILER_CODEC = new Codec<>() {
        @Override
        public Object encode(BoilerState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("fuelBuffer", state.fuelBuffer());
            data.put("burnTicksLeft", state.burnTicksLeft());
            return data;
        }

        @Override
        public BoilerState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new BoilerState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "fuelBuffer"),
                    Codec.requireField(data, "burnTicksLeft"));
        }
    };

    /** A pole keeps no state at all — see {@link PoleState}. The encoded shape is an empty object, not a missing field. */
    private static final Codec<PoleState> POLE_CODEC = new Codec<>() {
        @Override
        public Object encode(PoleState state) {
            return new LinkedHashMap<String, Object>();
        }

        @Override
        public PoleState decode(Object raw, Registry<ItemType> items) {
            return new PoleState();
        }
    };

    private static final Codec<GeneratorState> GENERATOR_CODEC = new Codec<>() {
        @Override
        public Object encode(GeneratorState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            return data;
        }

        @Override
        public GeneratorState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new GeneratorState(Direction.valueOf(Codec.requireField(data, "direction")));
        }
    };

    // FROZEN must be declared (and therefore initialized) after every shared RestoreFactory/Codec
    // constant above and before anything that calls frozen() — same top-to-bottom
    // initialization-order trap already caught once in VanillaItems.
    // DECLARED BEFORE {@code FROZEN} on purpose, and the ordering is load-bearing: static
    // initialisers run in textual order, FROZEN calls buildFrozen() -> register() -> withCategory(),
    // and a category map declared further down is still null at that moment. It was, and the whole
    // class failed to initialise with ExceptionInInitializerError — the same family of trap this
    // repository already records for EnumSet inside an enum's own constructor.
    /**
     * Every vanilla building's build-panel category, in ONE place rather than as an extra argument
     * on nineteen registration calls — the category is a fact about the kind, and the kind is
     * already the key here.
     *
     * <p>{@code BOILER} sits under fluids rather than power on purpose: it is a machine that turns
     * water into steam, and a player hunting for it is looking among pipes and tanks. It feeds the
     * power chain, but so does coal.
     *
     * <p>{@link EnumMap} built empty and filled, never {@code new EnumMap<>(someOtherMap)} — this
     * repository has a trap recorded for exactly that constructor.
     */
    private static final Map<BuildingType, ContentId> CATEGORY_BY_TYPE = categoryByType();

    private static Map<BuildingType, ContentId> categoryByType() {
        Map<BuildingType, ContentId> byType = new EnumMap<>(BuildingType.class);
        byType.put(BuildingType.MINER, VanillaCategories.MINING);
        byType.put(BuildingType.ELECTRIC_MINER, VanillaCategories.MINING);

        byType.put(BuildingType.CHEST, VanillaCategories.LOGISTICS);
        byType.put(BuildingType.BELT, VanillaCategories.LOGISTICS);
        byType.put(BuildingType.SPLITTER, VanillaCategories.LOGISTICS);
        byType.put(BuildingType.FILTER, VanillaCategories.LOGISTICS);
        byType.put(BuildingType.INSERTER, VanillaCategories.LOGISTICS);
        byType.put(BuildingType.UNDERGROUND_IN, VanillaCategories.LOGISTICS);
        byType.put(BuildingType.UNDERGROUND_OUT, VanillaCategories.LOGISTICS);

        byType.put(BuildingType.FURNACE, VanillaCategories.PRODUCTION);
        byType.put(BuildingType.PRESS, VanillaCategories.PRODUCTION);
        byType.put(BuildingType.ASSEMBLER, VanillaCategories.PRODUCTION);
        byType.put(BuildingType.LAB, VanillaCategories.PRODUCTION);

        byType.put(BuildingType.PIPE, VanillaCategories.FLUIDS);
        byType.put(BuildingType.TANK, VanillaCategories.FLUIDS);
        byType.put(BuildingType.PUMP, VanillaCategories.FLUIDS);
        byType.put(BuildingType.BOILER, VanillaCategories.FLUIDS);

        byType.put(BuildingType.POLE, VanillaCategories.POWER);
        byType.put(BuildingType.GENERATOR, VanillaCategories.POWER);
        return byType;
    }

    /**
     * Every archetype whose Java behavior actually reads {@link BuildingPrototype#power()} at
     * all — {@link Miner} (MINER and ELECTRIC_MINER both resolve to it), {@link Pole}, {@link
     * Generator}. Every OTHER archetype's class never calls {@link TickContext#drawPower} or looks
     * at a {@link PowerSpec}, so a {@code "power"} block on one of them used to load without error
     * and then do nothing at runtime — {@code com.rustorio.mod.BuildingJsonLoader} consults {@link
     * #honorsPower} before accepting the field, closing that gap. {@link EnumSet}, not {@code
     * Set.of}: an error message lists this set's contents, and that list has to read the same way
     * on every run.
     */
    private static final Set<BuildingType> POWER_AWARE_TYPES =
            EnumSet.of(BuildingType.MINER, BuildingType.ELECTRIC_MINER, BuildingType.POLE, BuildingType.GENERATOR);

    /** Whether {@code type}'s Java behavior ever reads a declared {@link PowerSpec} — see {@link #POWER_AWARE_TYPES}. */
    public static boolean honorsPower(BuildingType type) {
        return POWER_AWARE_TYPES.contains(type);
    }

    /** The archetypes {@link #honorsPower} answers {@code true} for — for an error message that has to name them. */
    public static Set<BuildingType> powerAwareArchetypes() {
        return POWER_AWARE_TYPES;
    }

    /** {@code traits} plus this kind's category — the category is added here so no registration site has to remember it. */

    private static final Registry<BuildingPrototype> FROZEN = buildFrozen();

    private VanillaBuildings() {
    }

    /**
     * {@code BuildingType}'s constant name, lowercased, under the {@code rustorio} namespace — the
     * one place callers ask for this conversion, so {@link #registerAll} and any later lookup (a
     * future card) always agree on the same id instead of each retyping the convention
     * independently. Delegates to {@link BuildingType#contentId()} — the actual formula lives
     * there now, not here, since {@code com.rustorio.domain.Recipe}/{@code RecipeBook} need the
     * same conversion but {@code com.rustorio.domain} may not depend on this package (see that
     * method's own javadoc).
     */
    public static ContentId idFor(BuildingType type) {
        return type.contentId();
    }

    /** The canonical, already-frozen registry backing every building's default data. */
    public static Registry<BuildingPrototype> frozen() {
        return FROZEN;
    }

    /**
     * Registers one vanilla building prototype per {@link BuildingType} constant into {@code
     * prototypes}. For tests/custom assemblies that want their own isolated (unfrozen) copy instead
     * of sharing {@link #frozen()}.
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
                PlacementRule.NEEDS_ORE, VanillaSprites.MINER, 0, 1, true,
                (self, direction, factory) -> new Miner(factory.oreLayout(), direction, self),
                (self, decodedState, factory) -> {
                    MinerState state = (MinerState) decodedState;
                    return new Miner(factory.oreLayout(), state.direction(), state.cooldown(), state.held(), state.speedLevel(), self);
                },
                MINER_CODEC, null, Traits.one(VanillaTraits.SPEED_TECH, VanillaTechs.FAST_MINING));
        register(prototypes, BuildingType.CHEST, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.CHEST, true,
                (self, direction, factory) -> new Chest(direction, self),
                (self, decodedState, factory) -> {
                    ChestState state = (ChestState) decodedState;
                    return new Chest(state.direction(), state.contents(), state.speedLevel(), self);
                },
                CHEST_CODEC);
        registerFurnaceLike(prototypes, BuildingType.FURNACE, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                VanillaSprites.FURNACE_COLD, 5, 1, VanillaItems.COAL);
        register(prototypes, BuildingType.BELT, new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.BELT_EMPTY, false,
                (self, direction, factory) -> new Belt(direction, self),
                (self, decodedState, factory) -> {
                    BeltState state = (BeltState) decodedState;
                    return new Belt(state.direction(), state.held(), self);
                },
                BELT_CODEC);
        register(prototypes, BuildingType.SPLITTER, new BuildingCost(VanillaItems.IRON_PLATE, 3),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.SPLITTER, false,
                (self, direction, factory) -> new Splitter(direction, self),
                (self, decodedState, factory) -> {
                    SplitterState state = (SplitterState) decodedState;
                    return new Splitter(state.facing(), state.held(), state.nextIsForward(), self);
                },
                SPLITTER_CODEC);
        registerFurnaceLike(prototypes, BuildingType.PRESS, new BuildingCost(VanillaItems.IRON_PLATE, 8),
                VanillaSprites.FURNACE_COLD, 5, 1, null);
        register(prototypes, BuildingType.UNDERGROUND_IN, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_IN, false,
                (self, direction, factory) -> new UndergroundBelt(UndergroundBelt.Kind.IN, direction, self),
                UNDERGROUND_BELT_RESTORE,
                UNDERGROUND_BELT_CODEC);
        register(prototypes, BuildingType.UNDERGROUND_OUT, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.ALWAYS, VanillaSprites.UNDERGROUND_OUT, false,
                (self, direction, factory) -> new UndergroundBelt(UndergroundBelt.Kind.OUT, direction, self),
                UNDERGROUND_BELT_RESTORE,
                UNDERGROUND_BELT_CODEC);
        register(prototypes, BuildingType.LAB, new BuildingCost(VanillaItems.GEAR, 10),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.LAB, 0, 1, true,
                (self, direction, factory) -> new Lab(factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    LabState state = (LabState) decodedState;
                    return new Lab(factory.recipeBook(), state.buffer(), state.cooldown(), state.speedLevel(), self);
                },
                LAB_CODEC, null, Traits.one(VanillaTraits.SPEED_TECH, VanillaTechs.FAST_LAB));
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
                (self, direction, factory) -> new Filter(direction, VanillaItems.IRON_ORE, factory.items(), self),
                (self, decodedState, factory) -> {
                    FilterState state = (FilterState) decodedState;
                    return new Filter(state.facing(), state.filterItem(), state.held(), factory.items(), self);
                },
                FILTER_CODEC);
        register(prototypes, BuildingType.INSERTER, new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.INSERTER, false,
                (self, direction, factory) -> new Inserter(direction, self),
                (self, decodedState, factory) -> {
                    InserterState state = (InserterState) decodedState;
                    return new Inserter(state.direction(), state.held(), self);
                },
                INSERTER_CODEC);
        registerFurnaceLike(prototypes, BuildingType.ASSEMBLER, new BuildingCost(VanillaItems.GEAR, 15),
                VanillaSprites.ASSEMBLER, 5, 1, null);
        registerFluidNode(prototypes, BuildingType.PIPE, new BuildingCost(VanillaItems.IRON_PLATE, 1),
                VanillaSprites.PIPE, PIPE_CAPACITY);
        registerFluidNode(prototypes, BuildingType.TANK, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                VanillaSprites.TANK, TANK_CAPACITY);
        register(prototypes, BuildingType.PUMP, new BuildingCost(VanillaItems.IRON_PLATE, 5),
                PlacementRule.ADJACENT_TO_WATER, VanillaSprites.PUMP, 0, 1, false,
                (self, direction, factory) -> new Pump(BuildingType.PUMP, direction, self, factory.oreLayout()),
                (self, decodedState, factory) -> {
                    PumpState state = (PumpState) decodedState;
                    return new Pump(BuildingType.PUMP, state.direction(), state.cooldown(), self, factory.oreLayout());
                },
                PUMP_CODEC, null, Traits.one(VanillaTraits.FLUID_OUTPUT, VanillaFluids.WATER));
        register(prototypes, BuildingType.BOILER, new BuildingCost(VanillaItems.IRON_PLATE, 8),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.BOILER, 0, 1, false,
                (self, direction, factory) -> new Boiler(BuildingType.BOILER, direction, self),
                (self, decodedState, factory) -> {
                    BoilerState state = (BoilerState) decodedState;
                    return new Boiler(BuildingType.BOILER, state, self);
                },
                BOILER_CODEC, VanillaItems.COAL, Traits.of(new LinkedHashMap<>(Map.of(
                        VanillaTraits.FLUID_INPUT, VanillaFluids.WATER,
                        VanillaTraits.FLUID_OUTPUT, VanillaFluids.STEAM))));
        register(prototypes, BuildingType.POLE, new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.POLE, 0, 1, false,
                (self, direction, factory) -> new Pole(BuildingType.POLE, self),
                (self, decodedState, factory) -> new Pole(BuildingType.POLE, self),
                POLE_CODEC, null, Traits.one(VanillaTraits.POWER, PowerSpec.pole(POLE_RADIUS)));
        register(prototypes, BuildingType.GENERATOR, new BuildingCost(VanillaItems.GEAR, 10),
                PlacementRule.NEEDS_PASSABLE_TERRAIN, VanillaSprites.GENERATOR, 0, 1, false,
                (self, direction, factory) -> new Generator(BuildingType.GENERATOR, direction, self),
                (self, decodedState, factory) -> {
                    GeneratorState state = (GeneratorState) decodedState;
                    return new Generator(BuildingType.GENERATOR, state.direction(), self);
                },
                GENERATOR_CODEC, null, Traits.of(new LinkedHashMap<>(Map.of(
                        VanillaTraits.FLUID_INPUT, VanillaFluids.STEAM,
                        VanillaTraits.POWER, PowerSpec.generator(GENERATOR_OUTPUT)))));
        // The same Miner archetype as the plain one, and deliberately so: what makes this one
        // electric is a declared demand, which is data. If an electric variant needed its own Java
        // class, "electricity is opt-in" would be a claim about one hardcoded building rather than
        // about every building a mod might write.
        register(prototypes, BuildingType.ELECTRIC_MINER, new BuildingCost(VanillaItems.GEAR, 5),
                PlacementRule.NEEDS_ORE, VanillaSprites.ELECTRIC_MINER, 0, 1, true,
                (self, direction, factory) ->
                        new Miner(BuildingType.ELECTRIC_MINER, factory.oreLayout(), direction, self),
                (self, decodedState, factory) -> {
                    MinerState state = (MinerState) decodedState;
                    return new Miner(BuildingType.ELECTRIC_MINER, factory.oreLayout(), state.direction(),
                            state.cooldown(), state.held(), state.speedLevel(), self);
                },
                MINER_CODEC, null, Traits.of(new LinkedHashMap<>(Map.of(
                        VanillaTraits.POWER, PowerSpec.consumer(ELECTRIC_MINER_DEMAND),
                        VanillaTraits.SPEED_TECH, VanillaTechs.FAST_MINING))));
    }

    /**
     * How far a pole reaches, in cells. Not a balance pass — five cells means a pole covers an 11x11
     * square, so a modest factory needs a handful of them and planning where they go is a real
     * question without being a chore.
     */
    private static final int POLE_RADIUS = 5;

    /**
     * One generator's output per tick, and one electric machine's demand. A balance pass now,
     * anchored on Factorio's 900 kW steam engine to 90 kW mining drill — ten to one — so one
     * generator carries exactly ten electric miners and the grid is something to plan against.
     */
    private static final int GENERATOR_OUTPUT = 100;

    private static final int ELECTRIC_MINER_DEMAND = 10;

    /**
     * One pipe tile's volume. Not derived from anything — no balance pass has run on fluids yet —
     * so it is a starting proposal, chosen so a short run of pipe is a line, not a reservoir: a
     * player who wants to store fluid should have to build the thing meant for storing it.
     */
    private static final int PIPE_CAPACITY = 100;

    /** A tank holds a whole line's worth of pipe — the same "storage is a deliberate building" proposal as {@link #PIPE_CAPACITY}, seen from the other end. */
    private static final int TANK_CAPACITY = 2500;

    /**
     * A {@link Pipe}-archetype tile (PIPE/TANK, one class for both) — always {@link
     * PlacementRule#NEEDS_PASSABLE_TERRAIN}, never accepts speed effects (there is no {@code tick}
     * to run twice; see {@link Pipe}'s own javadoc).
     *
     * <p>{@code capacity} rides in on {@code bufferMax}, the field a {@link Furnace}-kind uses for
     * its input buffer, rather than on a new prototype component of its own: both are the same
     * question — how much does this building hold — asked of archetypes that can never be the same
     * building, and a JSON-authored pipe therefore needs no field the loader doesn't already read.
     */
    private static void registerFluidNode(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, ContentId texture, int capacity) {
        register(prototypes, type, cost, PlacementRule.NEEDS_PASSABLE_TERRAIN, texture, capacity, 1, false,
                (self, direction, factory) -> new Pipe(type, self),
                (self, decodedState, factory) -> {
                    PipeState state = (PipeState) decodedState;
                    ContentId fluidId = state.fluid();
                    // getOrUnknown, not get: a save may name a fluid whose mod has since been
                    // removed, and that is content merely ABSENT, not a file that is unreadable —
                    // get() would throw, JsonSaveRepository would turn it into a Failure, and the
                    // whole factory would be rejected over one pipe. A missing prototype already
                    // degrades this way; the tile comes back, empty, with the volume it held gone
                    // along with the fluid that no longer has a name.
                    FluidType fluid = fluidId == null ? null : factory.fluids().getOrUnknown(fluidId).orElse(null);
                    return new Pipe(type, self, fluid, fluid == null ? 0 : state.amount());
                },
                PIPE_CODEC, null);
    }

    private static Registry<BuildingPrototype> buildFrozen() {
        Registry<BuildingPrototype> prototypes = new Registry<>();
        registerAll(prototypes);
        prototypes.freeze();
        return prototypes;
    }

    /** Every non-{@link Furnace} archetype: {@code bufferMax}/{@code speedMultiplier} are meaningless to it, registered as {@code 0}/{@code 1}, no fuel. */
    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, boolean acceptsSpeedEffects,
            BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec) {
        register(prototypes, type, cost, placementRule, texture, 0, 1, acceptsSpeedEffects, behavior,
                restoreBehavior, codec, null);
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
     * identical regardless of kind. {@code fuelItem} is {@link VanillaItems#COAL} for FURNACE only,
     * {@code null} for PRESS/ASSEMBLER (D-05, DEV_TASKS.md) — see {@link BuildingPrototype}'s own
     * javadoc for why this is data on the prototype now, not a {@code kind == FURNACE} check inside
     * {@link Furnace} itself. {@code recipeKind} is left at its default (the prototype's own {@code
     * id}) — exactly {@code idFor(type)}, the shared pool these three vanilla kinds have always had.
     */
    private static void registerFurnaceLike(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, ContentId texture, int bufferMax, int speedMultiplier, @Nullable ItemType fuelItem) {
        register(prototypes, type, cost, PlacementRule.NEEDS_PASSABLE_TERRAIN, texture, bufferMax, speedMultiplier, true,
                (self, direction, factory) -> new Furnace(type, direction, factory.recipeBook(), self),
                (self, decodedState, factory) -> {
                    FurnaceState state = (FurnaceState) decodedState;
                    return new Furnace(type, state, factory.recipeBook(), self);
                },
                FURNACE_CODEC, fuelItem, Traits.one(VanillaTraits.SPEED_TECH, VanillaTechs.FAST_SMELTING));
    }

    /** {@code label}/{@code footprintWidth}/{@code footprintHeight} come from {@code type} itself — the single source of truth for every vanilla prototype's data, not retyped here. {@code recipeKind} defaults to the prototype's own {@code id} (see {@link BuildingPrototype}'s own convenience constructor) — the shared pool every vanilla kind has always had, since each is registered under its own {@code idFor(type)}. */
    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
            @Nullable ItemType fuelItem) {
        register(prototypes, type, cost, placementRule, texture, bufferMax, speedMultiplier, acceptsSpeedEffects,
                behavior, restoreBehavior, codec, fuelItem, Traits.NONE);
    }

    /**
     * The full form, for an archetype that declares an optional property — a fluid port, a power
     * spec. ONE such form now, whatever the property: this used to be a rung per property, and
     * adding fluids and electricity added two of them at once.
     */
    private static void register(Registry<BuildingPrototype> prototypes, BuildingType type,
            BuildingCost cost, PlacementRule placementRule, ContentId texture, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
            @Nullable ItemType fuelItem, Traits traits) {
        ContentId id = idFor(type);
        prototypes.register(id, new BuildingPrototype(id, type.label(), cost, placementRule, texture,
                type.footprintWidth(), type.footprintHeight(), bufferMax, speedMultiplier,
                acceptsSpeedEffects, behavior, restoreBehavior, codec, id, fuelItem,
                withCategory(type, traits)));
    }

    private static Traits withCategory(BuildingType type, Traits traits) {
        Map<TraitKey<?>, Object> merged = new LinkedHashMap<>(traits.asMap());
        merged.put(VanillaCategories.CATEGORY, CATEGORY_BY_TYPE.get(type));
        return Traits.of(merged);
    }
}

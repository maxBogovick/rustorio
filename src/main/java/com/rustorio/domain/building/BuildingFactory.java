package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import org.jspecify.annotations.Nullable;

/**
 * Factory Method: the one place that turns "build a {@link BuildingType} facing this way" or "here
 * is a captured {@link BuildingMemento}" into a live {@link Building} instance. Everything each
 * concrete building needs beyond its own state — the {@link OreLayout} a {@link Miner} reads, the
 * {@link RecipeBook} a {@link Furnace} searches, the {@link Registry} a {@link Filter} cycles
 * through — is injected here once, at construction, instead of every building reaching for
 * static, shared state on its own.
 *
 * <p>Consolidates what used to be two separate hand-written {@code switch} statements living in
 * two different classes ({@code World}'s placement dispatch and the save/load file's loading
 * dispatch) into one place that both {@code World} (new buildings) and the persistence layer
 * (restored buildings) call through.
 */
public final class BuildingFactory {

    private final OreLayout oreLayout;
    private final RecipeBook recipeBook;
    private final Registry<ItemType> items;
    private final Registry<BuildingPrototype> prototypes;

    /** Convenience for callers that only care about the vanilla item/building sets — see the 4-arg constructor for real injection. */
    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook) {
        this(oreLayout, recipeBook, VanillaItems.frozen(), VanillaBuildings.frozen());
    }

    /** Convenience for callers that only care about the vanilla building set — see the 4-arg constructor for real injection. */
    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook, Registry<ItemType> items) {
        this(oreLayout, recipeBook, items, VanillaBuildings.frozen());
    }

    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook, Registry<ItemType> items,
            Registry<BuildingPrototype> prototypes) {
        this.oreLayout = oreLayout;
        this.recipeBook = recipeBook;
        this.items = items;
        this.prototypes = prototypes;
    }

    /** The game's default factory: the standard ore map and the standard recipe set. */
    public static BuildingFactory standard() {
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard());
    }

    public OreLayout oreLayout() {
        return oreLayout;
    }

    public RecipeBook recipeBook() {
        return recipeBook;
    }

    /** The item registry this factory's buildings were built with — what {@link Filter#cycleFilterItem()} cycles through. */
    public Registry<ItemType> items() {
        return items;
    }

    /** {@code type}'s data — cost, placement rule, texture — read from this factory's own registry instead of a switch. */
    public BuildingPrototype prototype(BuildingType type) {
        return prototypes.get(VanillaBuildings.idFor(type));
    }

    /**
     * Whether {@code type} satisfies its {@link PlacementRule} at {@code (x, y)} — the type-
     * specific half of {@code World.place}'s check; the free+in-bounds half is {@code World}'s own
     * business and stays there. See P3-04, BUG_FIX_PROGRESS.md.
     */
    public boolean canPlace(BuildingType type, int x, int y) {
        return prototype(type).placementRule().test(x, y, oreLayout);
    }

    /** Build a brand-new building of {@code type}, facing {@code direction} where that matters. */
    public Building create(BuildingType type, Direction direction) {
        return switch (type) {
            case MINER -> new Miner(oreLayout, direction);
            case CHEST -> new Chest(direction);
            case FURNACE -> new Furnace(BuildingType.FURNACE, direction, recipeBook, prototype(BuildingType.FURNACE));
            case PRESS -> new Furnace(BuildingType.PRESS, direction, recipeBook, prototype(BuildingType.PRESS));
            case BELT -> new Belt(direction);
            case SPLITTER -> new Splitter(direction);
            // Default filterItem is IRON_ORE — the more common ore, and a reasonable starting
            // point for a newly-placed FILTER. NOT full parity with the old (deleted)
            // SortRule.ORE_FORWARD, which forwarded BOTH IRON_ORE and BRONZE_ORE: Filter passes
            // exactly ONE item identity by design (see Filter's own class javadoc — that's the
            // whole point of replacing a fixed multi-item rule with player-cyclable data), so no
            // single default can replicate a two-item rule. A default Filter on a bronze line will
            // route bronze ore to the side lane until the player cycles it (F) to BRONZE_ORE —
            // this comment previously overclaimed equivalence with ORE_FORWARD (code review
            // finding); fixing the mismatch means fixing the CLAIM, since Filter's single-item
            // design is deliberate, not a bug.
            case FILTER -> new Filter(direction, VanillaItems.IRON_ORE, items);
            case INSERTER -> new Inserter(direction);
            case UNDERGROUND_IN -> new UndergroundBelt(UndergroundBelt.Kind.IN, direction);
            case UNDERGROUND_OUT -> new UndergroundBelt(UndergroundBelt.Kind.OUT, direction);
            case LAB -> new Lab(recipeBook);
            // Reuses Furnace outright (X-03, DEV_TASKS.md) rather than a new Building
            // implementation: a 2x2 footprint plus a dedicated ASSEMBLER-kind recipe (see
            // RecipeBook) is the entire difference from PRESS — see Furnace#footprintWidth.
            case ASSEMBLER -> new Furnace(BuildingType.ASSEMBLER, direction, recipeBook, prototype(BuildingType.ASSEMBLER));
        };
    }

    /**
     * Rebuild a building from a captured {@link BuildingMemento}, then re-apply {@code
     * speedLevel} layers of {@link SpeedModule} — the wrapper's own state lives outside the
     * memento entirely (see {@link Building#speedLevel()}), so the persistence layer tracks it
     * separately and this method re-wraps rather than trying to recover it from the memento.
     *
     * <p>The pattern-matching {@code switch} over the sealed {@link BuildingMemento} hierarchy is
     * exhaustive by construction: adding a new building kind without a matching case here is a
     * compile error, not a runtime surprise. Unlike {@link #create}, this takes no separate {@link
     * BuildingType} — every memento variant already carries everything needed to rebuild its exact
     * building, including {@link BuildingMemento.FurnaceState#kind()} for telling {@code FURNACE}
     * apart from {@code PRESS}, so there is exactly one source of truth for "what kind is this."
     */
    public Building restore(BuildingMemento memento, int speedLevel) {
        Building building = switch (memento) {
            case BuildingMemento.MinerState s -> new Miner(oreLayout, s.direction(), s.cooldown(), s.held());
            case BuildingMemento.ChestState s -> new Chest(s.direction(), s.contents());
            case BuildingMemento.FurnaceState s -> new Furnace(s, recipeBook, furnacePrototype(s));
            case BuildingMemento.BeltState s -> new Belt(s.direction(), s.held());
            case BuildingMemento.SplitterState s -> new Splitter(s.facing(), s.held(), s.nextIsForward());
            case BuildingMemento.FilterState s -> new Filter(s.facing(), s.filterItem(), s.held(), items);
            case BuildingMemento.InserterState s -> new Inserter(s.direction(), s.held());
            case BuildingMemento.LabState s -> new Lab(recipeBook, s.buffer(), s.cooldown());
            case BuildingMemento.UndergroundBeltState s -> new UndergroundBelt(s.kind(), s.direction(), s.held());
        };
        for (int i = 0; i < speedLevel; i++) {
            building = new SpeedModule(building);
        }
        return building;
    }

    /**
     * {@code state}'s own prototype from THIS factory's registry, or {@code state.kind()}'s
     * default when {@code prototypeId} is {@code null} (a save written before that field existed).
     */
    private BuildingPrototype furnacePrototype(BuildingMemento.FurnaceState state) {
        ContentId id = state.prototypeId();
        return id != null ? prototypes.get(id) : prototype(state.kind());
    }

    /**
     * The narrow public door {@code World} reaches {@link TransportNode#attachToNeighbors} through
     * (P3-02, BUG_FIX_PROGRESS.md): {@code World} owns the cell map and finds which neighbors (if
     * any) sit behind/ahead of a freshly placed or restored node, but everything past that —
     * actually wiring tiles into a {@link BeltSegment} — is this package's business, not {@code
     * World}'s. Takes {@link TransportNode}, not specifically {@link Belt}: any capability
     * implementer can be attached the same way, not just the vanilla one.
     */
    public static void attachTransportNode(TransportNode node, @Nullable TransportNode behind, @Nullable TransportNode ahead) {
        node.attachToNeighbors(behind, ahead);
    }

    /**
     * The narrow public door {@code World} reaches {@link TransportNode#leaveSegment} through
     * (P3-02, BUG_FIX_PROGRESS.md) — on demolition, or to make a re-restore idempotent (see {@code
     * World.restoreBuilding}).
     */
    public static void detachTransportNode(TransportNode node) {
        node.leaveSegment();
    }

    /**
     * The narrow public door {@code TickScheduler} reaches every arrival mark through (P3-03,
     * BUG_FIX_PROGRESS.md) — called once per building, once per world tick, before either traversal
     * pass runs. Buildings that carry no mark (a chest, a furnace) simply have nothing to clear —
     * they don't implement {@link SettlesEachTick} at all.
     *
     * <p>One method taking any {@link Building} rather than one overload per kind (N2,
     * NEW_BUGS_PROGRESS.md): a relay kind that needs a settle implements the capability interface
     * and is picked up here automatically, instead of this method (or {@code TickScheduler}) growing
     * a new branch every time one is added. {@link Building#unwrap} first, so a {@link
     * SpeedModule}-wrapped relay is marked-cleared exactly like a bare one.
     */
    public static void clearArrivalMark(Building building) {
        if (Building.unwrap(building) instanceof SettlesEachTick settling) {
            settling.clearArrivalMark();
        }
    }
}

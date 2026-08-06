package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Cell;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaFluids;
import com.rustorio.domain.VanillaItems;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Factory Method: the one place that turns "build a {@link BuildingType} facing this way" or "here
 * is a decoded state" into a live {@link Building} instance. Everything each concrete building
 * needs beyond its own state — the {@link OreLayout} a {@link Miner} reads, the {@link RecipeBook}
 * a {@link Furnace} searches, the {@link Registry} a {@link Filter} cycles through — is injected
 * here once, at construction, instead of every building reaching for static, shared state on its
 * own.
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
    private final Registry<FluidType> fluids;

    /** Convenience for callers that only care about the vanilla item/building sets — see the 5-arg constructor for real injection. */
    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook) {
        this(oreLayout, recipeBook, VanillaItems.frozen(), VanillaBuildings.frozen());
    }

    /** Convenience for callers that only care about the vanilla building set — see the 5-arg constructor for real injection. */
    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook, Registry<ItemType> items) {
        this(oreLayout, recipeBook, items, VanillaBuildings.frozen());
    }

    /** Convenience for callers that only care about the vanilla fluid set — see the 5-arg constructor for real injection. */
    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook, Registry<ItemType> items,
            Registry<BuildingPrototype> prototypes) {
        this(oreLayout, recipeBook, items, prototypes, VanillaFluids.frozen());
    }

    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook, Registry<ItemType> items,
            Registry<BuildingPrototype> prototypes, Registry<FluidType> fluids) {
        this.oreLayout = oreLayout;
        this.recipeBook = recipeBook;
        this.items = items;
        this.prototypes = prototypes;
        this.fluids = fluids;
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

    /**
     * The fluid registry this factory's buildings were built with — what a {@link Pipe}'s own
     * {@code RestoreFactory} resolves a save's recorded fluid id against (see {@link PipeState} for
     * why the resolution happens there rather than inside the {@link Codec}).
     */
    public Registry<FluidType> fluids() {
        return fluids;
    }

    /** The building prototype registry this factory was built with — every registered prototype, vanilla or modded, for a UI that lists them all (a build menu) rather than looking one up by id. */
    public Registry<BuildingPrototype> buildings() {
        return prototypes;
    }

    /** {@code type}'s data — cost, placement rule, texture — read from this factory's own registry instead of a switch. */
    public BuildingPrototype prototype(BuildingType type) {
        return prototypes.get(VanillaBuildings.idFor(type));
    }

    /** {@code id}'s data, vanilla or modded — the {@link ContentId} counterpart to {@link #prototype(BuildingType)}, for content that has no {@link BuildingType} of its own at all. */
    public BuildingPrototype prototype(ContentId id) {
        return prototypes.get(id);
    }

    /**
     * {@link #prototype(ContentId)}, but {@link Optional#empty()} instead of throwing when {@code
     * id} isn't registered — the save-loading path needs to detect a save naming content whose mod
     * was removed and report it, not crash (see {@code JsonSaveRepository#load}).
     */
    public Optional<BuildingPrototype> prototypeOrUnknown(ContentId id) {
        return prototypes.getOrUnknown(id);
    }

    /**
     * Whether the prototype registered under {@code id} — vanilla or modded — satisfies its {@link
     * PlacementRule} at {@code (x, y)}: the content-specific half of {@code World.place}'s check;
     * the free+in-bounds half is {@code World}'s own business and stays there.
     */
    public boolean canPlace(ContentId id, int x, int y) {
        return prototype(id).placementRule().test(x, y, oreLayout);
    }

    /** Convenience for the closed vanilla set — resolves {@code type}'s own prototype id and delegates to {@link #canPlace(ContentId, int, int)}. See P3-04, BUG_FIX_PROGRESS.md. */
    public boolean canPlace(BuildingType type, int x, int y) {
        return canPlace(VanillaBuildings.idFor(type), x, y);
    }

    /**
     * Build a brand-new building from any registered prototype — vanilla or modded, {@code id}
     * doesn't need a corresponding {@link BuildingType} at all — facing {@code direction} where
     * that matters. Delegates the actual construction to {@code id}'s own registered {@link
     * BehaviorFactory} (see {@link VanillaBuildings#registerAll}): this class no longer contains a
     * single {@code new Miner(...)}/{@code new Chest(...)} call anywhere.
     */
    public Building create(ContentId id, Direction direction) {
        BuildingPrototype proto = prototypes.get(id);
        return proto.behavior().create(proto, direction, this);
    }

    /** Convenience for the closed vanilla set — resolves {@code type}'s own prototype id and delegates to {@link #create(ContentId, Direction)}. */
    public Building create(BuildingType type, Direction direction) {
        return create(VanillaBuildings.idFor(type), direction);
    }

    /**
     * Rebuild a building from a save's own explicit {@code prototypeId} and raw (still-encoded)
     * state, delegating the actual construction to the governing prototype's own registered
     * {@link RestoreFactory} (see {@link VanillaBuildings#registerAll}) — this class contains no
     * {@code new Miner(...)}/{@code new Chest(...)} call anywhere, exactly like {@link #create}
     * above. Decodes through {@code prototypeId}'s own {@link BuildingPrototype#codec()} first —
     * callers hand this method exactly what a save file stores, not a pre-decoded value.
     *
     * <p>No {@code switch} anywhere in this method: unlike the old {@code BuildingMemento}-based
     * design (where the sealed memento's own TYPE had to be pattern-matched to find which
     * prototype governed it), {@code prototypeId} names the governing prototype directly — the
     * save's own envelope already says which one, nothing here needs to infer it.
     */
    public Building restore(ContentId prototypeId, Object rawEncodedState) {
        BuildingPrototype proto = prototypes.get(prototypeId);
        Object decodedState = proto.decodeState(rawEncodedState, items);
        return proto.restoreBehavior().restore(proto, decodedState, this);
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
     * The fluid counterpart to {@link #attachTransportNode}, and a narrow door for the same reason:
     * {@code World} owns the cell map, so only it can find which of the four neighboring cells hold
     * fluid tiles — everything past that (merging networks, splitting them) is this package's
     * business. {@code neighbors} are those tiles in a fixed side order; see {@link
     * FluidNetwork#attach} for why the side each came from is deliberately not passed along.
     */
    public static void attachFluidNode(FluidNode node, int x, int y, List<FluidNode> neighbors) {
        FluidNetwork.attach(node, new Cell(x, y), neighbors);
    }

    /** The fluid counterpart to {@link #detachTransportNode} — on demolition, or to make a re-restore idempotent (see {@code World.restoreBuilding}). */
    public static void detachFluidNode(FluidNode node, int x, int y) {
        FluidNetwork.detach(node, new Cell(x, y));
    }

    /**
     * The electrical counterpart to {@link #attachFluidNode}: {@code World} finds the already-placed
     * poles close enough to connect (it knows where poles stand), and everything past that — merging
     * grids — stays in this package.
     */
    public static void attachPowerNode(PowerNode node, int x, int y, List<PowerNode> neighbors) {
        PowerNetwork.attach(node, new Cell(x, y), neighbors);
    }

    /**
     * The electrical counterpart to {@link #detachFluidNode}. {@code reach} answers "do these two
     * poles see each other" for whatever poles are left, which is again a question about positions
     * and therefore {@code World}'s to answer.
     */
    public static void detachPowerNode(PowerNode node, int x, int y, PowerNetwork.Reach reach) {
        PowerNetwork.detach(node, new Cell(x, y), reach);
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
     * a new branch every time one is added.
     */
    public static void clearArrivalMark(Building building) {
        if (building instanceof SettlesEachTick settling) {
            settling.clearArrivalMark();
        }
    }
}

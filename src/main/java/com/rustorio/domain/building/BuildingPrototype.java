package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemType;
import org.jspecify.annotations.Nullable;

/**
 * A building's data — everything about it that doesn't depend on which Java class implements its
 * behavior: what it costs, where it may be placed, which sprite draws it, and (via {@link
 * #behavior}) which existing Java class actually implements it. Addressed by {@link ContentId}
 * instead of being read off a fixed {@link com.rustorio.domain.BuildingType} constant via a {@code
 * switch} — a new building variant (a faster, pricier furnace) is a new registered value here, not
 * a new {@code case} in {@code BuildingCost}/{@code PlacementRule}/{@code Textures}, and — since
 * {@link #behavior} joined this record — not a new {@code case} in {@code
 * BuildingFactory.create/restore} either.
 *
 * <p>{@code bufferMax} is "how much does this building hold": a {@link Furnace}-kind's input buffer,
 * and a {@link Pipe}-kind's fluid volume ({@link Pipe#fluidCapacity()}). One field rather than two,
 * because no building is ever both, and a JSON-authored pipe therefore needs no key the loader
 * didn't already read. {@code speedMultiplier} stays {@link Furnace}-specific; every other archetype
 * ignores both (registered as {@code 0}/{@code 1}, see {@link VanillaBuildings}). Deliberately not a
 * general per-archetype parameter bag: a generic mechanism for parameters nothing else reads yet
 * would be exactly the "abstraction for a future that isn't this card's job" the project's own
 * design checklist warns against.
 *
 * <p>{@code label} is this prototype's display name — data, not a hardcoded switch over {@code
 * BuildingType}, so a UI that lists every registered prototype (a build menu, a hotbar slot) never
 * needs a case for a new one. {@code footprintWidth}/{@code footprintHeight} are read by {@link
 * Furnace} (the only archetype whose vanilla footprint isn't 1×1 — {@code ASSEMBLER}) instead of
 * the {@link com.rustorio.domain.BuildingType} constant it used to read them from; a JSON-
 * configured building reusing that archetype can now genuinely have its own footprint, not just
 * borrow the vanilla kind's.
 *
 * <p>{@code acceptsSpeedEffects} replaces {@code UpgradeSpeedAction}'s old {@code instanceof}
 * chain over six single-slot/segment-joining classes: whether a kind's second {@code tick()} call
 * (from a speed wrapper) does anything meaningful is a property of the kind, not something the
 * upgrade action should determine by checking concrete Java types.
 *
 * <p>{@link #behavior}/{@link #restoreBehavior} replace {@code BuildingFactory.create/restore}'s
 * old hardcoded {@code switch} over {@link com.rustorio.domain.BuildingType} — two separate
 * single-method interfaces, not one with two methods, so both stay plain lambdas at every
 * registration site (see {@link BehaviorFactory}/{@link RestoreFactory}'s own javadoc for why
 * casting inside a restore lambda is safe, not defensive).
 *
 * <p>{@link #codec} is the persistence counterpart to {@link #behavior}/{@link #restoreBehavior}:
 * turns whatever {@link Building#state()} returns into a plain JSON-shaped value and back (see
 * {@link Codec}'s own javadoc) — erased to {@code Codec<?>} here for the same reason {@code
 * behavior}/{@code restoreBehavior} are erased to their own building-agnostic interfaces rather
 * than parametrizing this whole record by an archetype type.
 *
 * <p>{@link #recipeKind}/{@link #fuelItem} are what let a JSON-defined building reusing a
 * {@link Furnace}-archetype (FURNACE/PRESS/ASSEMBLER) get its own recipe pool and fuel rules with
 * no Java at all: {@code recipeKind} is the open {@link ContentId} {@code RecipeBook.forKind}
 * actually searches by (defaults to this prototype's own {@link #id} — private, so a new custom
 * building never accidentally collides or goes ambiguous against the vanilla pools unless it
 * explicitly names one of them, or another building's {@code recipeKind}, itself); {@code
 * fuelItem} is which item (if any) {@link Furnace} burns as fuel rather than a recipe ingredient
 * — {@code null} for none, same as PRESS/ASSEMBLER today, not hardcoded to {@code COAL} anymore.
 *
 * <p>{@link #fluidInput}/{@link #fluidOutput} are the same idea for fluid: WHICH fluid a machine
 * pulls in and which it pushes out, as data rather than a constant inside the archetype — the
 * reason {@link Pump} contains no mention of water and {@link Boiler} none of steam, so a mod's own
 * "oil derrick" or "cracking plant" is a JSON file on the same two archetypes. {@code null} means
 * this building has no such port; every archetype other than those two leaves both null.
 *
 * <p>{@link #power} is the electrical counterpart, and one grouped {@link PowerSpec} rather than
 * three loose numbers so that "this building has nothing to do with electricity" stays a single
 * {@code null} — which is what almost every building is, and what keeps electricity opt-in.
 *
 * <p>WHERE the ports sit is not stored: a machine's own facing is the output side and the cell
 * behind it is the input, the same convention every directional building here already follows. A
 * per-side port map is a real feature (a machine with two inputs on different sides) but it would be
 * a second way to say what {@code direction} already says for the machines that exist today.
 */
public record BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
        ContentId texture, int footprintWidth, int footprintHeight, int bufferMax, int speedMultiplier,
        boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
        ContentId recipeKind, @Nullable ItemType fuelItem,
        @Nullable FluidType fluidInput, @Nullable FluidType fluidOutput, @Nullable PowerSpec power) {

    /**
     * Convenience for a prototype that doesn't need its OWN private recipe pool or a fuel item —
     * {@link #recipeKind} defaults to this prototype's own {@link #id} (a private pool no other
     * prototype shares unless it explicitly names the same one — see {@code BuildingJsonLoader}),
     * {@link #fuelItem} to none. Every {@code Furnace}-archetype vanilla registration
     * ({@code VanillaBuildings#registerFurnaceLike}) that DOES need to share a pool or burn fuel
     * calls the fuel-aware constructor below directly instead.
     */
    public BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
            ContentId texture, int footprintWidth, int footprintHeight, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec) {
        this(id, label, cost, placementRule, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier,
                acceptsSpeedEffects, behavior, restoreBehavior, codec, id, null);
    }

    /**
     * Convenience for a prototype that touches no fluid at all — every archetype except a pump and a
     * boiler, which are the only two that name a {@link #fluidInput}/{@link #fluidOutput}. Exists so
     * adding those two ports didn't have to rewrite every registration site that never had an
     * opinion about fluid in the first place.
     */
    public BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
            ContentId texture, int footprintWidth, int footprintHeight, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
            ContentId recipeKind, @Nullable ItemType fuelItem) {
        this(id, label, cost, placementRule, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier,
                acceptsSpeedEffects, behavior, restoreBehavior, codec, recipeKind, fuelItem, null, null, null);
    }

    /** Convenience for the archetypes that name fluid ports but nothing electrical — a pump and a boiler today. */
    public BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
            ContentId texture, int footprintWidth, int footprintHeight, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
            ContentId recipeKind, @Nullable ItemType fuelItem,
            @Nullable FluidType fluidInput, @Nullable FluidType fluidOutput) {
        this(id, label, cost, placementRule, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier,
                acceptsSpeedEffects, behavior, restoreBehavior, codec, recipeKind, fuelItem, fluidInput, fluidOutput,
                null);
    }

    /** Convenience for the common 1×1 footprint — every archetype except {@code ASSEMBLER} today. */
    public BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
            ContentId texture, int bufferMax, int speedMultiplier, boolean acceptsSpeedEffects,
            BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec) {
        this(id, label, cost, placementRule, texture, 1, 1, bufferMax, speedMultiplier, acceptsSpeedEffects,
                behavior, restoreBehavior, codec);
    }

    /**
     * Encodes {@code liveState} (whatever {@link Building#state()} returned) via {@link #codec} —
     * a small unchecked-cast wrapper so callers (persistence) don't need to know or cast to the
     * exact erased type themselves; safe because {@code liveState} always comes from THIS SAME
     * prototype's own governing archetype, whose {@code state()} return type matches {@link
     * #codec}'s type parameter by construction.
     */
    @SuppressWarnings("unchecked")
    public Object encodeState(Object liveState) {
        return ((Codec<Object>) codec).encode(liveState);
    }

    /** The decoding counterpart to {@link #encodeState} — same unchecked-cast reasoning. */
    @SuppressWarnings("unchecked")
    public Object decodeState(Object rawState, Registry<ItemType> items) {
        return ((Codec<Object>) codec).decode(rawState, items);
    }
}

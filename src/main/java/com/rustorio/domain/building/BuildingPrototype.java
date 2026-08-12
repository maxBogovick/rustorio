package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.FluidType;
import com.rustorio.api.content.model.ItemType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * <p>{@link #traits} is where OPTIONAL properties live — which fluid a machine draws, what it asks
 * of the power grid — instead of each being a component of its own. They were components once, and
 * the cost showed the moment there were three of them: every new property meant another rung on
 * this record's telescope of constructors, another on {@code VanillaBuildings}' own, another field
 * read in the JSON loader and another line in the vanilla-as-data parity test, all before the
 * property did anything. A trait is one key and one parser; see {@link TraitKey}.
 *
 * <p>{@link #fluidInput()}/{@link #fluidOutput()}/{@link #power()} survive as named accessors over
 * that bag, because those names are public API a mod already calls and a name once shipped is a
 * promise. A mod's own trait gets no accessor here and needs none — it reads {@link #traits} with
 * its own key, which is exactly the extension point this replaced three components to gain.
 *
 * <p>WHERE the ports sit is not stored: a machine's own facing is the output side and the cell
 * behind it is the input, the same convention every directional building here already follows. A
 * per-side port map is a real feature (a machine with two inputs on different sides) but it would be
 * a second way to say what {@code direction} already says for the machines that exist today.
 */
public record BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
        ContentId texture, int footprintWidth, int footprintHeight, int bufferMax, int speedMultiplier,
        boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
        ContentId recipeKind, @Nullable ItemType fuelItem, Traits traits) {

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
     * Convenience for a prototype that declares no optional property at all — which, before fluids
     * and electricity, was every prototype in the game. There is exactly ONE rung here now: what
     * used to be a separate constructor per new property is a {@link Traits} bag, so the next
     * property adds no rung at all.
     */
    public BuildingPrototype(ContentId id, String label, BuildingCost cost, PlacementRule placementRule,
            ContentId texture, int footprintWidth, int footprintHeight, int bufferMax, int speedMultiplier,
            boolean acceptsSpeedEffects, BehaviorFactory behavior, RestoreFactory restoreBehavior, Codec<?> codec,
            ContentId recipeKind, @Nullable ItemType fuelItem) {
        this(id, label, cost, placementRule, texture, footprintWidth, footprintHeight, bufferMax, speedMultiplier,
                acceptsSpeedEffects, behavior, restoreBehavior, codec, recipeKind, fuelItem, Traits.NONE);
    }

    /**
     * Which fluid this building draws, or {@code null} for the vast majority that draw none.
     *
     * <p>A named door onto {@link VanillaTraits#FLUID_INPUT} rather than a component of its own: the
     * name is public API a mod already calls, so it stays, while what backs it moved into the trait
     * bag. A mod's OWN trait needs no accessor here — it reads {@link #traits} with its own key,
     * which is the whole reason the bag exists.
     */
    public @Nullable FluidType fluidInput() {
        return traits.get(VanillaTraits.FLUID_INPUT).orElse(null);
    }

    /** Which fluid this building produces — see {@link #fluidInput()} for why this is a door onto a trait. */
    public @Nullable FluidType fluidOutput() {
        return traits.get(VanillaTraits.FLUID_OUTPUT).orElse(null);
    }

    /** What this building has to do with electricity, or {@code null} for nothing at all — see {@link #fluidInput()}. */
    public @Nullable PowerSpec power() {
        return traits.get(VanillaTraits.POWER).orElse(null);
    }

    /**
     * Which technology, once unlocked, halves this building's own timing, or {@code null} for no
     * tech gate at all — see {@link #fluidInput()} for why this is a door onto a trait, and {@link
     * VanillaTraits#SPEED_TECH} for why an archetype-reusing JSON building answers this the same
     * way its archetype always has unless it names its own.
     */
    public @Nullable ContentId speedTech() {
        return traits.get(VanillaTraits.SPEED_TECH).orElse(null);
    }

    /** When this prototype unlocks in the build UI — empty means always available. */
    public Optional<VisibilityRule> visibleWhen() {
        return traits.get(VanillaTraits.VISIBLE_WHEN);
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
     *
     * <p>{@code state_version} is stamped only when {@link #stateVersion()} is greater than 1 —
     * writing it onto every vanilla save today would change the on-disk shape for a hop list nobody
     * can override yet ({@code BuildingPrototype} is a record; per-prototype version becomes a
     * real field in the same change that ships the first hop).
     */
    @SuppressWarnings("unchecked")
    public Object encodeState(Object liveState) {
        Object encoded = ((Codec<Object>) codec).encode(liveState);
        return stateVersion() > 1 ? stampStateVersion(encoded, stateVersion()) : encoded;
    }

    /**
     * The decoding counterpart to {@link #encodeState}: runs {@link StateMigration#apply} when the
     * encoded map carries an older {@code state_version} (or none — treated as version 1), then
     * hands the result to {@link #codec}. Same unchecked-cast reasoning as encode.
     */
    @SuppressWarnings("unchecked")
    public Object decodeState(Object rawState, Registry<ItemType> items) {
        return ((Codec<Object>) codec).decode(prepareEncodedState(rawState, stateVersion(), stateMigrations()),
                items);
    }

    /**
     * Current shape version of this prototype's encoded state. Stays at {@code 1} for every
     * shipped prototype until a real reshape lands hops — raising it without a matching record
     * component is impossible today (this type is a record), so the first reshape must add the
     * field and the hop list together.
     */
    public int stateVersion() {
        return 1;
    }

    /**
     * One-hop migrations from older encoded shapes up to {@link #stateVersion()}. Empty until the
     * first real reshape; {@link #decodeState} still walks through {@link StateMigration#apply} so
     * that reshape only fills this list.
     */
    public List<StateMigration> stateMigrations() {
        return List.of();
    }

    /**
     * Package-visible so a test can prove the load path applies hops without standing up a whole
     * custom prototype.
     */
    static Object prepareEncodedState(Object rawState, int stateVersion, List<StateMigration> migrations) {
        if (!(rawState instanceof Map<?, ?> raw)) {
            return rawState;
        }
        Map<String, Object> state = stringKeyedCopy(raw);
        if (state == null) {
            return rawState;
        }
        Object versionField = state.remove("state_version");
        // Absent field = version 1: every save written before a stamp existed.
        int fromVersion = versionField instanceof Number number ? number.intValue() : 1;
        if (fromVersion > stateVersion) {
            throw new IllegalStateException(
                    "encoded state_version " + fromVersion + " is newer than this prototype's "
                            + stateVersion + " — cannot load a future shape on an older engine");
        }
        return StateMigration.apply(state, fromVersion, stateVersion, migrations);
    }

    static Object stampStateVersion(Object encoded, int stateVersion) {
        if (!(encoded instanceof Map<?, ?> raw)) {
            return encoded;
        }
        Map<String, Object> withVersion = stringKeyedCopy(raw);
        if (withVersion == null) {
            return encoded;
        }
        withVersion.put("state_version", stateVersion);
        return withVersion;
    }

    /**
     * {@code null} when any key is not already a {@code String} — remapping through
     * {@code String.valueOf} would silently change a mod codec's key type on the in-memory
     * encode→decode path.
     */
    private static @Nullable Map<String, Object> stringKeyedCopy(Map<?, ?> raw) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            Object key = entry.getKey();
            if (!(key instanceof String stringKey)) {
                return null;
            }
            copy.put(stringKey, entry.getValue());
        }
        return copy;
    }
}

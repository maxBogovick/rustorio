package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.Tech;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Accepts an item from a neighbor, cooks it against whichever {@link Recipe} in its injected
 * {@link RecipeBook} matches, and pushes the result out in one fixed {@link #direction} chosen at
 * build time — never back out its own input side, which is what let a stalled downstream neighbor
 * jam an entire line silently in earlier versions of this building.
 *
 * <p>A furnace doesn't know its recipe until the first item arrives ({@link #accept}); once every
 * ingredient's buffer empties out it forgets again and is ready to retool for a different
 * material. One buffer slot per {@link Recipe#ingredients()} entry (not one shared counter),
 * sized the moment a recipe commits — {@link RecipeBook}'s {@code ENGINE} recipe needs one of each
 * ingredient before it can start, so spending the first one to arrive before its partner shows up
 * would waste it.
 *
 * <p><b>Owner decision (P2-02, BUG_FIX_PROGRESS.md):</b> option (C) — explicit player choice. Some
 * items are ambiguous: a {@code GEAR} is the first ingredient of {@code ENGINE} and the second
 * ingredient of {@code CHASSIS}. Silently committing to whichever recipe was declared first (the
 * old behavior) could commit a press to a recipe whose other ingredient never arrives, jamming it
 * forever with no way out but demolishing it. Now {@link #accept} only auto-commits when exactly
 * one recipe matches; an ambiguous item is refused (not consumed, not guessed at) until the player
 * calls {@link #cycleRecipe} to say which one they want.
 *
 * <p><b>Owner decision (D-05, DEV_TASKS.md):</b> originally only the vanilla {@code FURNACE} kind
 * burned {@link VanillaItems#COAL} — a {@code PRESS} is mechanical stamping, not a heat process, so
 * it has no physical reason to need fuel (§2.3 of the design audit only ever talks about smelting).
 * That's now data, not a hardcoded kind check: {@link BuildingPrototype#fuelItem()} names which
 * item (if any) THIS prototype burns — {@code COAL} for vanilla FURNACE, {@code null} (no fuel
 * requirement at all) for vanilla PRESS/ASSEMBLER, and whatever a JSON-defined custom archetype
 * asks for (see {@code BuildingJsonLoader}'s {@code "fuel"} field) — no Java needed to pick a
 * different fuel item, or none. {@link #fuelBuffer} is deliberately independent of the recipe's own
 * ingredient buffers: fuel is never itself a recipe ingredient (whatever item is named as fuel is
 * always intercepted before recipe-matching even runs — see {@link #accept}), it's a separate
 * precondition {@link #tick} checks before a batch may even start — the same "hold until
 * delivered" discipline, one more gate.
 *
 * <p>{@link BuildingPrototype#recipeKind()} is the other new prototype-driven knob (same feature):
 * which pool of {@link Recipe}s this furnace searches — defaults to the prototype's own {@link
 * BuildingPrototype#id()} (private, collision-free by construction) rather than always the shared
 * vanilla FURNACE/PRESS/ASSEMBLER pool a {@code kind} implied before. {@link #kind} itself
 * (the {@code BuildingType}) still only decides which of the three Java-level flavors this
 * instance reports via {@link #type()} and the {@code ASSEMBLER} single-sprite branch in {@link
 * #appearance()} — it no longer has anything to do with fuel or which recipes are reachable.
 */
public final class Furnace implements Building, RecipeSelectable {

    /** Same cap as {@link BuildingPrototype#bufferMax()} — fuel is stored the same way any other buffered input is. */
    private static final int FUEL_MAX = 5;

    /**
     * A committed recipe, its cooking timer, and one buffer slot per {@link Recipe#ingredients()}
     * entry — always set or cleared together (see {@link #accept}/{@link #tick}), never one
     * without the others. Grouping them into a single nullable field, instead of independent
     * nullable/uninitialized fields that happen to move in lockstep, makes "timer set but recipe
     * forgotten" (or a buffer array sized for the WRONG recipe) unrepresentable instead of just
     * unintended.
     */
    private record ActiveRecipe(Recipe recipe, ProcessTimer timer, int[] inputBuffers) {
    }

    private final BuildingType kind;
    private final Direction direction;
    private final RecipeBook recipeBook;
    /** Buffer size and speed multiplier — see {@link BuildingPrototype}'s own javadoc for why only this archetype reads them. */
    private final BuildingPrototype prototype;

    private @Nullable ActiveRecipe active;
    /**
     * The player's standing choice among {@link RecipeBook#forKind}, cycled via {@link
     * #cycleRecipe}. Consulted only while {@link #active} is {@code null} — once a batch commits,
     * this furnace runs it to completion regardless of what the player picks next. {@code null}
     * means "no preference": an unambiguous item still auto-commits, an ambiguous one is refused.
     * Persisted since F-03, DEV_TASKS.md (see {@link #state()}) — it used to not be.
     */
    private @Nullable Recipe selectedRecipe;
    /** How much of {@link BuildingPrototype#fuelItem()} is on hand — see the class javadoc's D-05 note. Always 0 and unused for a fuel-less prototype (vanilla PRESS/ASSEMBLER, or a custom archetype that skips {@code "fuel"}). */
    private int fuelBuffer;
    /**
     * A finished batch waiting for a downstream neighbor to accept it — independent of {@link
     * #active}: still holds after the last unit of a batch consumes {@code active} down to
     * nothing (see {@link #tick}), and a furnace can be mid-cooldown on a NEW recipe while an
     * older {@code pendingOutput} is still waiting to leave.
     */
    private @Nullable ItemType pendingOutput;
    /** Recomputed once per {@link #tick}, not once per render frame — see {@link BuildingStatus}'s own javadoc for why (F-01, DEV_TASKS.md). */
    private BuildingStatus status = BuildingStatus.WORKING;
    /** {@code UpgradeSpeedAction}'s upgrade count — see {@link #tick}'s own note on how it's applied. */
    private int speedLevel;

    /** Convenience for callers that only care about {@code kind}'s vanilla prototype — see the 4-arg constructor for real injection (a modded "steel furnace" needs its own prototype here). */
    public Furnace(BuildingType kind, Direction direction, RecipeBook recipeBook) {
        this(kind, direction, recipeBook, VanillaBuildings.frozen().get(VanillaBuildings.idFor(kind)));
    }

    public Furnace(BuildingType kind, Direction direction, RecipeBook recipeBook, BuildingPrototype prototype) {
        this.kind = kind;
        this.direction = direction;
        this.recipeBook = recipeBook;
        this.prototype = prototype;
    }

    /** Convenience restore constructor for callers that only care about the vanilla prototype set — see the 4-arg restore constructor for real injection. */
    Furnace(BuildingType kind, FurnaceState state, RecipeBook recipeBook) {
        this(kind, state, recipeBook, VanillaBuildings.frozen().get(VanillaBuildings.idFor(kind)));
    }

    /**
     * Restore constructor used by {@link BuildingFactory#restore} (via this prototype's own
     * registered {@link RestoreFactory}) — takes the decoded {@link FurnaceState} whole rather
     * than its fields spread across many parameters. {@code kind} is a separate parameter, not a
     * field of {@code state} itself — see {@link FurnaceState}'s own javadoc for why; the
     * registered restore lambda for each of the three furnace-kind prototypes supplies its own
     * captured kind, the same way the CREATE lambda already does.
     *
     * <p>Public (was package-private, back when this took the old sealed {@code BuildingMemento}
     * variant) — a mod that reuses {@link Furnace} for its own {@link BuildingPrototype} — no new
     * Java class needed, only new data (see {@code ExampleMod} in the test tree) — registers its
     * OWN {@code RestoreFactory} lambda, which lives outside this package and needs to call this
     * constructor directly.
     */
    public Furnace(BuildingType kind, FurnaceState state, RecipeBook recipeBook, BuildingPrototype prototype) {
        this(kind, state.direction(), recipeBook, prototype);
        this.speedLevel = state.speedLevel();
        this.fuelBuffer = state.fuelBuffer();
        ItemType recipeOutput = state.recipeOutput();
        if (recipeOutput != null) {
            // prototype.recipeKind(), not kind: a restored custom prototype's own private pool
            // isn't necessarily the shared vanilla FURNACE/PRESS/ASSEMBLER one kind alone names.
            Recipe recipe = recipeBook.findByOutput(prototype.recipeKind(), recipeOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + recipeOutput));
            // A non-positive cooldown means "no countdown was recorded", NOT "this batch is done"
            // (N10, NEW_BUGS_PROGRESS.md): ProcessTimer decrements before testing, so a timer
            // restored at 0 reports a finished batch on its very first tick — a whole recipe's work
            // for free. A live furnace never writes a 0 here (the countdown always resets to at
            // least 1), so this only guards a hand-edited or corrupted save; start a full batch.
            int cooldown = state.cooldown() > 0 ? state.cooldown() : recipe.time();
            int[] buffers = state.buffers().stream().mapToInt(Integer::intValue).toArray();
            this.active = new ActiveRecipe(recipe, new ProcessTimer(cooldown), buffers);
        }
        this.pendingOutput = state.pendingOutput();
        ItemType selectedOutput = state.selectedRecipeOutput();
        if (selectedOutput != null) {
            this.selectedRecipe = recipeBook.findByOutput(prototype.recipeKind(), selectedOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + selectedOutput));
        }
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        // item.equals(null) is a safe false (records never equal null) — a fuel-less prototype
        // simply never intercepts anything here, every item falls straight through to recipe
        // matching below, exactly like PRESS/ASSEMBLER always did.
        if (item.equals(prototype.fuelItem())) {
            if (fuelBuffer >= FUEL_MAX) {
                return false;
            }
            fuelBuffer++;
            return true;
        }
        ActiveRecipe current = active;
        if (current == null) {
            Recipe recipe = pickRecipe(item);
            if (recipe == null) {
                return false;
            }
            current = new ActiveRecipe(recipe, new ProcessTimer(effectiveTime(recipe, world)),
                    new int[recipe.ingredients().size()]);
            active = current;
        }
        List<ItemType> ingredients = current.recipe().ingredients();
        int[] buffers = current.inputBuffers();
        int max = effectiveBufferMax(world);
        // Two (or more) ingredient slots can be the SAME item (N12, NEW_BUGS_PROGRESS.md) — fill
        // whichever matching slot is emptiest instead of always the first match. Always filling
        // the first slot piled every unit into it while the others stayed at 0, and tick() never
        // starts a batch without EVERY ingredient: a press jammed forever on a full input buffer.
        // No standard recipe looks like this today, but an injected RecipeBook (a mod, a test
        // fixture) may define one and nothing forbids it.
        int bestSlot = -1;
        for (int i = 0; i < ingredients.size(); i++) {
            if (ingredients.get(i).equals(item) && buffers[i] < max
                    && (bestSlot == -1 || buffers[i] < buffers[bestSlot])) {
                bestSlot = i;
            }
        }
        if (bestSlot == -1) {
            return false;
        }
        buffers[bestSlot]++;
        return true;
    }

    /**
     * Which recipe {@code item} commits this furnace to, if any: the player's {@link
     * #selectedRecipe} when it's actually one of the candidates, the sole candidate when there's
     * exactly one, or {@code null} — refuse, don't guess — when there's more than one and the
     * player hasn't picked.
     */
    private @Nullable Recipe pickRecipe(ItemType item) {
        List<Recipe> candidates = recipeBook.findAll(prototype.recipeKind(), item);
        if (selectedRecipe != null && candidates.contains(selectedRecipe)) {
            return selectedRecipe;
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * Advance {@link #selectedRecipe} to the next candidate in this prototype's own {@link
     * BuildingPrototype#recipeKind()} pool (wrapping back to "no preference"). The player's remedy
     * for an ambiguous item {@link #accept} just refused — see the class javadoc's P2-02 note.
     *
     * @return the newly selected recipe's output, or empty for "no preference"
     */
    public Optional<ItemType> cycleRecipe() {
        List<Recipe> options = recipeBook.forKind(prototype.recipeKind());
        int next = selectedRecipe == null ? 0 : options.indexOf(selectedRecipe) + 1;
        selectedRecipe = next < options.size() ? options.get(next) : null;
        return Optional.ofNullable(selectedRecipe).map(Recipe::output);
    }

    /**
     * Jump {@link #selectedRecipe} straight to {@code recipe} — the click-a-row counterpart to
     * {@link #cycleRecipe}'s step-by-step version (inspection panel recipe picker). {@code recipe}
     * must be one of {@link #possibleRecipes} for this furnace's own {@link #kind} — the only way a
     * caller could have gotten a {@link Recipe} reference to pass here in the first place, since
     * that's the exact list any picker UI is drawn from.
     */
    @Override
    public void selectRecipe(Recipe recipe) {
        if (!possibleRecipes().contains(recipe)) {
            throw new IllegalArgumentException(
                    "Recipe " + recipe + " is not one this prototype's own kind (" + prototype.recipeKind() + ") can run");
        }
        selectedRecipe = recipe;
    }

    /**
     * Runs {@link #tickOnce} {@code 1 << speedLevel} times — the same multiplier {@code
     * SpeedModule} used to produce by nesting {@code speedLevel} independent wrapper layers, each
     * doubling whatever it wrapped (owner decision: preserve the exact ×2^N stacking, not switch to
     * a linear ×(1+N) just because the mechanism moved from a decorator to a field).
     */
    @Override
    public void tick(TickContext world, int x, int y) {
        for (int i = 0, repeats = 1 << speedLevel; i < repeats; i++) {
            tickOnce(world, x, y);
        }
    }

    private void tickOnce(TickContext world, int x, int y) {
        if (pendingOutput == null) {
            ActiveRecipe current = active;
            if (current == null) {
                status = BuildingStatus.NO_INPUT; // nothing committed yet (F-01, DEV_TASKS.md)
                return;
            }
            int[] buffers = current.inputBuffers();
            // The FIRST ingredient's slot is checked before fuel, deliberately — the rest are
            // checked after (see below): both branches report the same NO_INPUT status either
            // way, but this keeps the priority a live furnace already had before ingredients
            // became a list — a furnace with nothing at all buffered reports NO_INPUT even if it
            // also happens to be out of fuel, rather than NO_FUEL.
            if (buffers[0] == 0) {
                status = BuildingStatus.NO_INPUT;
                return;
            }
            // A prototype with a fuelItem needs some on hand to even start a batch (D-05,
            // DEV_TASKS.md); one without (vanilla PRESS/ASSEMBLER, or a fuel-less custom
            // archetype) has no fuel concept at all, so this is trivially true for it — see the
            // class javadoc.
            boolean fuelReady = prototype.fuelItem() == null || fuelBuffer > 0;
            if (!fuelReady) {
                status = BuildingStatus.NO_FUEL;
                return;
            }
            boolean everyIngredientReady = true;
            for (int i = 1; i < buffers.length; i++) {
                if (buffers[i] == 0) {
                    everyIngredientReady = false;
                    break;
                }
            }
            if (!everyIngredientReady) {
                status = BuildingStatus.NO_INPUT; // has SOME ingredients, still waiting on the rest
                return;
            }
            Recipe recipe = current.recipe();
            if (!current.timer().tick(effectiveTime(recipe, world))) {
                status = BuildingStatus.WORKING; // actively cooking, just not done this tick
                return;
            }
            for (int i = 0; i < buffers.length; i++) {
                buffers[i]--;
            }
            if (prototype.fuelItem() != null) {
                fuelBuffer--;
            }
            pendingOutput = recipe.output();
            world.notifyProduced(pendingOutput);
            boolean everyBufferEmpty = true;
            for (int count : buffers) {
                if (count > 0) {
                    everyBufferEmpty = false;
                    break;
                }
            }
            if (everyBufferEmpty) {
                active = null;
            }
        }
        if (world.offerForward(outputX(x, direction), outputY(y, direction), pendingOutput)) {
            pendingOutput = null;
            status = BuildingStatus.WORKING;
        } else {
            status = BuildingStatus.OUTPUT_FULL;
        }
    }

    /**
     * Where {@link #tick} pushes a finished item — {@code x + direction.dx()} for a 1x1 building
     * (every kind except {@code ASSEMBLER}), but that formula alone would push output INTO the
     * building's own footprint for a 2x2 {@code ASSEMBLER}: e.g. facing {@link Direction#RIGHT}
     * from the anchor {@code (x, y)} would land on {@code (x + 1, y)}, still this same building's
     * own top-right cell. Generalizes to "one past the far edge of the footprint" on the axis
     * {@code direction} actually moves along, and reduces to the exact old formula whenever {@link
     * #footprintWidth}/{@link #footprintHeight} are both {@code 1} (X-03, DEV_TASKS.md).
     */
    private int outputX(int x, Direction direction) {
        int dx = direction.dx();
        return dx > 0 ? x + footprintWidth() : dx < 0 ? x - 1 : x;
    }

    /** The {@code y} counterpart to {@link #outputX} — see its javadoc. */
    private int outputY(int y, Direction direction) {
        int dy = direction.dy();
        return dy > 0 ? y + footprintHeight() : dy < 0 ? y - 1 : y;
    }

    /**
     * {@code 2} for the vanilla {@code ASSEMBLER} prototype, {@code 1} for every other vanilla
     * kind — read from {@link #prototype}, not {@link #kind}: a JSON-configured building reusing
     * this archetype (see {@code BuildingJsonLoader}) supplies its OWN footprint on its own
     * prototype, which would be unreachable if this read the closed {@link BuildingType} constant
     * instead.
     */
    @Override
    public int footprintWidth() {
        return prototype.footprintWidth();
    }

    /** The height counterpart to {@link #footprintWidth} — see its javadoc. Not necessarily square anymore (a modded prototype may differ), but {@link #rotatedClockwise} still only reshapes facing, never the occupied cells. */
    @Override
    public int footprintHeight() {
        return prototype.footprintHeight();
    }

    /** {@code recipe.time()}, halved again by {@link #prototype}'s own {@code speedMultiplier} — the "twice as fast" a modded furnace variant asks for stacks with, not instead of, the {@code FAST_SMELTING} tech bonus. */
    private int effectiveTime(Recipe recipe, TickContext world) {
        int baseTime = Math.max(1, recipe.time() / prototype.speedMultiplier());
        return world.research().fasterIfUnlocked(Tech.FAST_SMELTING, baseTime);
    }

    private int effectiveBufferMax(TickContext world) {
        return world.research().biggerIfUnlocked(Tech.BIG_BUFFER, prototype.bufferMax());
    }

    /** Sum of every ingredient's buffered count (0 with no recipe committed yet) — shown as the furnace's badge; the method name predates recipes taking more than one ingredient. */
    public int oreBuffer() {
        ActiveRecipe current = active;
        if (current == null) {
            return 0;
        }
        int total = 0;
        for (int count : current.inputBuffers()) {
            total += count;
        }
        return total;
    }

    /**
     * The recipe actively cooking (or buffered, waiting to start), if any — the FULL {@link
     * Recipe} (inputs AND output), not just {@link Appearance#recipeHint}'s output-only summary.
     * For the inspection panel (live bug report: "не понятно что вход" — the old panel showed only
     * the output item, never what to feed it).
     */
    public Optional<Recipe> activeRecipe() {
        ActiveRecipe current = active;
        return current == null ? Optional.empty() : Optional.of(current.recipe());
    }

    /** The player's standing preference (see {@link #cycleRecipe}) — consulted only while {@link #activeRecipe} is empty. */
    @Override
    public Optional<Recipe> selectedRecipeChoice() {
        return Optional.ofNullable(selectedRecipe);
    }

    /** Every recipe this furnace's own {@link BuildingPrototype#recipeKind()} pool can run at all — "what could this produce" when nothing is committed yet. */
    @Override
    public List<Recipe> possibleRecipes() {
        return recipeBook.forKind(prototype.recipeKind());
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    /**
     * Rebuilds through {@link #state()} rather than a dedicated copy constructor: {@link
     * FurnaceState} already carries every field this class has, so replaying it through the
     * existing restore constructor with only {@code direction} swapped is the whole
     * implementation, instead of a second parameter list to keep in sync with the first.
     *
     * <p>{@link #status} isn't part of {@link FurnaceState} (ephemeral, recomputed on the next
     * {@link #tick} — same call made for every other building's status), so the restore
     * constructor alone would reset a rotated furnace back to {@code WORKING}. Unlike an actual
     * save/load, a rotation doesn't create a new logical furnace — set explicitly here (code review
     * finding) so a furnace that was actually blocked doesn't flash back to {@code WORKING} for one
     * tick just because the player rotated it; {@code HudRenderer.alerts()} reads {@code
     * World.statusCounts()} directly now, so a stale reset here is directly visible on the HUD.
     */
    @Override
    public Optional<Building> rotatedClockwise() {
        FurnaceState state = state();
        FurnaceState rotated = new FurnaceState(
                direction.rotate(), state.buffers(),
                state.cooldown(), state.recipeOutput(), state.pendingOutput(), state.fuelBuffer(),
                state.selectedRecipeOutput(), state.speedLevel());
        // THIS instance's own prototype passed through directly, not re-resolved from a
        // registry-default lookup — a rotation must keep exactly the prototype this furnace was
        // already built with, not silently fall back to the vanilla default if it's running a
        // modded one.
        Furnace turned = new Furnace(kind, rotated, recipeBook, prototype);
        turned.status = status;
        return Optional.of(turned);
    }

    @Override
    public int speedLevel() {
        return speedLevel;
    }

    /**
     * Rebuilds through {@link #state()}, same approach as {@link #rotatedClockwise} — see its
     * own javadoc for why a copy constructor beats a dedicated one here.
     */
    @Override
    public Building withSpeedLevel(int newSpeedLevel) {
        FurnaceState current = state();
        FurnaceState updated = new FurnaceState(
                current.direction(), current.buffers(), current.cooldown(), current.recipeOutput(),
                current.pendingOutput(), current.fuelBuffer(), current.selectedRecipeOutput(), newSpeedLevel);
        Furnace copy = new Furnace(kind, updated, recipeBook, prototype);
        copy.status = status;
        return copy;
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(pendingOutput);
    }

    @Override
    public Appearance appearance() {
        // Committed recipe wins over the standing preference — once a batch is running, THAT'S
        // what's actually cooking; selectedRecipe only matters again once active clears (F-03,
        // DEV_TASKS.md: "выбранный рецепт... виден на экране, не только в панели").
        ActiveRecipe current = active;
        ItemType recipeHint = current != null ? current.recipe().output()
                : selectedRecipe != null ? selectedRecipe.output() : null;
        int buffered = oreBuffer();
        // ASSEMBLER (X-03, DEV_TASKS.md) has exactly one drawn sprite — there's no second assembler
        // sprite to distinguish "actively cooking" from "idle" with — so it always draws whatever
        // this prototype's own texture is, badge shown unconditionally.
        if (kind == BuildingType.ASSEMBLER) {
            return Appearance.of(prototype.texture(), buffered, status, recipeHint);
        }
        // FURNACE/PRESS ship a matching hot/cold sprite pair (VanillaBuildings) — but a mod
        // reusing this archetype (BuildingJsonLoader, e.g. a modded furnace) supplies only ONE
        // texture, so the hot swap only applies while this prototype is still running the vanilla
        // cold texture; anything else is drawn as-is regardless of buffered state, since there's no
        // second sprite for it to swap to.
        ContentId texture = prototype.texture();
        ContentId sprite = buffered > 0 && texture.equals(VanillaSprites.FURNACE_COLD)
                ? VanillaSprites.FURNACE_HOT
                : texture;
        return buffered > 0
                ? Appearance.of(sprite, buffered, status, recipeHint)
                : Appearance.of(sprite, status, recipeHint);
    }

    @Override
    public BuildingType type() {
        return kind;
    }

    /** Not {@code prototype.id()} of the vanilla default for {@link #kind} — the exact prototype this instance was actually built/restored with, modded or not. */
    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    /** No {@code kind} here — see {@link FurnaceState}'s own javadoc for why it's redundant once the save envelope names the governing prototype directly (which archetype-specific restore lambda gets called already implies it). */
    @Override
    public FurnaceState state() {
        ActiveRecipe current = active;
        List<Integer> buffers = current == null
                ? List.of()
                : Arrays.stream(current.inputBuffers()).boxed().toList();
        return new FurnaceState(
                direction, buffers,
                current == null ? 0 : current.timer().cooldown(),
                current == null ? null : current.recipe().output(),
                pendingOutput, fuelBuffer,
                selectedRecipe == null ? null : selectedRecipe.output(),
                speedLevel);
    }

    /** How much of {@link BuildingPrototype#fuelItem()} is on hand — always 0 for a fuel-less prototype. For the inspection panel (F-03), later. */
    public int fuelBuffer() {
        return fuelBuffer;
    }
}

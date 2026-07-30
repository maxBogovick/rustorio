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
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Accepts an item from a neighbor, cooks it against whichever {@link Recipe} in its injected
 * {@link RecipeBook} matches, and pushes the result out in one fixed {@link #direction} chosen at
 * build time — never back out its own input side, which is what let a stalled downstream neighbor
 * jam an entire line silently in earlier versions of this building.
 *
 * <p>A furnace doesn't know its recipe until the first item arrives ({@link #accept}); once both
 * buffers empty out it forgets again and is ready to retool for a different material. {@code
 * bufferA}/{@code bufferB} are separate (not one shared counter) because {@link RecipeBook}'s
 * {@code ENGINE} recipe needs one of each ingredient before it can start — spending the first one
 * to arrive before its partner shows up would waste it.
 *
 * <p><b>Owner decision (P2-02, BUG_FIX_PROGRESS.md):</b> option (C) — explicit player choice. Some
 * items are ambiguous: a {@code GEAR} is the first ingredient of {@code ENGINE} and the second
 * ingredient of {@code CHASSIS}. Silently committing to whichever recipe was declared first (the
 * old behavior) could commit a press to a recipe whose other ingredient never arrives, jamming it
 * forever with no way out but demolishing it. Now {@link #accept} only auto-commits when exactly
 * one recipe matches; an ambiguous item is refused (not consumed, not guessed at) until the player
 * calls {@link #cycleRecipe} to say which one they want.
 *
 * <p><b>Owner decision (D-05, DEV_TASKS.md):</b> only the {@code FURNACE} kind burns {@link
 * VanillaItems#COAL} — a {@code PRESS} is mechanical stamping, not a heat process, so it has no physical
 * reason to need fuel (§2.3 of the design audit only ever talks about smelting, and the card's own
 * acceptance criterion says "печь", furnace specifically, not press). {@link #fuelBuffer} is
 * deliberately independent of {@link #bufferA}/{@code bufferB}: fuel isn't a recipe ingredient (no
 * {@link Recipe} lists {@code COAL} as an input), it's a separate precondition {@link #tick} checks
 * before a {@code FURNACE} may even start a batch — the same "hold until delivered" discipline, one
 * more gate.
 */
public final class Furnace implements Building {

    /** Same cap as {@link BuildingPrototype#bufferMax()} — fuel is stored the same way any other buffered input is. */
    private static final int FUEL_MAX = 5;

    /**
     * A committed recipe and its cooking timer — always set or cleared together (see {@link
     * #accept}/{@link #tick}), never one without the other. Grouping them into a single nullable
     * field, instead of two independent nullable fields that happen to move in lockstep, makes
     * "timer set but recipe forgotten" (or vice versa) unrepresentable instead of just unintended.
     */
    private record ActiveRecipe(Recipe recipe, ProcessTimer timer) {
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
     * Persisted since F-03, DEV_TASKS.md (see {@link #memento()}) — it used to not be.
     */
    private @Nullable Recipe selectedRecipe;
    private int bufferA;
    private int bufferB;
    /** Coal on hand, {@code FURNACE} kind only — see the class javadoc's D-05 note. Always 0 and unused for {@code PRESS}. */
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

    /**
     * Convenience restore constructor used by {@link BuildingFactory#restore} for callers that
     * only care about the vanilla prototype set — resolves {@link
     * BuildingMemento.FurnaceState#prototypeId()} against {@link VanillaBuildings#frozen()}, or
     * falls back to {@code state.kind()}'s vanilla default when it's {@code null} (a save written
     * before this field existed). See the 3-arg restore constructor for real injection.
     */
    Furnace(BuildingMemento.FurnaceState state, RecipeBook recipeBook) {
        this(state, recipeBook, resolvePrototype(state));
    }

    private static BuildingPrototype resolvePrototype(BuildingMemento.FurnaceState state) {
        ContentId id = state.prototypeId() != null ? state.prototypeId() : VanillaBuildings.idFor(state.kind());
        return VanillaBuildings.frozen().get(id);
    }

    /**
     * Package-private restore constructor used by {@link BuildingFactory#restore} — takes the
     * captured {@link BuildingMemento.FurnaceState} whole rather than its fields spread across
     * many parameters, so there's one grouped state object to read instead of a long,
     * easy-to-transpose parameter list.
     */
    Furnace(BuildingMemento.FurnaceState state, RecipeBook recipeBook, BuildingPrototype prototype) {
        this(state.kind(), state.direction(), recipeBook, prototype);
        this.bufferA = state.bufferA();
        this.bufferB = state.bufferB();
        this.fuelBuffer = state.fuelBuffer();
        ItemType recipeOutput = state.recipeOutput();
        if (recipeOutput != null) {
            Recipe recipe = recipeBook.findByOutput(state.kind(), recipeOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + recipeOutput));
            // A non-positive cooldown means "no countdown was recorded", NOT "this batch is done"
            // (N10, NEW_BUGS_PROGRESS.md): ProcessTimer decrements before testing, so a timer
            // restored at 0 reports a finished batch on its very first tick — a whole recipe's work
            // for free. A live furnace never writes a 0 here (the countdown always resets to at
            // least 1), so this only guards a hand-edited or corrupted save; start a full batch.
            int cooldown = state.cooldown() > 0 ? state.cooldown() : recipe.time();
            this.active = new ActiveRecipe(recipe, new ProcessTimer(cooldown));
        }
        this.pendingOutput = state.pendingOutput();
        ItemType selectedOutput = state.selectedRecipeOutput();
        if (selectedOutput != null) {
            this.selectedRecipe = recipeBook.findByOutput(state.kind(), selectedOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + selectedOutput));
        }
    }

    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (item.equals(VanillaItems.COAL)) {
            if (kind != BuildingType.FURNACE || fuelBuffer >= FUEL_MAX) {
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
            current = new ActiveRecipe(recipe, new ProcessTimer(effectiveTime(recipe, world)));
            active = current;
        }
        Recipe recipe = current.recipe();
        int max = effectiveBufferMax(world);
        boolean fitsA = item.equals(recipe.input()) && bufferA < max;
        boolean fitsB = recipe.hasSecondInput() && item.equals(recipe.input2()) && bufferB < max;
        // A recipe whose two ingredients are the same item fits BOTH buffers (N12,
        // NEW_BUGS_PROGRESS.md) — fill the emptier one instead of taking the first match and
        // returning. Matching A first, as this used to, piled every unit into bufferA while
        // bufferB stayed at 0, and tick() never starts a batch without the second ingredient: a
        // press jammed forever on a full input buffer. No standard recipe looks like this today,
        // but an injected RecipeBook (a mod, a test fixture) may define one and nothing forbids it.
        if (fitsA && fitsB) {
            if (bufferA <= bufferB) {
                bufferA++;
            } else {
                bufferB++;
            }
            return true;
        }
        if (fitsA) {
            bufferA++;
            return true;
        }
        if (fitsB) {
            bufferB++;
            return true;
        }
        return false;
    }

    /**
     * Which recipe {@code item} commits this furnace to, if any: the player's {@link
     * #selectedRecipe} when it's actually one of the candidates, the sole candidate when there's
     * exactly one, or {@code null} — refuse, don't guess — when there's more than one and the
     * player hasn't picked.
     */
    private @Nullable Recipe pickRecipe(ItemType item) {
        List<Recipe> candidates = recipeBook.findAll(kind, item);
        if (selectedRecipe != null && candidates.contains(selectedRecipe)) {
            return selectedRecipe;
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    /**
     * Advance {@link #selectedRecipe} to the next candidate for this furnace's {@link #kind}
     * (wrapping back to "no preference"). The player's remedy for an ambiguous item {@link
     * #accept} just refused — see the class javadoc's P2-02 note.
     *
     * @return the newly selected recipe's output, or empty for "no preference"
     */
    public Optional<ItemType> cycleRecipe() {
        List<Recipe> options = recipeBook.forKind(kind);
        int next = selectedRecipe == null ? 0 : options.indexOf(selectedRecipe) + 1;
        selectedRecipe = next < options.size() ? options.get(next) : null;
        return Optional.ofNullable(selectedRecipe).map(Recipe::output);
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (pendingOutput == null) {
            ActiveRecipe current = active;
            boolean secondInputReady = current == null || !current.recipe().hasSecondInput() || bufferB > 0;
            // FURNACE needs coal on hand to even start a batch (D-05, DEV_TASKS.md); PRESS has no
            // fuel concept at all, so this is trivially true for it — see the class javadoc.
            boolean fuelReady = kind != BuildingType.FURNACE || fuelBuffer > 0;
            if (bufferA == 0 || current == null) {
                status = BuildingStatus.NO_INPUT; // nothing buffered at all yet (F-01, DEV_TASKS.md)
                return;
            }
            if (!fuelReady) {
                status = BuildingStatus.NO_FUEL;
                return;
            }
            if (!secondInputReady) {
                status = BuildingStatus.NO_INPUT; // has the first ingredient, still waiting on the second
                return;
            }
            Recipe recipe = current.recipe();
            if (!current.timer().tick(effectiveTime(recipe, world))) {
                status = BuildingStatus.WORKING; // actively cooking, just not done this tick
                return;
            }
            bufferA--;
            if (recipe.hasSecondInput()) {
                bufferB--;
            }
            if (kind == BuildingType.FURNACE) {
                fuelBuffer--;
            }
            pendingOutput = recipe.output();
            world.notifyProduced(pendingOutput);
            if (bufferA == 0 && (!recipe.hasSecondInput() || bufferB == 0)) {
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

    /** {@code 2} for {@code ASSEMBLER} (X-03, DEV_TASKS.md); {@code 1} for every other kind — delegates to {@link BuildingType#footprintWidth}, the single source of truth. */
    @Override
    public int footprintWidth() {
        return kind.footprintWidth();
    }

    /** Square footprint (see {@link #footprintWidth}) — kept equal so {@link #rotatedClockwise} never has to reshape the occupied cells, only the facing. */
    @Override
    public int footprintHeight() {
        return kind.footprintHeight();
    }

    /** {@code recipe.time()}, halved again by {@link #prototype}'s own {@code speedMultiplier} — the "twice as fast" a modded furnace variant asks for stacks with, not instead of, the {@code FAST_SMELTING} tech bonus. */
    private int effectiveTime(Recipe recipe, TickContext world) {
        int baseTime = Math.max(1, recipe.time() / prototype.speedMultiplier());
        return world.research().fasterIfUnlocked(Tech.FAST_SMELTING, baseTime);
    }

    private int effectiveBufferMax(TickContext world) {
        return world.research().biggerIfUnlocked(Tech.BIG_BUFFER, prototype.bufferMax());
    }

    /** First-input buffer count — shown as the furnace's badge. */
    public int oreBuffer() {
        return bufferA;
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
    public Optional<Recipe> selectedRecipeChoice() {
        return Optional.ofNullable(selectedRecipe);
    }

    /** Every recipe this furnace's {@link #kind} can run at all — "what could this produce" when nothing is committed yet. */
    public List<Recipe> possibleRecipes() {
        return recipeBook.forKind(kind);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    /**
     * Rebuilds through {@link #memento()} rather than a dedicated copy constructor: {@link
     * BuildingMemento.FurnaceState} already carries every field this class has, so replaying it
     * through the existing restore constructor with only {@code direction} swapped is the whole
     * implementation, instead of a second parameter list to keep in sync with the first.
     *
     * <p>{@link #status} isn't part of {@link BuildingMemento.FurnaceState} (ephemeral, recomputed
     * on the next {@link #tick} — same call made for every other building's status), so the restore
     * constructor alone would reset a rotated furnace back to {@code WORKING}. Unlike an actual
     * save/load, a rotation doesn't create a new logical furnace — set explicitly here (code review
     * finding) so a furnace that was actually blocked doesn't flash back to {@code WORKING} for one
     * tick just because the player rotated it; {@code HudRenderer.alerts()} reads {@code
     * World.statusCounts()} directly now, so a stale reset here is directly visible on the HUD.
     */
    @Override
    public Optional<Building> rotatedClockwise() {
        BuildingMemento.FurnaceState state = (BuildingMemento.FurnaceState) memento();
        BuildingMemento.FurnaceState rotated = new BuildingMemento.FurnaceState(
                state.kind(), direction.rotate(), state.bufferA(), state.bufferB(),
                state.cooldown(), state.recipeOutput(), state.pendingOutput(), state.fuelBuffer(),
                state.selectedRecipeOutput(), state.prototypeId());
        // The 3-arg restore constructor, with THIS instance's own prototype passed through
        // directly — not re-resolved from state.prototypeId() via VanillaBuildings.frozen() (the
        // 2-arg convenience) — a rotation must keep exactly the registry this furnace was already
        // built with, not silently fall back to the vanilla default if it's running a modded one.
        Furnace turned = new Furnace(rotated, recipeBook, prototype);
        turned.status = status;
        return Optional.of(turned);
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
        // ASSEMBLER (X-03, DEV_TASKS.md) has exactly one drawn sprite — resources/assembler.png,
        // via VanillaSprites.ASSEMBLER — unlike FURNACE/PRESS's hot/cold pair, since there's no second
        // assembler sprite to distinguish "actively cooking" from "idle" with.
        if (kind == BuildingType.ASSEMBLER) {
            return Appearance.of(VanillaSprites.ASSEMBLER, bufferA, status, recipeHint);
        }
        return bufferA > 0
                ? Appearance.of(VanillaSprites.FURNACE_HOT, bufferA, status, recipeHint)
                : Appearance.of(VanillaSprites.FURNACE_COLD, status, recipeHint);
    }

    @Override
    public BuildingType type() {
        return kind;
    }

    @Override
    public BuildingMemento memento() {
        ActiveRecipe current = active;
        return new BuildingMemento.FurnaceState(
                kind, direction, bufferA, bufferB,
                current == null ? 0 : current.timer().cooldown(),
                current == null ? null : current.recipe().output(),
                pendingOutput, fuelBuffer,
                selectedRecipe == null ? null : selectedRecipe.output(),
                prototype.id());
    }

    /** Coal on hand — {@code FURNACE} kind only; always 0 for {@code PRESS}. For the inspection panel (F-03), later. */
    public int fuelBuffer() {
        return fuelBuffer;
    }
}

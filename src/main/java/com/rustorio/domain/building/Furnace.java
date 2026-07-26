package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Sprite;
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
 */
public final class Furnace implements Building {

    private static final int BUFFER_MAX = 5;

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

    private @Nullable ActiveRecipe active;
    /**
     * The player's standing choice among {@link RecipeBook#forKind}, cycled via {@link
     * #cycleRecipe}. Consulted only while {@link #active} is {@code null} — once a batch commits,
     * this furnace runs it to completion regardless of what the player picks next. {@code null}
     * means "no preference": an unambiguous item still auto-commits, an ambiguous one is refused.
     * Not persisted (see {@link #memento()}) — same known compromise as {@code Splitter}'s {@code
     * SortRule}.
     */
    private @Nullable Recipe selectedRecipe;
    private int bufferA;
    private int bufferB;
    /**
     * A finished batch waiting for a downstream neighbor to accept it — independent of {@link
     * #active}: still holds after the last unit of a batch consumes {@code active} down to
     * nothing (see {@link #tick}), and a furnace can be mid-cooldown on a NEW recipe while an
     * older {@code pendingOutput} is still waiting to leave.
     */
    private @Nullable Item pendingOutput;

    public Furnace(BuildingType kind, Direction direction, RecipeBook recipeBook) {
        this.kind = kind;
        this.direction = direction;
        this.recipeBook = recipeBook;
    }

    /**
     * Package-private restore constructor used by {@link BuildingFactory#restore} — takes the
     * captured {@link BuildingMemento.FurnaceState} whole rather than its seven fields spread
     * across seven parameters, so there's one grouped state object to read instead of a long,
     * easy-to-transpose parameter list.
     */
    Furnace(BuildingMemento.FurnaceState state, RecipeBook recipeBook) {
        this(state.kind(), state.direction(), recipeBook);
        this.bufferA = state.bufferA();
        this.bufferB = state.bufferB();
        Item recipeOutput = state.recipeOutput();
        if (recipeOutput != null) {
            Recipe recipe = recipeBook.findByOutput(state.kind(), recipeOutput)
                    .orElseThrow(() -> new IllegalStateException("Unknown recipe output: " + recipeOutput));
            this.active = new ActiveRecipe(recipe, new ProcessTimer(state.cooldown()));
        }
        this.pendingOutput = state.pendingOutput();
    }

    @Override
    public boolean accept(TickContext world, Item item) {
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
        if (item == recipe.input() && bufferA < max) {
            bufferA++;
            return true;
        }
        if (recipe.hasSecondInput() && item == recipe.input2() && bufferB < max) {
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
    private @Nullable Recipe pickRecipe(Item item) {
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
    public Optional<Item> cycleRecipe() {
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
            if (bufferA == 0 || !secondInputReady || current == null) {
                return;
            }
            Recipe recipe = current.recipe();
            if (!current.timer().tick(effectiveTime(recipe, world))) {
                return;
            }
            bufferA--;
            if (recipe.hasSecondInput()) {
                bufferB--;
            }
            pendingOutput = recipe.output();
            world.notifyProduced(pendingOutput);
            if (bufferA == 0 && (!recipe.hasSecondInput() || bufferB == 0)) {
                active = null;
            }
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), pendingOutput)) {
            pendingOutput = null;
        }
    }

    private static int effectiveTime(Recipe recipe, TickContext world) {
        return world.research().fasterIfUnlocked(Tech.FAST_SMELTING, recipe.time());
    }

    private static int effectiveBufferMax(TickContext world) {
        return world.research().biggerIfUnlocked(Tech.BIG_BUFFER, BUFFER_MAX);
    }

    /** First-input buffer count — shown as the furnace's badge. */
    public int oreBuffer() {
        return bufferA;
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Item> heldItem() {
        return Optional.ofNullable(pendingOutput);
    }

    @Override
    public Appearance appearance() {
        return bufferA > 0
                ? Appearance.of(Sprite.FURNACE_HOT, bufferA)
                : Appearance.of(Sprite.FURNACE_COLD);
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
                pendingOutput);
    }
}

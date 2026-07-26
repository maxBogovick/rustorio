package com.rustorio.domain.action;

import com.rustorio.domain.world.World;
import java.util.BitSet;
import java.util.List;

/**
 * Composite pattern: several actions applied and undone as one. Nothing new was needed to make
 * this work — {@code ActionHistory} doesn't know this class exists; to it, this is just another
 * {@code apply}/{@code undo} pair. A well-placed pattern pays for the next feature for free.
 */
public final class CompositeAction implements PlayerAction {

    private final List<PlayerAction> actions;
    private final BitSet applied;

    public CompositeAction(List<PlayerAction> actions) {
        this.actions = List.copyOf(actions);
        this.applied = new BitSet(this.actions.size());
    }

    @Override
    public boolean apply(World world) {
        applied.clear();
        for (int i = 0; i < actions.size(); i++) {
            if (actions.get(i).apply(world)) {
                applied.set(i);
            }
        }
        return !applied.isEmpty();
    }

    @Override
    public void undo(World world) {
        // Per the PlayerAction contract, undo must undo exactly what apply did. Some children may
        // have failed to apply (e.g. a cell was occupied); undoing them anyway would act on state
        // they never touched — see P1-01 in BUG_FIX_PROGRESS.md.
        //
        // Reverse order — like a stack of plates: the last one placed comes off first. Order
        // doesn't matter for independent placements, but this is the honest general case.
        for (int i = actions.size() - 1; i >= 0; i--) {
            if (applied.get(i)) {
                actions.get(i).undo(world);
            }
        }
    }
}

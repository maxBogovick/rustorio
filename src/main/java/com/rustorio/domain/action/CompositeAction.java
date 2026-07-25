package com.rustorio.domain.action;

import com.rustorio.domain.world.World;
import java.util.List;

/**
 * Composite pattern: several actions applied and undone as one. Nothing new was needed to make
 * this work — {@code ActionHistory} doesn't know this class exists; to it, this is just another
 * {@code apply}/{@code undo} pair. A well-placed pattern pays for the next feature for free.
 */
public final class CompositeAction implements PlayerAction {

    private final List<PlayerAction> actions;

    public CompositeAction(List<PlayerAction> actions) {
        this.actions = actions;
    }

    @Override
    public boolean apply(World world) {
        boolean any = false;
        for (PlayerAction action : actions) {
            if (action.apply(world)) {
                any = true;
            }
        }
        return any;
    }

    @Override
    public void undo(World world) {
        // Reverse order — like a stack of plates: the last one placed comes off first. Order
        // doesn't matter for independent placements, but this is the honest general case.
        for (int i = actions.size() - 1; i >= 0; i--) {
            actions.get(i).undo(world);
        }
    }
}

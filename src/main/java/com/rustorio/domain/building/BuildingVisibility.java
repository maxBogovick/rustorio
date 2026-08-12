package com.rustorio.domain.building;

import com.rustorio.domain.VisibilityContext;

/** Whether a prototype is unlocked for the HUD build strip and build menu interaction. */
public final class BuildingVisibility {

    private BuildingVisibility() {
    }

    public static boolean isAvailable(BuildingPrototype prototype, VisibilityContext context) {
        return prototype.visibleWhen().map(rule -> rule.satisfied(context)).orElse(true);
    }
}

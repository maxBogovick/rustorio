package com.rustorio.domain;

import java.util.Set;

/**
 * Read-only face of {@link Research} — every query, none of the mutators. {@code World.research()}
 * returns this, not {@code Research} itself: holding a {@code ResearchView} makes {@link
 * Research#addPoints}/{@link Research#clear}/{@link Research#restore} uncallable at the type
 * level, so nothing outside {@code World} (an HUD panel, a future plugin) can grant itself free
 * research points or wipe progress just because it has a {@code World} reference in hand.
 */
public interface ResearchView {

    int points();

    Set<Tech> unlocked();

    boolean isUnlocked(Tech tech);

    int fasterIfUnlocked(Tech tech, int baseTime);

    int biggerIfUnlocked(Tech tech, int baseCapacity);

    Research.Snapshot snapshot();
}

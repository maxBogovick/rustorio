package com.rustorio.domain;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import java.util.Set;

/**
 * Read-only face of {@link Research} — every query, none of the mutators. {@code World.research()}
 * returns this, not {@code Research} itself: holding a {@code ResearchView} makes {@link
 * Research#addPoints}/{@link Research#clear}/{@link Research#restore} uncallable at the type
 * level, so nothing outside {@code World} (an HUD panel, a mod) can grant itself free research
 * points or wipe progress just because it has a {@code World} reference in hand.
 */
public interface ResearchView {

    /**
     * Every technology this game knows about. The tech-tree panel iterates THIS rather than a
     * fixed list of built-in constants — a panel walking the vanilla set would silently omit a
     * mod's own technology, and no test would notice.
     */
    Registry<TechType> techs();

    int points();

    Set<ContentId> unlocked();

    boolean isUnlocked(ContentId tech);

    int fasterIfUnlocked(ContentId tech, int baseTime);

    int biggerIfUnlocked(ContentId tech, int baseCapacity);

    Research.Snapshot snapshot();
}

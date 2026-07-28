package com.rustorio.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * (P-02, DEV_TASKS.md) {@link Research}: unlocking a {@link Tech} is now the player's explicit,
 * spending choice — {@link Research#addPoints} only accumulates, and {@link Research#unlock} is
 * gated by both affordability and prerequisites, refusing (spending and unlocking nothing) unless
 * both hold.
 */
class ResearchTest {

    /** The exact defect §2.4 of the design audit describes: accumulating enough points must not, by itself, unlock anything. */
    @Test
    void addingPointsAloneNeverUnlocksAnything() {
        Research research = new Research();

        research.addPoints(Tech.FAST_LAB.cost()); // far more than FAST_MINING needs on its own

        assertFalse(research.isUnlocked(Tech.FAST_MINING));
        assertTrue(research.unlocked().isEmpty());
        assertEquals(Tech.FAST_LAB.cost(), research.points(), "points must still be sitting there, unspent");
    }

    @Test
    void unlockSpendsExactlyTheTechsCostOnARootTech() {
        Research research = new Research();
        research.addPoints(Tech.FAST_MINING.cost() + 5); // a little extra left over on purpose

        assertTrue(research.unlock(Tech.FAST_MINING));

        assertTrue(research.isUnlocked(Tech.FAST_MINING));
        assertEquals(5, research.points(), "only the tech's own cost is spent, not the whole pool");
    }

    @Test
    void unlockRefusesWhenNotEnoughPointsAreBanked() {
        Research research = new Research();
        research.addPoints(Tech.FAST_MINING.cost() - 1);

        assertFalse(research.unlock(Tech.FAST_MINING));

        assertFalse(research.isUnlocked(Tech.FAST_MINING));
        assertEquals(Tech.FAST_MINING.cost() - 1, research.points(), "a refused unlock must not spend anything");
    }

    /** FAST_SMELTING requires FAST_MINING — affordable alone isn't enough. */
    @Test
    void unlockRefusesWhenAPrerequisiteIsMissingEvenIfAffordable() {
        Research research = new Research();
        research.addPoints(Tech.FAST_SMELTING.cost());

        assertFalse(research.unlock(Tech.FAST_SMELTING), "FAST_MINING isn't unlocked yet");

        assertFalse(research.isUnlocked(Tech.FAST_SMELTING));
        assertEquals(Tech.FAST_SMELTING.cost(), research.points(), "a refused unlock must not spend anything");
    }

    @Test
    void unlockSucceedsOnceThePrerequisiteIsUnlockedFirst() {
        Research research = new Research();
        research.addPoints(Tech.FAST_MINING.cost());
        assertTrue(research.unlock(Tech.FAST_MINING));

        research.addPoints(Tech.FAST_SMELTING.cost());
        assertTrue(research.unlock(Tech.FAST_SMELTING));

        assertTrue(research.isUnlocked(Tech.FAST_SMELTING));
    }

    /** FAST_LAB requires BOTH FAST_SMELTING and BIG_BUFFER — only one of the two must not be enough. */
    @Test
    void unlockWithMultiplePrerequisitesNeedsAllOfThem() {
        Research research = new Research();
        research.addPoints(Tech.FAST_MINING.cost());
        assertTrue(research.unlock(Tech.FAST_MINING));
        research.addPoints(Tech.FAST_SMELTING.cost());
        assertTrue(research.unlock(Tech.FAST_SMELTING));

        research.addPoints(Tech.FAST_LAB.cost());
        assertFalse(research.unlock(Tech.FAST_LAB), "BIG_BUFFER is still locked — only one of FAST_LAB's two prerequisites is met");

        research.addPoints(Tech.BIG_BUFFER.cost());
        assertTrue(research.unlock(Tech.BIG_BUFFER));
        assertTrue(research.unlock(Tech.FAST_LAB), "both prerequisites are unlocked now, and the points were never spent");
    }

    /**
     * A snapshot is a point-in-time record — nobody may edit it after the fact (N5,
     * NEW_BUGS_PROGRESS.md). The compact constructor copied the incoming set but handed the bare,
     * still-mutable {@code EnumSet} back out through the accessor.
     */
    @Test
    void snapshotSetCannotBeMutatedThroughItsAccessor() {
        Research research = new Research();
        research.addPoints(Tech.FAST_MINING.cost());
        assertTrue(research.unlock(Tech.FAST_MINING));

        Research.Snapshot snapshot = research.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.unlocked().add(Tech.FAST_LAB));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unlocked().clear());
        assertEquals(java.util.Set.of(Tech.FAST_MINING), snapshot.unlocked());
    }

    @Test
    void unlockRefusesATechThatIsAlreadyUnlocked() {
        Research research = new Research();
        research.addPoints(Tech.FAST_MINING.cost() * 2);
        assertTrue(research.unlock(Tech.FAST_MINING));

        assertFalse(research.unlock(Tech.FAST_MINING), "already unlocked — nothing left to spend on it");
        assertEquals(Tech.FAST_MINING.cost(), research.points(), "must not be charged a second time");
    }
}

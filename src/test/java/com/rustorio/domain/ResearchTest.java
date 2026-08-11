package com.rustorio.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.api.content.vanilla.VanillaTechEffects;

/**
 * {@link Research}: unlocking a technology is now the player's explicit,
 * spending choice — {@link Research#addPoints} only accumulates, and {@link Research#unlock} is
 * gated by both affordability and prerequisites, refusing (spending and unlocking nothing) unless
 * both hold.
 */
class ResearchTest {

    /** The exact defect §2.4 of the design audit describes: accumulating enough points must not, by itself, unlock anything. */
    @Test
    void addingPointsAloneNeverUnlocksAnything() {
        Research research = new Research(VanillaTechs.frozen());

        research.addPoints(costOf(VanillaTechs.FAST_LAB)); // far more than FAST_MINING needs on its own

        assertFalse(research.isUnlocked(VanillaTechs.FAST_MINING));
        assertTrue(research.unlocked().isEmpty());
        assertEquals(costOf(VanillaTechs.FAST_LAB), research.points(), "points must still be sitting there, unspent");
    }

    @Test
    void unlockSpendsExactlyTheTechsCostOnARootTech() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_MINING) + 5); // a little extra left over on purpose

        assertTrue(research.unlock(VanillaTechs.FAST_MINING));

        assertTrue(research.isUnlocked(VanillaTechs.FAST_MINING));
        assertEquals(5, research.points(), "only the tech's own cost is spent, not the whole pool");
    }

    @Test
    void unlockRefusesWhenNotEnoughPointsAreBanked() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_MINING) - 1);

        assertFalse(research.unlock(VanillaTechs.FAST_MINING));

        assertFalse(research.isUnlocked(VanillaTechs.FAST_MINING));
        assertEquals(costOf(VanillaTechs.FAST_MINING) - 1, research.points(), "a refused unlock must not spend anything");
    }

    /** FAST_SMELTING requires FAST_MINING — affordable alone isn't enough. */
    @Test
    void unlockRefusesWhenAPrerequisiteIsMissingEvenIfAffordable() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_SMELTING));

        assertFalse(research.unlock(VanillaTechs.FAST_SMELTING), "FAST_MINING isn't unlocked yet");

        assertFalse(research.isUnlocked(VanillaTechs.FAST_SMELTING));
        assertEquals(costOf(VanillaTechs.FAST_SMELTING), research.points(), "a refused unlock must not spend anything");
    }

    @Test
    void unlockSucceedsOnceThePrerequisiteIsUnlockedFirst() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_MINING));
        assertTrue(research.unlock(VanillaTechs.FAST_MINING));

        research.addPoints(costOf(VanillaTechs.FAST_SMELTING));
        assertTrue(research.unlock(VanillaTechs.FAST_SMELTING));

        assertTrue(research.isUnlocked(VanillaTechs.FAST_SMELTING));
    }

    /** FAST_LAB requires BOTH FAST_SMELTING and BIG_BUFFER — only one of the two must not be enough. */
    @Test
    void unlockWithMultiplePrerequisitesNeedsAllOfThem() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_MINING));
        assertTrue(research.unlock(VanillaTechs.FAST_MINING));
        research.addPoints(costOf(VanillaTechs.FAST_SMELTING));
        assertTrue(research.unlock(VanillaTechs.FAST_SMELTING));

        research.addPoints(costOf(VanillaTechs.FAST_LAB));
        assertFalse(research.unlock(VanillaTechs.FAST_LAB), "BIG_BUFFER is still locked — only one of FAST_LAB's two prerequisites is met");

        research.addPoints(costOf(VanillaTechs.BIG_BUFFER));
        assertTrue(research.unlock(VanillaTechs.BIG_BUFFER));
        assertTrue(research.unlock(VanillaTechs.FAST_LAB), "both prerequisites are unlocked now, and the points were never spent");
    }

    /**
     * A snapshot is a point-in-time record — nobody may edit it after the fact (N5,
     * NEW_BUGS_PROGRESS.md). The compact constructor copied the incoming set but handed the bare,
     * still-mutable {@code EnumSet} back out through the accessor.
     */
    @Test
    void snapshotSetCannotBeMutatedThroughItsAccessor() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_MINING));
        assertTrue(research.unlock(VanillaTechs.FAST_MINING));

        Research.Snapshot snapshot = research.snapshot();

        assertThrows(UnsupportedOperationException.class, () -> snapshot.unlocked().add(VanillaTechs.FAST_LAB));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.unlocked().clear());
        assertEquals(java.util.Set.of(VanillaTechs.FAST_MINING), snapshot.unlocked());
    }

    @Test
    void unlockRefusesATechThatIsAlreadyUnlocked() {
        Research research = new Research(VanillaTechs.frozen());
        research.addPoints(costOf(VanillaTechs.FAST_MINING) * 2);
        assertTrue(research.unlock(VanillaTechs.FAST_MINING));

        assertFalse(research.unlock(VanillaTechs.FAST_MINING), "already unlocked — nothing left to spend on it");
        assertEquals(costOf(VanillaTechs.FAST_MINING), research.points(), "must not be charged a second time");
    }

    @Test
    void hasEffectIsTrueOnlyAfterAGrantingTechIsUnlocked() {
        Research research = new Research(VanillaTechs.frozen());
        assertFalse(research.hasEffect(VanillaTechEffects.FAST_MINING));

        research.addPoints(costOf(VanillaTechs.FAST_MINING));
        assertTrue(research.unlock(VanillaTechs.FAST_MINING));

        assertTrue(research.hasEffect(VanillaTechEffects.FAST_MINING),
                "unlocking a tech must surface every effect it lists");
        assertFalse(research.hasEffect(VanillaTechEffects.BIG_BUFFER),
                "an effect from a still-locked tech must stay false");
    }

    /** A vanilla technology's price, read from the registry the game itself researches through. */
    private static int costOf(com.rustorio.api.content.ContentId tech) {
        return VanillaTechs.frozen().get(tech).cost();
    }
}

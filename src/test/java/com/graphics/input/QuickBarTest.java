package com.graphics.input;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

/**
 * {@link QuickBar} — pure logic, no window needed, which is the point of it being a class of its
 * own rather than four fields inside {@code InputHandler}: the growth rule and the duplicate rule
 * are exactly the kind of arithmetic that is invisible in a headless session and wrong on screen.
 */
class QuickBarTest {

    private static ContentId id(int n) {
        return ContentId.of("test:building_" + n);
    }

    @Test
    void anEmptyBarStillShowsOneRowSoThePlayerCanSeeItExists() {
        QuickBar bar = new QuickBar();

        assertTrue(bar.isEmpty());
        assertEquals(3, bar.visibleCells(), "an empty bar draws its first row, not nothing at all");
        assertEquals(1, bar.visibleRows());
    }

    /** The growth rule the owner asked for, at each of its three steps and at the boundary between them. */
    @Test
    void theBarGrowsARowAtATimeAsPinsArriveAndStopsAtNine() {
        QuickBar bar = new QuickBar();

        IntStream.rangeClosed(1, 3).forEach(n -> bar.pin(id(n)));
        assertEquals(3, bar.visibleCells(), "three pins still fit one row");

        bar.pin(id(4));
        assertEquals(6, bar.visibleCells(), "the fourth pin is what adds the second row");

        IntStream.rangeClosed(5, 6).forEach(n -> bar.pin(id(n)));
        assertEquals(6, bar.visibleCells(), "six pins still fit two rows");

        bar.pin(id(7));
        assertEquals(9, bar.visibleCells(), "the seventh adds the third");

        IntStream.rangeClosed(8, 9).forEach(n -> bar.pin(id(n)));
        assertEquals(9, bar.visibleCells());
        assertEquals(3, bar.visibleRows());
    }

    /**
     * A full bar refuses rather than evicting. Silently dropping the oldest pin would mean the
     * player loses something they chose on purpose, at the moment they are looking somewhere else
     * entirely — at the catalogue they just clicked in.
     */
    @Test
    void pinningIntoAFullBarIsRefusedRatherThanEvictingSomethingThePlayerChose() {
        QuickBar bar = new QuickBar();
        IntStream.rangeClosed(1, 9).forEach(n -> bar.pin(id(n)));

        assertEquals(-1, bar.pin(id(10)), "the tenth pin has nowhere to go and must say so");
        assertEquals(9, bar.slots().size());
        assertEquals(id(1), bar.at(0).orElseThrow(), "the first pin must still be there");
    }

    @Test
    void pinningTheSameBuildingTwiceReusesItsCellInsteadOfSpendingAnother() {
        QuickBar bar = new QuickBar();
        bar.pin(id(1));
        bar.pin(id(2));

        assertEquals(0, bar.pin(id(1)), "the second pin of the same building returns where it already is");
        assertEquals(2, bar.slots().size(), "and consumes no extra cell");
    }

    @Test
    void unpinningClosesTheGapSoTheGridNeverShowsAHoleBetweenPins() {
        QuickBar bar = new QuickBar();
        IntStream.rangeClosed(1, 3).forEach(n -> bar.pin(id(n)));

        bar.unpin(1);

        assertEquals(List.of(id(1), id(3)), bar.slots());
        assertTrue(bar.at(2).isEmpty(), "the third cell is drawn but empty now");
    }

    /**
     * A saved layout is player data that has been sitting in a file, so it is not trusted: a
     * hand-edited settings file must not be able to produce a bar with twelve entries or the same
     * building twice, since no sequence of clicks could have built one.
     */
    @Test
    void restoringIgnoresDuplicatesAndAnythingPastTheNinthSlot() {
        QuickBar bar = new QuickBar();
        List<ContentId> overlong = IntStream.rangeClosed(1, 12).mapToObj(QuickBarTest::id).toList();

        bar.restore(overlong);
        assertEquals(9, bar.slots().size(), "a longer saved bar is truncated, not honoured");

        bar.restore(List.of(id(1), id(1), id(2)));
        assertEquals(List.of(id(1), id(2)), bar.slots(), "a duplicate in the file collapses to one cell");
        assertFalse(bar.isEmpty());
    }
}

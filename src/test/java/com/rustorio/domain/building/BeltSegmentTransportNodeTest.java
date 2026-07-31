package com.rustorio.domain.building;

import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the point of generalizing {@link BeltSegment} from {@link Belt} to {@link TransportNode}:
 * a class that is NOT {@link Belt} — and does not even implement {@link Building} — cascades cargo
 * through the SAME segment as a real {@link Belt}, tile for tile.
 *
 * <p>Deliberately does NOT go through {@code World}/{@code BuildingFactory.create}: at the time
 * this test was written, {@link Building} was still {@code sealed}, so no class outside its {@code
 * permits} list could implement it at all — this test predates {@code Building} opening up (a
 * later card in the same phase). {@link BeltSegment} itself only ever asks for {@link
 * TransportNode} — it doesn't care whether a tile is a {@link Building} at all — so this test
 * exercises exactly the part of the mechanism this card generalizes, without depending on the
 * unrelated card that opens {@link Building} up.
 */
class BeltSegmentTransportNodeTest {

    /** The bare minimum {@link TransportNode} surface — no {@link Building} methods at all. */
    private static final class PlainTransportNode implements TransportNode {
        private final Direction direction;
        private @Nullable ItemType held;
        private @Nullable BeltSegment segment;
        private boolean arrivedThisTick;

        PlainTransportNode(Direction direction) {
            this.direction = direction;
        }

        @Override
        public Direction direction() {
            return direction;
        }

        @Override
        public @Nullable ItemType held() {
            return held;
        }

        @Override
        public void setHeld(ItemType item) {
            held = item;
        }

        @Override
        public void clearHeld() {
            held = null;
        }

        @Override
        public boolean arrivedThisTick() {
            return arrivedThisTick;
        }

        @Override
        public void joinSegment(@Nullable BeltSegment segment) {
            this.segment = segment;
        }

        @Override
        public void leaveSegment() {
            if (segment != null) {
                segment.remove(this);
            }
        }

        @Override
        public BeltSegment segment() {
            return Objects.requireNonNull(segment);
        }
    }

    @Test
    void aPlainTransportNodeCascadesCargoAlongsideARealBeltInTheSameSegment() {
        Belt belt = new Belt(Direction.RIGHT);
        PlainTransportNode plain = new PlainTransportNode(Direction.RIGHT);

        // Built directly, the low-level way BeltSegment itself is built (package-private
        // constructor) — bypassing World entirely, since this test is about BeltSegment's own
        // generalized typing, not the placement path (see class javadoc).
        BeltSegment segment = new BeltSegment(Direction.RIGHT);
        segment.addHead(belt); // belt becomes the tail (the only tile so far)
        segment.addHead(plain); // plain becomes the head, past belt

        assertTrue(segment.isTail(belt));
        assertEquals(2, segment.size());

        belt.setHeld(VanillaItems.IRON_ORE);
        segment.tick(item -> false); // nothing to exit to yet — just cascades within the segment
        assertEquals(Optional.empty(), belt.heldItem(), "cargo must leave the real Belt tail");
        assertEquals(VanillaItems.IRON_ORE, plain.held(),
                "cargo must land on the plain TransportNode head — proves one shared segment, not two independent tiles");

        List<ItemType> exited = new ArrayList<>();
        segment.tick(item -> {
            exited.add(item);
            return true;
        });
        assertEquals(Optional.empty(), Optional.ofNullable(plain.held()), "cargo must leave the plain node once it's the tail-facing exit");
        assertEquals(List.of(VanillaItems.IRON_ORE), exited);
    }

    @Test
    void mergingTwoSegmentsWorksWhenOneSideIsAPlainTransportNode() {
        Belt tailBelt = new Belt(Direction.RIGHT);
        PlainTransportNode middle = new PlainTransportNode(Direction.RIGHT);
        Belt headBelt = new Belt(Direction.RIGHT);

        BeltSegment segmentA = new BeltSegment(Direction.RIGHT);
        segmentA.addHead(tailBelt);
        BeltSegment segmentB = new BeltSegment(Direction.RIGHT);
        segmentB.addHead(headBelt);

        // The same shape World.attachToSegment produces when a tile lands between two existing
        // segments: attach to the one behind, then merge the one ahead into it.
        segmentA.addHead(middle);
        segmentA.mergeHead(segmentB);

        assertEquals(3, segmentA.size());
        assertTrue(segmentA.isTail(tailBelt));

        tailBelt.setHeld(VanillaItems.IRON_ORE);
        segmentA.tick(item -> false);
        segmentA.tick(item -> false);
        List<ItemType> exited = new ArrayList<>();
        segmentA.tick(item -> {
            exited.add(item);
            return true;
        });
        assertEquals(List.of(VanillaItems.IRON_ORE), exited,
                "three tiles, three ticks, identical timing to an all-Belt chain, even with a plain TransportNode merged in the middle");
    }
}

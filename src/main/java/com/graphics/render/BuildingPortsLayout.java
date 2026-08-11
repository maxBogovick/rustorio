package com.graphics.render;

import com.rustorio.domain.Direction;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Where IN and OUT ports sit on a building's footprint — shared by {@link OverlayRenderer} (draw)
 * and tests. Pure arithmetic, no libGDX.
 *
 * <p>The old overlay put one arrow on the <em>anchor cell's centre</em>. That reads fine on a 1×1
 * furnace and lies on a 2×2 assembler: the real output cell is on the far edge of the footprint
 * ({@code Furnace#outputX}), so the arrow sat on the wrong tile and players fed belts into the
 * middle of the machine. Ports live on the outer edges of the whole footprint instead.
 *
 * <p>They sit <em>inset</em> from the outer edge, not on the pixel of the boundary: an OUT tip
 * that crossed into the neighbour cell painted over whatever building stood there. IN already
 * pointed inward; OUT must stay inside for the same reason.
 */
public final class BuildingPortsLayout {

    /**
     * Four sides in declaration order — a fixed list, not {@code Direction.values()}, because
     * {@code values()} allocates a fresh array and this runs for every visible building every frame
     * ({@code graphics.md}).
     */
    public static final List<Direction> SIDES = List.of(
            Direction.RIGHT, Direction.DOWN, Direction.LEFT, Direction.UP);

    /**
     * How far inside the footprint the port centre sits, in tiles. Must be {@code ≥}
     * {@link #OUT_REACH_TILES} so the OUT tip never crosses the outer edge.
     */
    public static final float PORT_INSET_TILES = 0.22f;

    /** Tip length of an OUT arrow along the outward axis, in tiles — kept ≤ {@link #PORT_INSET_TILES}. */
    public static final float OUT_REACH_TILES = 0.18f;

    /** Tip length of an IN arrow along the inward axis, in tiles. */
    public static final float IN_REACH_TILES = 0.28f;

    private BuildingPortsLayout() {
    }

    /** Pixel X of the port centre on {@code side}. {@code anchorPx} is the left of the footprint. */
    public static float edgeCenterX(float anchorPx, float tile, int footprintWidth, int footprintHeight,
            Direction side) {
        float inset = PORT_INSET_TILES * tile;
        return switch (side) {
            case LEFT -> anchorPx + inset;
            case RIGHT -> anchorPx + footprintWidth * tile - inset;
            case UP, DOWN -> anchorPx + footprintWidth * tile / 2f;
        };
    }

    /**
     * Pixel Y of that same centre — screen Y grows up, so UP is toward the top of the footprint.
     * {@code anchorPy} is the screen bottom of the whole footprint ({@code Grid#yFootprintBottom}).
     */
    public static float edgeCenterY(float anchorPy, float tile, int footprintWidth, int footprintHeight,
            Direction side) {
        float inset = PORT_INSET_TILES * tile;
        return switch (side) {
            case DOWN -> anchorPy + inset;
            case UP -> anchorPy + footprintHeight * tile - inset;
            case LEFT, RIGHT -> anchorPy + footprintHeight * tile / 2f;
        };
    }

    /** Direction an IN arrow's tip must point so it aims into the building from {@code side}. */
    public static Direction inward(Direction side) {
        return side.rotate().rotate();
    }

    /** Whether {@code side} is an output face (primary or splitter secondary). */
    public static boolean isOutputSide(Direction side, Direction primaryOut,
            @Nullable Direction secondaryOut) {
        return side == primaryOut || side == secondaryOut;
    }

    /**
     * IN triangles only on multi-cell machines. A 1×1 furnace/press already reads as "feed any
     * free side, leave on the yellow arrow"; drawing three blue INs on every such building drowned
     * the map. The vanilla assembler is 2×2 — the only place those sides need labeling. A modded
     * multi-cell machine gets the same treatment without naming {@code ASSEMBLER} in the renderer.
     */
    public static boolean showsInputPorts(int footprintWidth, int footprintHeight) {
        return footprintWidth > 1 || footprintHeight > 1;
    }
}

package com.graphics.render;

import com.rustorio.domain.Cell;

/**
 * Which palette slot a network's overlay colour comes from — pure arithmetic, kept out of {@link
 * OverlayRenderer} so a headless test can pin it down (the {@code graphics.md} rule: testable maths
 * lives in a class that needs no window, the way {@code CameraViewport} does). The input is a
 * network's anchor {@link Cell}: equal anchors always land on the same slot, so every tile of one
 * network is painted alike and paints the same way on the next frame and the next run — the whole
 * reason a network's identity is a coordinate and not an object hash.
 */
final class NetworkTint {

    private NetworkTint() {
    }

    /**
     * A slot in {@code [0, paletteSize)} for a network anchored at {@code anchor}: a fixed integer
     * mix of its two coordinates, folded into range. Deterministic and spread out, but NOT a
     * promise that two different networks never share a slot — an overlay that now and then paints
     * two distant networks alike is still readable, whereas one that flickers between runs is not,
     * so determinism is the property worth guaranteeing and distinctness is not.
     */
    static int paletteIndex(Cell anchor, int paletteSize) {
        int mixed = anchor.x() * 73856093 ^ anchor.y() * 19349663;
        return Math.floorMod(mixed, paletteSize);
    }
}

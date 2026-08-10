package com.rustorio.domain.building;

/**
 * A picture a building offers the game to show — raw pixels and nothing else.
 *
 * <p>Plain {@code int}s and an {@code int[]}, deliberately: this crosses from a building to the
 * renderer, and the domain does not take rendering types in its signatures (the same rule that
 * makes {@code World.forEachBuildingIn} hand out bare coordinates). A {@code Texture}, a {@code
 * Pixmap} or an AWT image here would put libGDX or {@code java.desktop} in the one ring that is
 * supposed to know about neither, and would make this interface unimplementable by a mod that
 * produces its pixels some other way.
 *
 * <p>Not persisted and not part of any save: a picture is a view of state, never state.
 *
 * @param width pixels across, at least 1
 * @param height pixels down, at least 1
 * @param argb {@code width * height} pixels in row-major order, each {@code 0xAARRGGBB}
 */
public record BuildingImage(int width, int height, int[] argb) {

    public BuildingImage {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("image must have positive size, got " + width + "x" + height);
        }
        if (argb.length != width * height) {
            throw new IllegalArgumentException(
                    "expected " + width * height + " pixels for " + width + "x" + height + ", got " + argb.length);
        }
    }
}

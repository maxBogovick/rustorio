package com.rustorio.domain.building;

import java.util.Optional;

/**
 * A building that has a full picture to show, not just lines of text — the door {@link
 * InspectableBuilding} is for facts, this one is for an image.
 *
 * <p>It exists for a mod's sake, and the shape of it is chosen so the engine never learns what the
 * picture is OF. The case that prompted it is a monitor showing a fetched web page drawn as a page;
 * the engine knows only that some building can produce {@code width * height} pixels on request,
 * and would carry a mod's map, camera feed or blueprint thumbnail exactly as well.
 *
 * <p><b>Called only while the viewer is actually open</b>, and then once per frame — so an
 * implementation must hand back something it already has rather than build it on the spot, the same
 * discipline {@link InspectableBuilding} asks for and for the same reason. Returning the SAME
 * instance while the picture has not changed is what lets the renderer keep one texture instead of
 * uploading pixels to the GPU sixty times a second; a new instance means "this changed, upload it
 * again".
 *
 * <p>Empty means "nothing to show right now" — a monitor whose miner has not fetched anything yet,
 * or fetched something that is not a page. That is an ordinary state, not a failure.
 */
public interface ViewableBuilding {

    /** This building's current picture, or empty when it has none — see the interface javadoc for what the caller guarantees and what it expects back. */
    Optional<BuildingImage> image(TickContext world, int x, int y);
}

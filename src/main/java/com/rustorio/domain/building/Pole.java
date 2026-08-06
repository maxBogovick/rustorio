package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import org.jspecify.annotations.Nullable;

/**
 * A power pole: covers a square of cells around itself, and joins with every other pole within
 * reach into one grid. The only building that is actually a member of a {@link PowerNetwork}.
 *
 * <p>It does not tick. A pole neither makes nor uses power — laying out where the grid reaches IS
 * its whole function, which is the point of poles existing at all rather than machines simply
 * wiring themselves to their neighbors (owner decision): planning coverage is a task the player
 * does, and a building that does nothing per tick is what makes it free to do.
 *
 * <p>Its radius comes from {@link BuildingPrototype#power()}, so a mod's "big pole" is a JSON file
 * with a bigger number and no Java at all.
 */
public final class Pole implements Building, PowerNode {

    private final BuildingType type;
    private final BuildingPrototype prototype;

    private @Nullable PowerNetwork network;

    public Pole(BuildingType type, BuildingPrototype prototype) {
        this.type = type;
        this.prototype = prototype;
    }

    @Override
    public int coverageRadius() {
        PowerSpec spec = prototype.power();
        return spec == null ? 0 : spec.radius();
    }

    @Override
    public @Nullable PowerNetwork network() {
        return network;
    }

    @Override
    public void joinNetwork(@Nullable PowerNetwork network) {
        this.network = network;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture());
    }

    @Override
    public BuildingType type() {
        return type;
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public PoleState state() {
        return new PoleState();
    }
}

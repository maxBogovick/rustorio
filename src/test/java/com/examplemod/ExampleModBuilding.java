package com.examplemod;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;

/**
 * A minimal {@link Building} implementation living OUTSIDE {@code com.rustorio.domain.building} —
 * a stand-in for a mod's own class in its own package. Proves E5-06's whole point: before {@code
 * Building} gave up {@code sealed}, no foreign class could ever implement it — only classes listed
 * in its own {@code permits} clause, all of them in the same file. This one compiles now.
 *
 * <p>{@link #state()} throws: this stand-in has no {@code Codec} registered anywhere, so it can't
 * really participate in save/load — a genuinely new archetype needs to register a real prototype
 * with a real codec (see {@code ExampleMod}/{@code ExampleModBelt} for one that does), which this
 * minimal class was never meant to demonstrate.
 */
final class ExampleModBuilding implements Building {

    @Override
    public Appearance appearance() {
        return Appearance.of(VanillaSprites.CHEST); // borrows a vanilla sprite — this test isn't about assets
    }

    @Override
    public BuildingType type() {
        // Borrows a vanilla kind — a real mod would register its own via BuildingPrototype once
        // BuildingFactory.create/restore's own dispatch opens up (E5-07), not this card's job.
        return BuildingType.CHEST;
    }

    @Override
    public Object state() {
        throw new UnsupportedOperationException("this stand-in class has no Codec of its own registered anywhere");
    }
}

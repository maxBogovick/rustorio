package com.rustorio.game;

import com.rustorio.domain.OreLayout;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.world.World;
import com.rustorio.mod.EventWiring;
import com.rustorio.mod.LoadedGame;
import com.rustorio.persistence.JsonSaveRepository;
import java.nio.file.Path;

/**
 * The two places a {@link LoadedGame} has to be turned into something the game runs on: a live
 * {@link World}, and a save repository configured for that same content.
 *
 * <p>Both exist as one method because doing them by hand is what went wrong. Every caller that
 * built a {@code World} from loaded content forgot to attach the mod event bus to it, so no mod
 * ever received an event in the running game; every caller that opened a save passed the loaded
 * item registry but not the prototype renames those same mods declared, so a renamed building was
 * reported as missing content instead of being restored. Neither omission was visible at the call
 * site — which is the argument for there being exactly one call site.
 */
public final class GameBootstrap {

    private GameBootstrap() {
    }

    /**
     * Builds the world on {@code content}'s registries — items, fluids, buildings, recipes and
     * technologies alike — then attaches {@code content}'s event bus to it, in that order and
     * before the caller places anything: {@code WorldInitEvent} claims to fire on a world where
     * nothing has happened yet (see {@link EventWiring#attach}).
     */
    public static World createWorld(LoadedGame content, OreLayout oreLayout, int width, int height) {
        BuildingFactory buildingFactory = new BuildingFactory(oreLayout, content.recipes(), content.items(),
                content.buildings(), content.fluids());
        World world = new World(width, height, buildingFactory, content.techs());
        EventWiring.attach(world, content.events());
        return world;
    }

    /**
     * A save repository for {@code path} that reads and writes {@code content}'s items and applies
     * {@code content}'s prototype renames.
     *
     * <p>Both arguments matter and neither is a default: without the item registry a save holding a
     * modded item fails to load with an unknown key, and without the renames a prototype a mod
     * merely renamed is reported as removed content and its cell comes back empty.
     */
    public static JsonSaveRepository saves(LoadedGame content, Path path) {
        return new JsonSaveRepository(path, content.items(), content.prototypeRenames());
    }
}

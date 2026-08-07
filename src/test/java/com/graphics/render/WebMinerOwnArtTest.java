package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import com.webminer.WebMinerMod;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A mod that adds buildings ships their art too — checked on the one mod in this repository that
 * has any. All three of the webminer archetypes used to name vanilla sprites instead ({@code
 * rustorio:miner} for the miner, {@code rustorio:belt_empty} for both pass-throughs), which is
 * invisible to every other test: a borrowed sprite resolves in the atlas exactly like an owned
 * one, so nothing went red while a web miner was drawn as a drill and a monitor was drawn as a
 * belt with nothing to tell it apart from the interpreter beside it.
 *
 * <p>Two halves, and both are needed: the prototype must name a sprite of its own namespace, AND
 * that sprite must be one the renderer will actually find. The second half is what catches a
 * misnamed or missing PNG — an id nobody backs with a file throws only at draw time, in a window
 * this test run has no way to open.
 *
 * <p>The index is assembled here the way {@link Textures#loadFrom} assembles it (mod directory
 * name as namespace, {@code textures/} subdirectory as the source), because {@code loadFrom}
 * itself packs pixels and needs a GL context; {@link TextureIndex} is the half that does not.
 */
class WebMinerOwnArtTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path WEBMINER_MOD_DIR = Path.of("resources", "mods", "webminer");

    @Test
    void everyWebminerBuildingDrawsItsOwnSpriteAndThatSpriteHasAFile() {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WEBMINER_MOD_DIR));
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        WebMinerMod.registerAll(prototypes, content.items());
        prototypes.freeze();

        TextureIndex index = TextureIndex.vanilla();
        index.addDirectory(WEBMINER_MOD_DIR.getFileName().toString(), WEBMINER_MOD_DIR.resolve("textures"));

        for (ContentId building : List.of(WebMinerMod.WEB_MINER_ID, WebMinerMod.MONITOR_ID,
                WebMinerMod.INTERPRETER_ID)) {
            ContentId sprite = prototypes.get(building).texture();
            assertEquals("webminer", sprite.namespace(),
                    building + " draws a sprite from another mod's namespace: " + sprite);
            assertTrue(index.sprites().contains(sprite),
                    sprite + " is named by " + building + " but no file in resources/mods/webminer/textures backs it");
        }
    }
}

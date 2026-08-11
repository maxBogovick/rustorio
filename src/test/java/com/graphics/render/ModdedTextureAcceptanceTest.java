package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.vanilla.VanillaSprites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase capstone: a {@link TextureIndex} built from {@link TextureIndex#vanilla()} plus one
 * modded entry (via {@link TextureIndex#addDirectory}) holds both, under different namespaces,
 * with no change to either mechanism — the same index a mod's sprite would occupy is the one
 * every vanilla sprite already lives in.
 *
 * <p>This only proves the indexing half. Whether a modded sprite actually ends up packed into the
 * SAME atlas as the vanilla ones — the other half of "moddable, one draw batch" — needs a real
 * {@code PixmapPacker}/{@code TextureAtlas}, which needs a GL context this headless test run
 * doesn't have; nothing here exercises {@link Textures} itself.
 */
class ModdedTextureAcceptanceTest {

    @Test
    void indexHoldsBothVanillaAndModdedSpritesUnderDistinctNamespaces(@TempDir Path modDir) throws IOException {
        Files.createFile(modDir.resolve("copper_ore.png"));

        TextureIndex index = TextureIndex.vanilla();
        int vanillaCount = index.sprites().size();
        index.addDirectory("examplemod", modDir);

        ContentId moddedOre = new ContentId("examplemod", "copper_ore");
        assertEquals(vanillaCount + 1, index.sprites().size());
        assertTrue(index.sprites().contains(VanillaSprites.CHEST), "vanilla entries must survive adding a mod directory");
        assertTrue(index.sprites().contains(moddedOre), "the modded sprite must appear under its own namespace");
        assertEquals(modDir.resolve("copper_ore.png").toString(), index.path(moddedOre));
    }
}

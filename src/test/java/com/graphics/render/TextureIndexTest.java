package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.VanillaSprites;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link TextureIndex} — the mapping half of {@link Textures}, deliberately free of any libGDX
 * import so it can run headless, unlike the {@code PixmapPacker}/{@code TextureAtlas} half that
 * consumes it.
 */
class TextureIndexTest {

    @Test
    void vanillaHasAnEntryForEveryFileBackedSprite() {
        TextureIndex index = TextureIndex.vanilla();

        assertEquals(20, index.sprites().size());
        assertTrue(index.sprites().contains(VanillaSprites.MINER));
        assertTrue(index.sprites().contains(VanillaSprites.ASSEMBLER));
        assertTrue(index.sprites().contains(VanillaSprites.LAB));
        assertTrue(index.sprites().contains(VanillaSprites.PIPE),
                "a building whose sprite has no entry here throws while the atlas is packed, not when it is first drawn");
    }

    @Test
    void pathReturnsTheRegisteredFileForAKnownSprite() {
        TextureIndex index = TextureIndex.vanilla();

        assertEquals("resources/chest.png", index.path(VanillaSprites.CHEST));
    }

    @Test
    void pathRejectsASpriteThatWasNeverRegistered() {
        TextureIndex index = new TextureIndex();

        assertThrows(IllegalArgumentException.class, () -> index.path(VanillaSprites.MINER));
    }

    @Test
    void putOverwritesAnExistingEntryForTheSameSprite() {
        TextureIndex index = TextureIndex.vanilla();

        index.put(VanillaSprites.CHEST, "mods/example/chest.png");

        assertEquals("mods/example/chest.png", index.path(VanillaSprites.CHEST));
        assertEquals(20, index.sprites().size(), "overwriting an existing sprite must not add a second entry");
    }

    @Test
    void addDirectoryRegistersOnePngPerFileUnderTheGivenNamespace(@TempDir Path dir) throws IOException {
        Files.createFile(dir.resolve("copper_ore.png"));
        Files.createFile(dir.resolve("copper_plate.png"));
        Files.createFile(dir.resolve("readme.txt")); // not a .png — must be ignored

        TextureIndex index = new TextureIndex();
        index.addDirectory("examplemod", dir);

        assertEquals(2, index.sprites().size());
        ContentId ore = new ContentId("examplemod", "copper_ore");
        assertEquals(dir.resolve("copper_ore.png").toString(), index.path(ore));
    }
}

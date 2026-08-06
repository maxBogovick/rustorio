package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.VanillaSprites;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Which file backs each sprite — plain data, no libGDX import anywhere in this class. {@link
 * Textures} is the other half: it takes an index and actually loads/packs the files it names.
 * Splitting the two means the mapping itself (does every sprite have an entry? do names collide?)
 * is unit-testable without a GL context, unlike the packing it feeds.
 *
 * <p>{@link VanillaSprites#LAB} now has a real file ({@code resources/lab.png}) like every other
 * vanilla sprite; {@link Textures} still falls back to a placeholder for any index that omits it
 * (e.g. a mod that adds a building without shipping its own art) — see that class's javadoc.
 */
final class TextureIndex {

    private final Map<ContentId, String> paths = new HashMap<>();

    static TextureIndex vanilla() {
        TextureIndex index = new TextureIndex();
        index.put(VanillaSprites.MINER, "resources/miner_1.png");
        index.put(VanillaSprites.CHEST, "resources/chest.png");
        index.put(VanillaSprites.FURNACE_HOT, "resources/furnace_on.png");
        index.put(VanillaSprites.FURNACE_COLD, "resources/furnace_off.png");
        index.put(VanillaSprites.BELT_EMPTY, "resources/belt_1.png");
        index.put(VanillaSprites.BELT_FULL, "resources/belt_2.png");
        index.put(VanillaSprites.SPLITTER, "resources/branch_1.png");
        index.put(VanillaSprites.FILTER, "resources/branch_2.png");
        index.put(VanillaSprites.INSERTER, "resources/branch_3.png");
        index.put(VanillaSprites.UNDERGROUND_IN, "resources/underground_in.png");
        index.put(VanillaSprites.UNDERGROUND_OUT, "resources/underground_out.png");
        index.put(VanillaSprites.ASSEMBLER, "resources/assembler.png");
        index.put(VanillaSprites.LAB, "resources/lab.png");
        // Свои спрайты жидкостно-электрических зданий: та же тёмная стальная панель, что у ленты и
        // печи, плюс акцент по смыслу (вода/пламя/ток). Имена файлов без завершающей цифры —
        // упаковщик атласа не отрежет её как индекс (см. ловушку в graphics.md).
        index.put(VanillaSprites.PIPE, "resources/pipe.png");
        index.put(VanillaSprites.TANK, "resources/tank.png");
        index.put(VanillaSprites.PUMP, "resources/pump.png");
        index.put(VanillaSprites.BOILER, "resources/boiler.png");
        index.put(VanillaSprites.POLE, "resources/pole.png");
        index.put(VanillaSprites.GENERATOR, "resources/generator.png");
        index.put(VanillaSprites.ELECTRIC_MINER, "resources/electric_miner.png");
        return index;
    }

    void put(ContentId sprite, String path) {
        paths.put(sprite, path);
    }

    /**
     * Registers every {@code .png} directly inside {@code dir} under {@code namespace}, named by
     * its filename without the extension — e.g. {@code copper_ore.png} becomes {@code
     * namespace:copper_ore}. Only records what and where; the file's actual pixels aren't read
     * until packing. Not a mod loader — just the mechanism a future one can call once it has
     * decided which directory belongs to which mod.
     */
    void addDirectory(String namespace, Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(file -> file.getFileName().toString().endsWith(".png"))
                    .forEach(file -> {
                        String fileName = file.getFileName().toString();
                        String name = fileName.substring(0, fileName.length() - ".png".length());
                        put(new ContentId(namespace, name), file.toString());
                    });
        } catch (IOException e) {
            throw new UncheckedIOException("failed to scan texture directory: " + dir, e);
        }
    }

    String path(ContentId sprite) {
        String path = paths.get(sprite);
        if (path == null) {
            throw new IllegalArgumentException("No file registered for sprite: " + sprite);
        }
        return path;
    }

    Set<ContentId> sprites() {
        return Set.copyOf(paths.keySet());
    }
}

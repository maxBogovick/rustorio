package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.vanilla.VanillaSprites;
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

    /**
     * Имена файлов, не совпадающие с именем спрайта, — все исторические: картинки появились раньше,
     * чем спрайты стали называться отдельно от них, и переименовать PNG агенту запрещено. Всё
     * остальное берётся по соглашению, поэтому новый ванильный спрайт добавляется одной строкой в
     * {@code VanillaSprites}, а не двумя в двух файлах.
     */
    private static final Map<ContentId, String> FILE_NAME_EXCEPTIONS = Map.of(
            VanillaSprites.MINER, "miner_1",
            VanillaSprites.FURNACE_HOT, "furnace_on",
            VanillaSprites.FURNACE_COLD, "furnace_off",
            VanillaSprites.BELT_EMPTY, "belt_1",
            VanillaSprites.BELT_FULL, "belt_2",
            VanillaSprites.SPLITTER, "branch_1",
            VanillaSprites.FILTER, "branch_2",
            VanillaSprites.INSERTER, "branch_3");

    /**
     * Соглашение: {@code rustorio:pipe} лежит в {@code resources/pipe.png}. Список спрайтов —
     * {@link VanillaSprites#all()}, один и тот же на весь проект: раньше здесь стоял второй,
     * рукописный, и синхронность двух держалась только на внимании ревьюера.
     */
    static TextureIndex vanilla() {
        TextureIndex index = new TextureIndex();
        for (ContentId sprite : VanillaSprites.all()) {
            index.put(sprite, "resources/" + FILE_NAME_EXCEPTIONS.getOrDefault(sprite, sprite.path()) + ".png");
        }
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

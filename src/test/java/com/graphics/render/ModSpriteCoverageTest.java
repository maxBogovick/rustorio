package com.graphics.render;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModDirectories;
import com.rustorio.mod.ModLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Каждое установленное здание должно называть спрайт, за которым лежит настоящий файл.
 *
 * <p>Закрывает ошибку, которая иначе не ловится ничем: мод объявляет `"texture": "мод:имя"`, PNG
 * никто не кладёт, вся сборка зелёная — и игра падает при первой же отрисовке. Не «показывается
 * заглушка»: {@link Textures#forSprite} бросает исключение, а панель строительства рисует ВСЕ
 * зарегистрированные здания, поэтому падение случается сразу при входе в игру, а не когда игрок
 * решит что-то построить. Заглушка в упаковщике ровно одна и только для ванильной лаборатории.
 *
 * <p>Проверка без окна и без GL: атлас здесь не пакуется, сверяется только индекс «спрайт → файл»,
 * который {@code GameScreen} строит ровно так же — ванильные имена плюс `textures/` каждого мода.
 * Так тест ловит ту же ошибку, что уронила бы окно, но остаётся запускаемым в headless-сборке.
 */
class ModSpriteCoverageTest {

    private static final Path MODS_ROOT = Path.of("resources", "mods");

    @Test
    void everyRegisteredBuildingNamesASpriteThatHasAFile() {
        List<Path> modDirectories = ModDirectories.discover(MODS_ROOT);
        LoadedGame content = ModLoader.loadAll(modDirectories);

        // Тот же индекс, что собирает GameScreen: ванильные спрайты плюс PNG каждого мода.
        TextureIndex index = TextureIndex.vanilla();
        for (Path modDirectory : modDirectories) {
            Path textures = modDirectory.resolve("textures");
            if (Files.isDirectory(textures)) {
                index.addDirectory(modDirectory.getFileName().toString(), textures);
            }
        }

        List<String> missing = new ArrayList<>();
        for (BuildingPrototype prototype : content.buildings().iterate()) {
            ContentId sprite = prototype.texture();
            if (!index.sprites().contains(sprite)) {
                missing.add(prototype.id() + " -> " + sprite);
            }
        }

        assertTrue(missing.isEmpty(), "у этих зданий имя спрайта не подкреплено файлом, и отрисовка "
                + "упадёт при первом же кадре: " + missing);
    }
}

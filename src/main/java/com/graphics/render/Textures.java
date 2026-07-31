package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.VanillaSprites;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Все спрайты игры, загруженные ОДИН раз при старте и склеенные в ЕДИНЫЙ атлас.
 *
 * <p><b>Зачем атлас (задача A2).</b> Видеокарта рисует «пачками» и обязана прервать
 * пачку при каждой смене текстуры. Пока у каждого спрайта своя {@link Texture}, на
 * соседних клетках стоят бур/лента/печь — смена почти на каждой клетке, и пачка
 * рвётся десятки тысяч раз за кадр. Склеив все спрайты в одну большую картинку
 * (одну {@link Texture}), мы убираем смены вовсе: вся сцена рисуется одной пачкой.
 *
 * <p><b>Как склеиваем.</b> {@link PixmapPacker} упаковывает исходные картинки в одну
 * страницу в памяти при старте — без внешнего инструмента и без правки {@code
 * build.gradle}. Наружу каждый спрайт отдаётся как {@link TextureRegion} — «окно» в
 * общий атлас; все окна смотрят в одну и ту же {@link Texture}.
 *
 * <p>Какой файл соответствует какому спрайту знает только {@link TextureIndex} — этот класс
 * лишь читает и пакует то, что индекс называет, регионом по имени самого {@link ContentId}
 * (например {@code "rustorio:miner"}: без хвостовых цифр, так что {@code
 * generateTextureAtlas()} никогда не примет его за кадр анимации).
 *
 * <p>Спрайты лежат в {@code resources/} (оставлены от Rust-версии), фильтр
 * {@link Texture.TextureFilter#Nearest} — иначе пиксель-арт размажется при
 * растягивании до размера клетки. {@link Disposable} обязывает освободить атлас в
 * {@link #dispose()}: libGDX не собирает нативную память GPU сборщиком мусора.
 */
public final class Textures implements Disposable {

    /** Единственная текстура-атлас, куда смотрят все регионы ниже — пересобирается целиком в {@link #reload}. */
    private TextureAtlas atlas;
    private final Map<ContentId, TextureRegion> regions = new HashMap<>();
    /** Уголь на земле (D-05, DEV_TASKS.md) — единственный спрайт ПРЕДМЕТА, который реально упакован в атлас, см. {@link WorldRenderer}. */
    private TextureRegion coalOre;
    /** Тайлы ландшафта (арт-редизайн) — читает {@link WorldRenderer} вместо плоской заливки {@code ShapeRenderer}-прямоугольником. */
    private TextureRegion terrainGround;
    private TextureRegion terrainWater;
    private TextureRegion terrainRock;

    public static Textures vanilla() {
        return new Textures(TextureIndex.vanilla());
    }

    /**
     * Vanilla sprites plus, for every directory in {@code modDirectories} that has a {@code
     * textures/} subdirectory, every {@code .png} in it — under that mod directory's own name as
     * namespace (the same name {@link com.rustorio.mod.ModDirectories#discover} returned it by, and
     * the same name its {@code mod.json}'s own {@code id} is expected to match, exactly like {@code
     * resources/mods/rustorio} already does today). A mod with no {@code textures/} directory at
     * all just contributes nothing here — same "absence isn't an error" rule the JSON content
     * loaders already follow for a missing {@code content/} subdirectory.
     */
    public static Textures loadFrom(List<Path> modDirectories) {
        TextureIndex index = TextureIndex.vanilla();
        for (Path modDirectory : modDirectories) {
            Path texturesDir = modDirectory.resolve("textures");
            if (Files.isDirectory(texturesDir)) {
                index.addDirectory(modDirectory.getFileName().toString(), texturesDir);
            }
        }
        return new Textures(index);
    }

    Textures(TextureIndex index) {
        build(index);
    }

    /**
     * Явная пересборка атласа под новый {@link TextureIndex} — освобождает старый ДО того, как
     * начать паковать новый, а не после (не держать оба на GPU разом). Не полный hot-reload
     * (перегрузка живой сцены — отдельная задача); это только сама смена содержимого атласа.
     */
    public void reload(TextureIndex index) {
        atlas.dispose();
        build(index);
    }

    private void build(TextureIndex index) {
        // padding=2 + duplicateBorder: соседние спрайты не «протекают» друг в друга
        // при повороте/растяжении, а край каждого спрайта продлён в отступ.
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, true);
        for (ContentId sprite : index.sprites()) {
            packFile(packer, regionName(sprite), index.path(sprite));
        }
        // Ванильный LAB теперь тоже имеет свой файл (resources/lab.png) — эта ветка остаётся как
        // запасной вариант для любого ДРУГОГО индекса, который его не назвал (например, мод,
        // добавляющий здание без собственной художки). Честнее упаковать заметную заглушку, чем
        // подсунуть чужую картинку. packer.pack() не позовётся дважды под одним именем региона
        // (code review finding S4) именно из-за этой проверки.
        if (!index.sprites().contains(VanillaSprites.LAB)) {
            packLabPlaceholder(packer, regionName(VanillaSprites.LAB));
        }
        // Спрайты предметов (iron_ore.png и т.п.) сознательно НЕ упакованы: это заготовки
        // 3×4/4×4 пикселя, неотличимые друг от друга на глаз — груз рисует {@link ItemRenderer}
        // кружком через ShapeRenderer, настоящая художка для предметов не нужна.
        // coal_ore.png — та же 4×4 заготовка, но D-05 (DEV_TASKS.md) прямо требует использовать
        // именно этот файл в рендере, а не только цвет земли (см. WorldRenderer) — единственное
        // исключение из правила выше.
        packFile(packer, "coal_ore", "resources/coal_ore.png");
        // Тайлы ландшафта (арт-редизайн) — та же логика: земля/вода/камень были плоской заливкой
        // цветом, теперь у каждой лёгкая бесшовная спекл-текстура, ore-оверлей поверх неё рисует
        // {@link WorldRenderer} по-прежнему цветом (D-02/X-02, DEV_TASKS.md — та функциональная
        // роль не менялась, только фон под ней перестал быть голым прямоугольником).
        packFile(packer, "terrain_ground", "resources/terrain_ground.png");
        packFile(packer, "terrain_water", "resources/terrain_water.png");
        packFile(packer, "terrain_rock", "resources/terrain_rock.png");

        // Пока всё уместилось в одну страницу 1024×1024, атлас — это ровно ОДНА
        // текстура: все регионы делят её, и SpriteBatch не сбрасывает пачку.
        atlas = packer.generateTextureAtlas(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        packer.dispose(); // страницы скопированы в текстуры атласа — упаковщик больше не нужен

        regions.clear();
        for (ContentId sprite : index.sprites()) {
            regions.put(sprite, region(regionName(sprite)));
        }
        if (!index.sprites().contains(VanillaSprites.LAB)) {
            regions.put(VanillaSprites.LAB, region(regionName(VanillaSprites.LAB)));
        }
        coalOre = region("coal_ore");
        terrainGround = region("terrain_ground");
        terrainWater = region("terrain_water");
        terrainRock = region("terrain_rock");
    }

    /** Имя запакованного региона для {@code sprite} — сам {@link ContentId#toString()}: уникально, без хвостовых цифр. */
    private static String regionName(ContentId sprite) {
        return sprite.toString();
    }

    /**
     * Перевод логического имени спрайта в текстуру атласа. Про АССЕТЫ (какие пиксели), а не про
     * поведение зданий — здание лишь называет своё имя, а какая именно картинка за ним стоит,
     * знает только этот класс. Нужен и {@link BuildingRenderer} (здание на карте), и {@link
     * HudRenderer} (та же иконка — в панели построек): собран в одном месте, чтобы два разных
     * слоя рисовали ОДНУ и ту же картинку одного и того же здания, а не рассинхронизировались.
     */
    TextureRegion forSprite(ContentId sprite) {
        TextureRegion region = regions.get(sprite);
        if (region == null) {
            throw new IllegalArgumentException("No packed texture for sprite: " + sprite);
        }
        return region;
    }

    /** Спрайт угля на земле (D-05, DEV_TASKS.md) — читает {@link WorldRenderer}, рисуя его поверх клеток с углём. */
    TextureRegion coalOre() {
        return coalOre;
    }

    /** Базовый тайл земли — читает {@link WorldRenderer} вместо плоской заливки цветом. */
    TextureRegion terrainGround() {
        return terrainGround;
    }

    /** Базовый тайл воды — см. {@link #terrainGround}. */
    TextureRegion terrainWater() {
        return terrainWater;
    }

    /** Базовый тайл камня — см. {@link #terrainGround}. */
    TextureRegion terrainRock() {
        return terrainRock;
    }

    /** Регион атласа по имени; отсутствие — ошибка сборки атласа, а не тихий null. */
    private TextureRegion region(String name) {
        TextureRegion r = atlas.findRegion(name);
        if (r == null) {
            throw new IllegalStateException("В атласе нет региона: " + name);
        }
        return r;
    }

    private static void packFile(PixmapPacker packer, String name, String path) {
        Pixmap pixmap = new Pixmap(Gdx.files.internal(path));
        packer.pack(name, pixmap);
        pixmap.dispose(); // пиксели скопированы на страницу упаковщика
    }

    /** Заметная однотонная плашка — см. {@link #build}'s комментарий о лаборатории. */
    private static void packLabPlaceholder(PixmapPacker packer, String name) {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.45f, 0.30f, 0.65f, 1f);
        pixmap.fill();
        packer.pack(name, pixmap);
        pixmap.dispose();
    }

    @Override
    public void dispose() {
        atlas.dispose(); // одна текстура-атлас — одно освобождение
    }
}

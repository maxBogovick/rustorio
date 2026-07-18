package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.core.Item;

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
 * <p>Спрайты лежат в {@code resources/} (оставлены от Rust-версии), фильтр
 * {@link Texture.TextureFilter#Nearest} — иначе пиксель-арт размажется при
 * растягивании до размера клетки. {@link Disposable} обязывает освободить атлас в
 * {@link #dispose()}: libGDX не собирает нативную память GPU сборщиком мусора.
 */
public final class Textures implements Disposable {

    /** Единственная текстура-атлас, куда смотрят все регионы ниже. */
    private final TextureAtlas atlas;

    // Бур: 3 кадра прогресса добычи.
    final TextureRegion[] miner = new TextureRegion[3];
    // Лента: 2 кадра «бегущей дорожки», рисуются с поворотом под направление.
    final TextureRegion[] belt = new TextureRegion[2];
    final TextureRegion chest;
    final TextureRegion furnaceOn;
    final TextureRegion furnaceOff;
    final TextureRegion assembler;
    final TextureRegion splitter;
    final TextureRegion underground;
    /** Плашка-заглушка: нарисованного спрайта лаборатории в resources/ ещё нет. */
    final TextureRegion lab;
    /**
     * Белый квадрат 1×1. Им {@link BuildingRenderer} рисует ПОДПИСАННУЮ ПЛАШКУ для здания,
     * у которого ещё нет своего спрайта: белый регион растягивается на клетку и красится
     * в цвет через {@code batch.setColor}. Так новое здание видно на поле, а графику для
     * него никто не открывал.
     */
    final TextureRegion white;
    private final TextureRegion ironOre;
    private final TextureRegion ironPlate;
    private final TextureRegion gear;
    private final TextureRegion mechanism;

    public Textures() {
        // padding=2 + duplicateBorder: соседние спрайты не «протекают» друг в друга
        // при повороте/растяжении, а край каждого спрайта продлён в отступ.
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, true);
        // Имена БЕЗ завершающих цифр: generateTextureAtlas() разбирает хвостовые
        // цифры имени в «индекс региона» ("miner_1" → name="miner", index=1), и
        // тогда findRegion("miner_1") ничего не находит. Суффиксы-буквы этого избегают.
        packFile(packer, "miner_a", "resources/miner_1.png");
        packFile(packer, "miner_b", "resources/miner_2.png");
        packFile(packer, "miner_c", "resources/miner_3.png");
        packFile(packer, "belt_a", "resources/belt_1.png");
        packFile(packer, "belt_b", "resources/belt_2.png");
        packFile(packer, "chest", "resources/chest.png");
        packFile(packer, "furnace_on", "resources/furnace_on.png");
        packFile(packer, "furnace_off", "resources/furnace_off.png");
        packFile(packer, "assembler", "resources/assembler.png");
        packFile(packer, "splitter", "resources/branch_1.png");
        packFile(packer, "underground", "resources/underground_in.png");
        packLabPlaceholder(packer);
        packWhite(packer);
        packFile(packer, "iron_ore", "resources/iron_ore.png");
        packFile(packer, "iron_plate", "resources/iron_plate.png");
        packFile(packer, "gear", "resources/iron_gear.png");
        packFile(packer, "mechanism", "resources/bronse_gear.png");

        // Пока всё уместилось в одну страницу 1024×1024, атлас — это ровно ОДНА
        // текстура: все регионы делят её, и SpriteBatch не сбрасывает пачку.
        atlas = packer.generateTextureAtlas(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        packer.dispose(); // страницы скопированы в текстуры атласа — упаковщик больше не нужен

        miner[0] = region("miner_a");
        miner[1] = region("miner_b");
        miner[2] = region("miner_c");
        belt[0] = region("belt_a");
        belt[1] = region("belt_b");
        chest = region("chest");
        furnaceOn = region("furnace_on");
        furnaceOff = region("furnace_off");
        assembler = region("assembler");
        splitter = region("splitter");
        underground = region("underground");
        lab = region("lab");
        white = region("white");
        ironOre = region("iron_ore");
        ironPlate = region("iron_plate");
        gear = region("gear");
        // Новый предмет — компилятор ПОТРЕБУЕТ ветку в itemTexture(): «карта задач» в деле.
        mechanism = region("mechanism");
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

    /**
     * Заметная однотонная плашка для лаборатории.
     *
     * <p>Спрайты сплиттера и подземки в {@code resources/} лежат (остались от
     * Rust-версии), а лаборатории — нет. Честнее упаковать заметную заглушку, чем
     * подсунуть чужую картинку и потом гадать, что это за здание.
     */
    private static void packLabPlaceholder(PixmapPacker packer) {
        Pixmap pixmap = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmap.setColor(0.45f, 0.30f, 0.65f, 1f);
        pixmap.fill();
        packer.pack("lab", pixmap);
        pixmap.dispose();
    }

    /** Белый квадрат 1×1 для крашеных плашек (см. поле {@link #white}). */
    private static void packWhite(PixmapPacker packer) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(1f, 1f, 1f, 1f);
        pixmap.fill();
        packer.pack("white", pixmap);
        pixmap.dispose();
    }

    /** Спрайт предмета по его типу. Новый предмет — одна ветка здесь. */
    TextureRegion itemTexture(Item item) {
        return switch (item) {
            case IRON_ORE -> ironOre;
            case IRON_PLATE -> ironPlate;
            case GEAR -> gear;
            case MECHANISM -> mechanism;
        };
    }

    @Override
    public void dispose() {
        atlas.dispose(); // одна текстура-атлас — одно освобождение
    }
}

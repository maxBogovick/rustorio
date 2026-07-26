package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.domain.Sprite;

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

    private final TextureRegion miner;
    // Лента: 2 кадра «бегущей дорожки», рисуются с поворотом под направление.
    private final TextureRegion[] belt = new TextureRegion[2];
    private final TextureRegion chest;
    private final TextureRegion furnaceOn;
    private final TextureRegion furnaceOff;
    private final TextureRegion splitter;
    private final TextureRegion undergroundIn;
    private final TextureRegion undergroundOut;
    /** Плашка-заглушка: нарисованного спрайта лаборатории в resources/ ещё нет. */
    private final TextureRegion lab;

    public Textures() {
        // padding=2 + duplicateBorder: соседние спрайты не «протекают» друг в друга
        // при повороте/растяжении, а край каждого спрайта продлён в отступ.
        PixmapPacker packer = new PixmapPacker(1024, 1024, Pixmap.Format.RGBA8888, 2, true);
        // Имена БЕЗ завершающих цифр: generateTextureAtlas() разбирает хвостовые
        // цифры имени в «индекс региона» ("miner_1" → name="miner", index=1), и
        // тогда findRegion("miner_1") ничего не находит. Суффиксы-буквы этого избегают.
        // Только первый кадр бура упакован — {@link #forSprite} не анимирует его (см. P4-01,
        // BUG_FIX_PROGRESS.md); остальные кадры и resources/assembler.png сейчас нигде не читаются.
        packFile(packer, "miner_a", "resources/miner_1.png");
        packFile(packer, "belt_a", "resources/belt_1.png");
        packFile(packer, "belt_b", "resources/belt_2.png");
        packFile(packer, "chest", "resources/chest.png");
        packFile(packer, "furnace_on", "resources/furnace_on.png");
        packFile(packer, "furnace_off", "resources/furnace_off.png");
        packFile(packer, "splitter", "resources/branch_1.png");
        packFile(packer, "underground_a", "resources/underground_in.png");
        packFile(packer, "underground_b", "resources/underground_out.png");
        packLabPlaceholder(packer);
        // Спрайты предметов (iron_ore.png и т.п.) сознательно НЕ упакованы: это заготовки
        // 3×4/4×4 пикселя, неотличимые друг от друга на глаз — груз рисует {@link ItemRenderer}
        // кружком через ShapeRenderer, настоящая художка для предметов не нужна.

        // Пока всё уместилось в одну страницу 1024×1024, атлас — это ровно ОДНА
        // текстура: все регионы делят её, и SpriteBatch не сбрасывает пачку.
        atlas = packer.generateTextureAtlas(
                Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest, false);
        packer.dispose(); // страницы скопированы в текстуры атласа — упаковщик больше не нужен

        miner = region("miner_a");
        belt[0] = region("belt_a");
        belt[1] = region("belt_b");
        chest = region("chest");
        furnaceOn = region("furnace_on");
        furnaceOff = region("furnace_off");
        splitter = region("splitter");
        undergroundIn = region("underground_a");
        undergroundOut = region("underground_b");
        lab = region("lab");
    }

    /**
     * Перевод логического имени спрайта в текстуру атласа. Про АССЕТЫ (какие пиксели), а не про
     * поведение зданий — здание лишь называет своё имя ({@link Sprite}), а какая именно картинка
     * за ним стоит, знает только этот класс. Нужен и {@link BuildingRenderer} (здание на карте),
     * и {@link HudRenderer} (та же иконка — в панели построек): собран в одном месте, чтобы два
     * разных слоя рисовали ОДНУ и ту же картинку одного и того же здания, а не рассинхронизировались.
     */
    TextureRegion forSprite(Sprite sprite) {
        return switch (sprite) {
            case MINER -> miner;
            case CHEST -> chest;
            case FURNACE_HOT -> furnaceOn;
            case FURNACE_COLD -> furnaceOff;
            case BELT_EMPTY -> belt[0];
            case BELT_FULL -> belt[1];
            case SPLITTER -> splitter;
            case UNDERGROUND_IN -> undergroundIn;
            case UNDERGROUND_OUT -> undergroundOut;
            case LAB -> lab;
        };
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

    @Override
    public void dispose() {
        atlas.dispose(); // одна текстура-атлас — одно освобождение
    }
}

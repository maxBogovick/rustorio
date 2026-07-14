package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.core.Item;

/**
 * Все спрайты игры, загруженные ОДИН раз при старте.
 *
 * <p>Текстуры — это ресурсы GPU, их нельзя грузить каждый кадр (утечка памяти и
 * тормоза), поэтому грузим один раз здесь и держим до выхода. {@link Disposable}
 * обязывает освободить их в {@link #dispose()} — libGDX сам не собирает нативную
 * память сборщиком мусора.
 *
 * <p>Спрайты лежат в {@code resources/} (оставлены от Rust-версии), фильтр
 * {@link Texture.TextureFilter#Nearest} — иначе пиксель-арт размажется при
 * растягивании до размера клетки.
 */
public final class Textures implements Disposable {

    // Бур: 3 кадра прогресса добычи.
    final Texture[] miner = new Texture[3];
    // Лента: 2 кадра «бегущей дорожки». TextureRegion — чтобы рисовать с
    // поворотом под направление.
    final TextureRegion[] belt = new TextureRegion[2];
    final Texture chest;
    final Texture furnaceOn;
    final Texture furnaceOff;
    final Texture assembler;
    final TextureRegion splitter;
    final TextureRegion underground;
    /** Плашка-заглушка: нарисованного спрайта лаборатории в resources/ ещё нет. */
    final Texture lab;
    private final Texture ironOre;
    private final Texture ironPlate;
    private final Texture gear;
    private final Texture mechanism;

    public Textures() {
        miner[0] = load("resources/miner_1.png");
        miner[1] = load("resources/miner_2.png");
        miner[2] = load("resources/miner_3.png");
        belt[0] = new TextureRegion(load("resources/belt_1.png"));
        belt[1] = new TextureRegion(load("resources/belt_2.png"));
        chest = load("resources/chest.png");
        furnaceOn = load("resources/furnace_on.png");
        furnaceOff = load("resources/furnace_off.png");
        assembler = load("resources/assembler.png");
        splitter = new TextureRegion(load("resources/branch_1.png"));
        underground = new TextureRegion(load("resources/underground_in.png"));
        lab = solidColor(0.45f, 0.30f, 0.65f);
        ironOre = load("resources/iron_ore.png");
        ironPlate = load("resources/iron_plate.png");
        gear = load("resources/iron_gear.png");
        // Новый предмет — компилятор ПОТРЕБОВАЛ ветку в itemTexture(): «карта задач» в деле.
        mechanism = load("resources/bronse_gear.png");
    }

    /**
     * Однотонная плашка, нарисованная в памяти.
     *
     * <p>Нужна лаборатории: спрайты сплиттера и подземки в {@code resources/} лежат
     * (остались от Rust-версии), а лаборатории — нет. Честнее нарисовать заметную
     * заглушку, чем подсунуть чужую картинку и потом гадать, что это за здание.
     */
    private static Texture solidColor(float r, float g, float b) {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(r, g, b, 1f);
        pixmap.fill();
        Texture texture = new Texture(pixmap);
        pixmap.dispose(); // Pixmap живёт в обычной памяти, текстура уже уехала в GPU
        return texture;
    }

    private static Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        return texture;
    }

    /** Спрайт предмета по его типу. Новый предмет — одна ветка здесь. */
    Texture itemTexture(Item item) {
        return switch (item) {
            case IRON_ORE -> ironOre;
            case IRON_PLATE -> ironPlate;
            case GEAR -> gear;
            case MECHANISM -> mechanism;
        };
    }

    @Override
    public void dispose() {
        for (Texture t : miner) {
            t.dispose();
        }
        for (TextureRegion r : belt) {
            r.getTexture().dispose();
        }
        chest.dispose();
        furnaceOn.dispose();
        furnaceOff.dispose();
        assembler.dispose();
        splitter.getTexture().dispose();
        underground.getTexture().dispose();
        lab.dispose();
        ironOre.dispose();
        ironPlate.dispose();
        gear.dispose();
        mechanism.dispose();
    }
}

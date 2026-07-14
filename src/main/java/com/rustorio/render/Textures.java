package com.rustorio.render;

import com.badlogic.gdx.Gdx;
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
    private final Texture ironOre;
    private final Texture ironPlate;
    private final Texture gear;

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
        ironOre = load("resources/iron_ore.png");
        ironPlate = load("resources/iron_plate.png");
        gear = load("resources/iron_gear.png");
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
        ironOre.dispose();
        ironPlate.dispose();
        gear.dispose();
    }
}

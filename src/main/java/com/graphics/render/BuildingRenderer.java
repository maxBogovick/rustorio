package com.graphics.render;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.graphics.GfxConfig;
import com.rustorio.Appearance;
import com.rustorio.BuildingType;
import com.rustorio.Sprite;
import com.rustorio.World;
import com.rustorio.core.Direction;
import com.rustorio.model.Building;

/**
 * Слой «здания»: рисует всё, что стоит на карте, читая мир и ничего в нём не меняя.
 *
 * <p>Два правила этого слоя, ради ленты и сплиттера:
 * <ul>
 *   <li>спрайты нарисованы «смотрящими на восток» — поворот берётся из
 *       {@link Building#direction()}, см. {@link #rotation};
 *   <li>лента анимирована: два кадра «бегущей дорожки» сменяются по общей для всех лент
 *       фазе, чтобы соседние клетки бежали в ногу.
 * </ul>
 */
final class BuildingRenderer {

    private final SpriteBatch batch;
    private final Textures textures;
    private final BitmapFont font;
    private final Grid grid;

    /**
     * Фаза анимации ленты в секундах. Одна на весь слой: если считать её для каждой ленты
     * отдельно, соседние клетки разъедутся по фазе и линия «замерцает».
     */
    private float beltPhase;

    BuildingRenderer(SpriteBatch batch, Textures textures, Grid grid, BitmapFont font) {
        this.batch = batch;
        this.textures = textures;
        this.grid = grid;
        this.font = font;
    }

    void render(World world, float delta) {
        float tile = GfxConfig.TILE;

        // Фаза держится в пределах одного цикла анимации — иначе за долгую сессию float
        // потеряет точность и лента начнёт дёргаться.
        float cycle = GfxConfig.BELT_FRAME_SECONDS * textures.belt.length;
        beltPhase = (beltPhase + delta) % cycle;
        final int beltFrame = (int) (beltPhase / GfxConfig.BELT_FRAME_SECONDS);

        batch.begin();
        world.forEachBuilding((x, y, building) -> {
            Appearance look = building.appearance();
            TextureRegion region = sprite(building, look.sprite(), beltFrame);
            float px = grid.x(x);
            float py = grid.yBottom(y);
            float angle = rotation(building);

            if (angle == 0f) {
                batch.draw(region, px, py, tile, tile);
            } else {
                // Крутим вокруг центра клетки, иначе повёрнутая лента уедет к соседям.
                batch.draw(region, px, py, tile / 2f, tile / 2f, tile, tile, 1f, 1f, angle);
            }

            if (look.hasBadge()) {
                font.draw(batch, Integer.toString(look.badge()), px + 3, py + tile - 3);
            }
        });
        batch.end();
    }


    public static String hotbar(BuildingType selected) {
        StringBuilder sb = new StringBuilder("Build:   ");
        for (BuildingType type : BuildingType.values()) {       // список строит сам себя
            int number = type.ordinal() + 1;
            if (type == selected) sb.append("[ ").append(number).append(' ').append(type.label()).append(" ]    ");
            else                  sb.append("  ").append(number).append(' ').append(type.label()).append("     ");
        }
        return sb.toString();
    }

    /**
     * Спрайт здания.
     *
     * <p>Сплиттер отбирается ПО ТИПУ, а не по {@link Sprite}: в модели он наследник ленты и
     * потому приходит с её {@code BELT_*}. Тип — единственное, чем он от ленты отличается,
     * пока у него нет собственной внешности.
     */
    private TextureRegion sprite(Building building, Sprite sprite, int beltFrame) {
        if (building.type() == BuildingType.SPLITTER) {
            return textures.splitter;
        }
        return switch (sprite) {
            case MINER        -> textures.miner[0];
            case CHEST        -> textures.chest;
            case FURNACE_HOT  -> textures.furnaceOn;
            case FURNACE_COLD -> textures.furnaceOff;
            // Пустая и гружёная лента делят одну «дорожку»: отдельного спрайта гружёной ленты
            // в resources/ нет, а предметы поверх ленты — работа ItemRenderer.
            case BELT_EMPTY, BELT_FULL -> textures.belt[beltFrame];
        };
    }

    /**
     * Угол поворота спрайта в градусах против часовой стрелки (как их понимает
     * {@code SpriteBatch}).
     *
     * <p>Считается из вектора направления, а не по таблице «EAST → 0, NORTH → 90»: у модели ось
     * Y растёт ВНИЗ экрана (тот же переворот делает {@link Grid}), поэтому экранный вектор
     * направления — это {@code (dx, -dy)}. Так графика не спорит с моделью о том, где верх:
     * куда здание отдаёт предмет, туда и смотрит спрайт.
     *
     * <p>Здание без направления (ящик) рисуется как есть — угол 0.
     */
    private static float rotation(Building building) {
        Direction dir = building.direction().orElse(Direction.EAST);
        return (float) Math.toDegrees(Math.atan2(-dir.dy(), dir.dx()));
    }
}

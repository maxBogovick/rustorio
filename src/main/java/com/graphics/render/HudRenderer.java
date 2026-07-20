package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.BuildingType;
import com.rustorio.Item;
import com.rustorio.ProductionStats;

/**
 * HUD — текст, прибитый к «стеклу» окна (рисуется в оконных координатах, зум/скролл его не
 * трогают): заголовок, панель постройки, статистика производства и подсказки управления.
 *
 * <p>Панель постройки и строка статистики — оба элемента интерфейса, которые ОТРАЖАЮТ состояние
 * игры, а не просто висят. Панель читает, что выбрал игрок ({@link BuildingType}); строка
 * статистики — сколько всего произведено ({@link ProductionStats}). Обе строятся из
 * {@code values()} своего enum — добавится новый сорт здания или предмета, он появится сам, без
 * правок здесь.
 */
final class HudRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;

    HudRenderer(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;
    }

    void render(BuildingType selected, ProductionStats stats) {
        float top = Gdx.graphics.getHeight();
        batch.begin();

        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", 20, top - 16);

        // Панель постройки: выбранный пункт — в скобках [ ].
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        font.draw(batch, hotbar(selected), 20, top - 44);

        // Статистика производства: сколько всего добыто/выплавлено с начала игры.
        font.draw(batch, produced(stats), 20, top - 66);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.9f);
        font.draw(batch, "1-5 select   LMB build   RMB remove   F5 save   F9 load   WASD pan   wheel zoom",
                20, top - 88);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** Строка панели: «Build: [ 1 Miner ] 2 Chest 3 Furnace» — выбранное в скобках. */
    private static String hotbar(BuildingType selected) {
        StringBuilder sb = new StringBuilder("Build:   ");
        for (BuildingType type : BuildingType.values()) {
            int number = type.ordinal() + 1;
            if (type == selected) {
                sb.append("[ ").append(number).append(' ').append(type.label()).append(" ]    ");
            } else {
                sb.append("  ").append(number).append(' ').append(type.label()).append("     ");
            }
        }
        return sb.toString();
    }

    /** Строка статистики: «Produced:   IRON_ORE 12    IRON_PLATE 4». */
    private static String produced(ProductionStats stats) {
        StringBuilder sb = new StringBuilder("Produced:   ");
        for (Item item : Item.values()) {
            sb.append(item.name()).append(' ').append(stats.total(item)).append("    ");
        }
        return sb.toString();
    }
}

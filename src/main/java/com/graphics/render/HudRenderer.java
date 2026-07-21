package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.BuildingType;
import com.rustorio.Direction;
import com.rustorio.Item;
import com.rustorio.ProductionLog;
import com.rustorio.ProductionStats;

/**
 * HUD — текст, прибитый к «стеклу» окна (рисуется в оконных координатах, зум/скролл его не
 * трогают): заголовок, панель постройки, статистика производства, лог последних событий и
 * подсказки управления.
 *
 * <p>Строка статистики и строка лога читают РАЗНЫХ, ничего не знающих друг о друге слушателей
 * одного и того же события «предмет произведён» ({@link ProductionStats}, {@link ProductionLog}
 * — урок 14). HUD дальше про них ничего не знает: просто читает и показывает.
 */
final class HudRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;

    HudRenderer(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;
    }

    void render(BuildingType selected, Direction facing, ProductionStats stats, ProductionLog log) {
        float top = Gdx.graphics.getHeight();
        batch.begin();

        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", 20, top - 16);

        // Панель постройки: выбранный пункт — в скобках [ ]; направление — важно только ленте.
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        font.draw(batch, hotbar(selected) + "  Facing: " + facing.name(), 20, top - 44);

        // Статистика производства: сколько всего добыто/выплавлено с начала игры.
        font.draw(batch, produced(stats), 20, top - 66);

        // Лог последних событий: независимый от статистики слушатель того же события.
        font.draw(batch, recent(log), 20, top - 88);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.9f);
        font.draw(batch,
                "1-6 select   R rotate   LMB build   RMB remove   U upgrade   "
                        + "Ctrl+Z undo   Ctrl+Y redo   F5 save   F9 load   WASD pan   wheel zoom",
                20, top - 110);

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

    /** Строка лога: «Recent:   IRON_ORE  IRON_PLATE  IRON_ORE» — самый свежий слева. */
    private static String recent(ProductionLog log) {
        StringBuilder sb = new StringBuilder("Recent:   ");
        if (log.recent().isEmpty()) {
            return sb.append('-').toString();
        }
        for (Item item : log.recent()) {
            sb.append(item.name()).append("  ");
        }
        return sb.toString();
    }
}

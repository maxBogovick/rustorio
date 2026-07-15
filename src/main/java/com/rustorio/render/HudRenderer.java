package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.core.Tech;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;
import com.rustorio.model.Technology;

/**
 * HUD — текст, прибитый к «стеклу» окна. Рисуется отдельной матрицей
 * ({@link GameCamera#hudMatrix()}), поэтому зум и скролл камеры его не двигают.
 */
final class HudRenderer {

    private final SpriteBatch batch;
    private final BitmapFont font;

    HudRenderer(SpriteBatch batch, BitmapFont font) {
        this.batch = batch;
        this.font = font;
    }

    void render(GameState game) {
        float top = Gdx.graphics.getHeight();
        batch.begin();

        String status = "Tool: " + game.tool().displayName()
                + "   Dir: " + game.direction().shortName()
                + "   Undo: " + game.undoDepth()
                + (game.isPaused() ? "   [PAUSED]" : "");
        font.setColor(Color.WHITE);
        font.getData().setScale(1.15f);
        font.draw(batch, status, 20, top - 16);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.9f);
        // Панель собирается из СПИСКА инструментов: добавили здание — подсказка
        // обновилась сама. Раньше эта строка была захардкожена и врала бы.
        StringBuilder hints = new StringBuilder();
        for (Tool t : Tool.values()) {
            hints.append(t.hotkeySlot()).append(' ').append(t.displayName()).append("  ");
        }
        hints.append("   |    LMB place   RMB remove   R rotate   Ctrl+Z/Y undo/redo"
                + "   F5 save   F9 load   Space pause   WASD/MMB pan   wheel zoom");
        font.draw(batch, hints.toString(), 20, top - 46);

        // Строка исследований: очки и состояние каждой технологии. Собирается из ДАННЫХ
        // (Tech.values() + таблица Technology), поэтому новая технология появится тут сама.
        var research = game.research();
        StringBuilder techs = new StringBuilder("Science: " + research.points() + "    ");
        for (Tech tech : Tech.values()) {
            Technology technology = Technology.of(tech);
            String state;
            if (research.isUnlocked(tech)) {
                state = "OK";
            } else if (research.canResearch(tech)) {
                state = "ready";
            } else {
                state = String.valueOf(technology.cost());
            }
            techs.append('F').append(tech.ordinal() + 1).append(' ')
                    .append(tech.displayName()).append(" [").append(state).append("]   ");
        }
        font.setColor(Palette.HINT);
        font.draw(batch, techs.toString(), 20, top - 66);
        font.getData().setScale(1f);

        batch.end();
    }
}

package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.rustorio.core.Tool;
import com.rustorio.game.GameState;

/**
 * HUD — текст, прибитый к «стеклу» окна. Рисуется отдельной матрицей
 * ({@link GameCamera#hudMatrix()}), поэтому зум и скролл камеры его не двигают.
 *
 * <p>По ходу курса сюда добавятся: панель инструментов, счётчик отмен,
 * строка исследований.
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

        // Клетка под курсором — живое доказательство, что камера и пик работают.
        String cell = game.hover()
                .map(c -> "(" + c.x() + ", " + c.y() + ")")
                .orElse("—");
        String status = "Tool: " + game.tool().displayName()
                + "   Dir: " + game.direction().shortName()
                + "   Cell: " + cell
                + (game.isPaused() ? "   [PAUSED]" : "");
        font.setColor(Color.WHITE);
        font.getData().setScale(1.15f);
        font.draw(batch, status, 20, top - 16);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.9f);
        // Панель собирается из СПИСКА инструментов: добавили здание — подсказка
        // обновилась сама. Захардкоженная строка врала бы уже через две лекции.
        StringBuilder hints = new StringBuilder();
        for (Tool t : Tool.values()) {
            hints.append(t.hotkeySlot()).append(' ').append(t.displayName()).append("  ");
        }
        hints.append("   |    R rotate   Space pause   WASD/MMB pan   wheel zoom");
        font.draw(batch, hints.toString(), 20, top - 46);
        font.getData().setScale(1f);

        batch.end();
    }
}

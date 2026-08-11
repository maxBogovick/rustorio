package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.ResearchView;
import com.rustorio.api.content.model.TechType;
import java.util.List;
import java.util.StringJoiner;

/**
 * Tech tree: a translucent panel listing every {@link TechType}, its cost, its prerequisites, and
 * whether it's unlocked, affordable-but-locked-behind-a-prerequisite, or actually unlockable right
 * now — opened and closed with T ({@code com.graphics.input.InputHandler}), which also reads
 * digits 1-9 while this is open to actually spend points on one (P-02, DEV_TASKS.md).
 *
 * <p>Replaces the single "Research: N pts   Next: X   Unlocked: ..." HUD line as the detailed
 * view — that line still exists for an at-a-glance point total, but this is where cost, structure,
 * and progress actually live now, per this task's own acceptance criterion.
 */
final class TechTreeRenderer {

    private static final float PADDING = 24f;
    private static final float TITLE_HEIGHT = 30f;
    private static final float ROW_HEIGHT = 30f;
    private static final float PANEL_WIDTH = 560f;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    TechTreeRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(ResearchView research) {
        // Живой реестр, а не фиксированный ванильный список: панель, обходящая
        // встроенные технологии, молча не показала бы ни одной модовой.
        List<TechType> techs = research.techs().iterate();
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * techs.size();
        float panelX = (screenW - PANEL_WIDTH) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float firstRowY = panelY + panelH - PADDING - TITLE_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Tech tree  (T to close)   " + research.points() + " pts banked",
                panelX + PADDING, panelY + panelH - PADDING);

        font.getData().setScale(0.8f);
        float y = firstRowY;
        for (int i = 0; i < techs.size(); i++) {
            TechType tech = techs.get(i);
            font.setColor(rowColor(research, tech));
            font.draw(batch, describe(research, tech, i + 1), panelX + PADDING, y);
            y -= ROW_HEIGHT;
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** Green — unlocked. White — unlockable right now (afford it, prerequisites met). Hint-grey — still locked. */
    private static Color rowColor(ResearchView research, TechType tech) {
        if (research.isUnlocked(tech.id())) {
            return Palette.WORKING;
        }
        return isUnlockableNow(research, tech) ? Color.WHITE : Palette.HINT;
    }

    private static boolean isUnlockableNow(ResearchView research, TechType tech) {
        return research.points() >= tech.cost() && research.unlocked().containsAll(tech.prerequisites());
    }

    /** "[3] Big buffers  35 pts  (needs: Fast mining)  — UNLOCKED" and similar, one line per tech. */
    private static String describe(ResearchView research, TechType tech, int hotkey) {
        StringBuilder line = new StringBuilder();
        line.append('[').append(hotkey).append("] ").append(tech.label())
                .append("  ").append(tech.cost()).append(" pts");
        if (!tech.prerequisites().isEmpty()) {
            StringJoiner names = new StringJoiner(", ");
            for (ContentId prerequisite : tech.prerequisites()) {
                // Подпись, если технология-предшественник существует; иначе сам id —
                // мод мог назвать чужую технологию, которой в этой сборке нет.
                names.add(research.techs().peek(prerequisite)
                        .map(TechType::label).orElseGet(prerequisite::toString));
            }
            line.append("  (needs: ").append(names).append(')');
        }
        if (research.isUnlocked(tech.id())) {
            line.append("  — UNLOCKED");
        } else if (isUnlockableNow(research, tech)) {
            line.append("  — press ").append(hotkey).append(" to unlock");
        }
        return line.toString();
    }
}

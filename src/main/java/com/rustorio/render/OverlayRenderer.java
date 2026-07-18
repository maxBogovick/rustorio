package com.rustorio.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.core.Config;
import com.rustorio.game.view.ConnLine;
import com.rustorio.game.view.Corner;
import com.rustorio.game.view.HudPanel;
import com.rustorio.game.view.Overlay;
import com.rustorio.game.view.PanelRow;
import com.rustorio.game.view.TileHighlight;
import com.rustorio.game.view.Toast;
import com.rustorio.game.view.WorldLabel;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Рисует «слой поверх мира» ({@link Overlay}) обобщённо: подсветки клеток, линии-связи,
 * метки над клетками, HUD-панели и уведомления. Тип каждого элемента графике неизвестен — она
 * рисует ДАННЫЕ, которые положил {@code game}. Поэтому новая визуализация в будущем уроке не
 * требует правок здесь: достаточно наполнить {@code Overlay}.
 *
 * <p>Два прохода в разных системах координат: подсветки, линии и метки — в мировой (через
 * камеру); панели и тосты — в оконной (HUD). Матрицы переключает {@link Renderer}.
 */
final class OverlayRenderer {

    private static final float TILE = Config.TILE;
    private static final float LINE_H = 18f;
    private static final float MARGIN = 12f;
    private static final float PANEL_W = 220f;
    private static final float ICON = 14f;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;
    private final Grid grid;

    OverlayRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures,
            Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
        this.grid = grid;
    }

    /** Мировой проход: подсветки, линии, метки. Матрица уже камеры (её ставит Renderer). */
    void renderWorld(Overlay overlay) {
        drawHighlights(overlay.highlights());
        drawLines(overlay.lines());
        drawLabels(overlay.labels());
    }

    /** HUD-проход: панели и тосты. Матрица уже оконная (её ставит Renderer). */
    void renderHud(Overlay overlay) {
        batch.begin();
        drawPanels(overlay.panels());
        drawToasts(overlay.toasts());
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawHighlights(List<TileHighlight> highlights) {
        if (highlights.isEmpty()) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (TileHighlight h : highlights) {
            shapes.setColor(Palette.tint(h.tint()));
            shapes.rect(grid.x(h.x()), grid.yBottom(h.y()), TILE, TILE);
        }
        shapes.end();
    }

    private void drawLines(List<ConnLine> lines) {
        if (lines.isEmpty()) {
            return;
        }
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ConnLine l : lines) {
            shapes.setColor(Palette.tintStrong(l.tint()));
            shapes.line(grid.centerX(l.x1()), grid.centerY(l.y1()),
                    grid.centerX(l.x2()), grid.centerY(l.y2()));
        }
        shapes.end();
    }

    private void drawLabels(List<WorldLabel> labels) {
        if (labels.isEmpty()) {
            return;
        }
        batch.begin();
        font.getData().setScale(0.7f);
        for (WorldLabel label : labels) {
            font.setColor(Palette.tintStrong(label.tint()));
            // Над клеткой: её верх — yBottom + TILE, чуть выше — плавающая метка.
            font.draw(batch, label.text(), grid.x(label.x()) + 2f, grid.yBottom(label.y()) + TILE + 14f);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    private void drawPanels(List<HudPanel> panels) {
        float w = Gdx.graphics.getWidth();
        float h = Gdx.graphics.getHeight();
        // Смещение уже занятой высоты по каждому углу — чтобы панели не наезжали друг на друга.
        Map<Corner, Float> used = new EnumMap<>(Corner.class);
        for (HudPanel panel : panels) {
            Corner corner = panel.corner();
            float offset = used.getOrDefault(corner, 0f);
            float blockH = (panel.rows().size() + 1) * LINE_H;

            boolean right = corner == Corner.TOP_RIGHT || corner == Corner.BOTTOM_RIGHT;
            boolean top = corner == Corner.TOP_LEFT || corner == Corner.TOP_RIGHT;
            float x = right ? w - PANEL_W - MARGIN : MARGIN;
            float titleY = top ? h - MARGIN - offset : MARGIN + blockH + offset;

            font.setColor(Palette.HINT);
            font.draw(batch, panel.title(), x, titleY);
            font.setColor(Color.WHITE);
            float y = titleY - LINE_H;
            for (PanelRow row : panel.rows()) {
                if (row.icon() != null) {
                    batch.setColor(Color.WHITE);
                    batch.draw(textures.itemTexture(row.icon()), x, y - ICON + 2f, ICON, ICON);
                    font.draw(batch, row.text(), x + ICON + 4f, y);
                } else {
                    font.draw(batch, row.text(), x, y);
                }
                y -= LINE_H;
            }
            used.put(corner, offset + blockH + LINE_H);
        }
    }

    private void drawToasts(List<Toast> toasts) {
        float y = 46f;
        for (Toast toast : toasts) {
            float alpha = Math.min(1f, toast.remaining()); // последняя секунда — плавное затухание
            font.setColor(1f, 1f, 1f, alpha);
            font.draw(batch, toast.text(), 20f, y);
            y += LINE_H;
        }
    }
}

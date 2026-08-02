package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import java.util.List;

/**
 * Draws {@link PauseMenuView} on top of the (frozen) game world — same panel-with-rows shape as
 * {@link RecipeBookRenderer}/{@link StatsScreenRenderer}, sharing {@link Renderer}'s own {@code
 * batch}/{@code shapes}/{@code font} rather than owning its own the way {@link MenuRenderer} does
 * for the standalone main menu screen: this one draws INTO an already-running frame (no {@code
 * glClear}, no projection matrix of its own — {@link Renderer#render} already set the HUD one
 * before calling this, same as every other panel it composes).
 */
final class PauseMenuRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    PauseMenuRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(PauseMenuView view) {
        boolean editingName = view.textEntry() != null;
        List<String> items = editingName ? List.of("Name: " + view.textEntry() + "_") : view.items();

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        boolean hasStatus = view.status() != null;
        float panelW = MenuLayout.PANEL_WIDTH;
        float panelH = MenuLayout.panelHeight(items.size(), hasStatus);
        float panelX = MenuLayout.panelX(screenW);
        float panelY = MenuLayout.panelY(screenH, items.size(), hasStatus);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, panelW, panelH);
        if (!editingName) {
            for (int i = 0; i < items.size(); i++) {
                if (i == view.selectedIndex()) {
                    shapes.setColor(Palette.SLOT_SELECTED);
                    shapes.rect(panelX + 8f, MenuLayout.rowBandBottom(panelY, panelH, i), panelW - 16f, MenuLayout.ROW_HEIGHT - 4f);
                } else if (i == view.hoverIndex()) {
                    shapes.setColor(Palette.SLOT_BG);
                    shapes.rect(panelX + 8f, MenuLayout.rowBandBottom(panelY, panelH, i), panelW - 16f, MenuLayout.ROW_HEIGHT - 4f);
                }
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        batch.begin();
        font.getData().setScale(1.15f);
        font.setColor(Color.WHITE);
        font.draw(batch, view.title(), panelX + MenuLayout.PADDING, panelY + panelH - MenuLayout.PADDING);

        font.getData().setScale(0.95f);
        for (int i = 0; i < items.size(); i++) {
            font.setColor(!editingName && i == view.selectedIndex() ? Color.BLACK : Color.WHITE);
            font.draw(batch, items.get(i), panelX + MenuLayout.PADDING, MenuLayout.rowY(panelY, panelH, i));
        }
        if (hasStatus) {
            font.setColor(Palette.HINT);
            font.draw(batch, view.status(), panelX + MenuLayout.PADDING, panelY + MenuLayout.PADDING - 4f);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }
}

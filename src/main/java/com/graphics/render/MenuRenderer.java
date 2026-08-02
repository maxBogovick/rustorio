package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Disposable;
import com.graphics.GfxConfig;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A single-column selectable list on a centered panel — {@code MainMenuScreen}'s only visual, and
 * (a later card) the in-game pause menu's. Deliberately generic (plain label strings, no domain
 * type in its signature): both the map list and the save list format their own rows before calling
 * this, the same way {@link Renderer} composes several such single-purpose drawers rather than one
 * class knowing every screen's content.
 *
 * <p>Owns its own {@link SpriteBatch}/{@link ShapeRenderer}/{@link BitmapFont} and a fixed
 * screen-space projection updated on {@link #resize} — unlike {@link Renderer}, there is no world
 * camera here at all: a menu has nothing to zoom or pan, only the window's own pixels (same reason
 * {@link GameCamera#hudMatrix()} exists as a separate, camera-independent matrix).
 */
public final class MenuRenderer implements Disposable {

    private final SpriteBatch batch = new SpriteBatch();
    private final ShapeRenderer shapes = new ShapeRenderer();
    private final BitmapFont font = new BitmapFont();
    private final Matrix4 projection = new Matrix4();

    public MenuRenderer() {
        resize(GfxConfig.WINDOW_W, GfxConfig.WINDOW_H);
    }

    public void resize(int width, int height) {
        projection.setToOrtho2D(0, 0, width, height);
    }

    /**
     * @param title         panel heading
     * @param items         one label per row, top to bottom
     * @param selectedIndex keyboard-selected row (amber fill), or {@code -1} for none
     * @param hoverIndex    mouse-hovered row (dim fill), or {@code -1} for none
     * @param status        an extra line under the list (e.g. a load failure reason), or {@code null}
     */
    public void render(String title, List<String> items, int selectedIndex, int hoverIndex, @Nullable String status) {
        Gdx.gl.glClearColor(Palette.BG.r, Palette.BG.g, Palette.BG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        batch.setProjectionMatrix(projection);
        shapes.setProjectionMatrix(projection);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        boolean hasStatus = status != null;
        float panelW = MenuLayout.PANEL_WIDTH;
        float panelH = MenuLayout.panelHeight(items.size(), hasStatus);
        float panelX = MenuLayout.panelX(screenW);
        float panelY = MenuLayout.panelY(screenH, items.size(), hasStatus);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, panelW, panelH);
        for (int i = 0; i < items.size(); i++) {
            if (i == selectedIndex) {
                shapes.setColor(Palette.SLOT_SELECTED);
                shapes.rect(panelX + 8f, MenuLayout.rowBandBottom(panelY, panelH, i), panelW - 16f, MenuLayout.ROW_HEIGHT - 4f);
            } else if (i == hoverIndex) {
                shapes.setColor(Palette.SLOT_BG);
                shapes.rect(panelX + 8f, MenuLayout.rowBandBottom(panelY, panelH, i), panelW - 16f, MenuLayout.ROW_HEIGHT - 4f);
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
        font.draw(batch, title, panelX + MenuLayout.PADDING, panelY + panelH - MenuLayout.PADDING);

        font.getData().setScale(0.95f);
        for (int i = 0; i < items.size(); i++) {
            font.setColor(i == selectedIndex ? Color.BLACK : Color.WHITE);
            font.draw(batch, items.get(i), panelX + MenuLayout.PADDING, MenuLayout.rowY(panelY, panelH, i));
        }
        if (hasStatus) {
            font.setColor(Palette.HINT);
            font.draw(batch, status, panelX + MenuLayout.PADDING, panelY + MenuLayout.PADDING - 4f);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        shapes.dispose();
        font.dispose();
    }
}

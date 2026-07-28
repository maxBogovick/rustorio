package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.domain.Item;
import com.rustorio.domain.world.ProductionStatsView;
import com.rustorio.domain.world.ProductionStatsView.RateSample;
import java.util.List;

/**
 * Stats screen (P-03, DEV_TASKS.md): opened and closed with V ({@code
 * com.graphics.input.InputHandler}), which also reads N while this is open to switch which item
 * the bar graph is showing.
 *
 * <p>Replaces the lifetime-total-only HUD line as the real production instrument — the design
 * audit's own point (§4.1): a total produced since the game started says nothing about whether a
 * line is CURRENTLY keeping up, only items/minute does. Every item's row shows its own smoothed
 * rate at a glance (numbers don't need a shared visual scale, so all eleven fit without one
 * squashing another); the graph — one item at a time, switched with N — is where the audit's
 * actual "a mass-produced item flattens the rest on a shared graph" complaint (a direct Factorio
 * wiki citation) gets solved: single-item, not overlaid multi-series.
 *
 * <p>Both the rate and the graph read {@link ProductionStatsView#ratePerMinute}/{@link
 * ProductionStatsView#history}, which bucket by {@code World.currentTick()} (D-06), never
 * wall-clock time — the speed multiplier (1×/2×/4×) changes how fast ticks arrive, not what one
 * tick means, so neither number distorts when the player changes it (the card's own acceptance
 * criterion).
 */
final class StatsScreenRenderer {

    private static final float PADDING = 24f;
    private static final float TITLE_HEIGHT = 30f;
    private static final float ROW_HEIGHT = 22f;
    private static final float PANEL_WIDTH = 720f;
    private static final float GRAPH_HEIGHT = 200f;

    /** Averaging window for each row's displayed rate — 10 simulated seconds: responsive, but not jumping on every single production event. */
    private static final long LIST_WINDOW_TICKS = 600;
    /** How many of the most recent buckets the graph shows — at {@code BUCKET_TICKS=60}, one simulated minute. */
    private static final int GRAPH_BUCKETS = 60;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    StatsScreenRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(ProductionStatsView stats, long currentTick, Item selected) {
        Item[] items = Item.values();
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelH = PADDING * 2 + TITLE_HEIGHT + ROW_HEIGHT * items.length + GRAPH_HEIGHT + PADDING;
        float panelX = (screenW - PANEL_WIDTH) / 2f;
        float panelY = (screenH - panelH) / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        shapes.end();

        float graphY = panelY + PADDING;
        float graphX = panelX + PADDING;
        float graphW = PANEL_WIDTH - PADDING * 2;
        renderGraph(stats, currentTick, selected, graphX, graphY, graphW, GRAPH_HEIGHT - PADDING);

        float firstRowY = panelY + panelH - PADDING - TITLE_HEIGHT;
        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Stats  (V to close, N to switch graphed item)", panelX + PADDING, panelY + panelH - PADDING);

        font.getData().setScale(0.8f);
        float y = firstRowY;
        for (Item item : items) {
            double rate = stats.ratePerMinute(item, currentTick, LIST_WINDOW_TICKS);
            boolean isGraphed = item == selected;
            font.setColor(isGraphed ? Palette.SLOT_SELECTED : Color.WHITE);
            font.draw(batch, (isGraphed ? "> " : "  ") + item + "   "
                    + String.format("%.1f/min", rate) + "   total " + stats.total(item), panelX + PADDING, y);
            y -= ROW_HEIGHT;
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * One bar per retained bucket in the last {@link #GRAPH_BUCKETS}, height proportional to that
     * bucket's own count — height is normalized to the TALLEST bar currently on screen, not a
     * fixed scale, so the graph is always readable regardless of whether the selected item makes
     * one unit an hour or a hundred a minute.
     */
    private void renderGraph(ProductionStatsView stats, long currentTick, Item selected, float x, float y, float w,
            float h) {
        long bucketTicks = stats.bucketTicks();
        long currentBucket = currentTick / bucketTicks;
        long firstBucket = currentBucket - GRAPH_BUCKETS + 1;

        int[] counts = new int[GRAPH_BUCKETS];
        List<RateSample> history = stats.history(selected);
        for (RateSample sample : history) {
            long bucketIndex = sample.tick() / bucketTicks;
            int slot = (int) (bucketIndex - firstBucket);
            if (slot >= 0 && slot < GRAPH_BUCKETS) {
                counts[slot] = sample.count();
            }
        }
        int max = 1;
        for (int count : counts) {
            max = Math.max(max, count);
        }

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.HINT);
        shapes.rect(x, y, w, h);
        shapes.end();

        float barSlot = w / GRAPH_BUCKETS;
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.itemColor(selected));
        for (int i = 0; i < GRAPH_BUCKETS; i++) {
            if (counts[i] == 0) {
                continue;
            }
            float barHeight = counts[i] / (float) max * h;
            shapes.rect(x + i * barSlot, y, barSlot * 0.8f, barHeight);
        }
        shapes.end();
    }
}

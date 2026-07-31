package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.Tech;
import com.rustorio.domain.world.PlayerInventoryView;
import com.rustorio.domain.world.ProductionLogView;
import com.rustorio.domain.world.ProductionStatsView;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToLongFunction;

/**
 * "Info" screen (I key, {@code SimulationControls.OverlayPanel.INFO}) — everything the top strip
 * used to cram into eight permanent rows (produced totals, inventory, research, recent log, the
 * full alert breakdown) now lives here instead, on demand, same toggle pattern as {@link
 * RecipeBookRenderer}/{@link TechTreeRenderer}/{@link StatsScreenRenderer}.
 *
 * <p>HUD redesign, live design feedback: the always-on panel read as a cluttered debug console —
 * eight rows of text, every one visible every frame regardless of whether the player had any
 * reason to look at it right then. {@link HudRenderer}'s own top strip now shows only a produced
 * preview (top 3 items) and an alerts badge; this screen is where the full picture — every item,
 * with its actual label, not just a color dot — lives for the moments a player actually wants it.
 *
 * <p>Unlike {@link HudRenderer}'s cached produced-preview chips (rebuilt only when their signature
 * changes — that row draws every single frame regardless of whether this screen is open), nothing
 * here is cached: {@link #render} only ever runs while the player has this screen open, so a plain
 * per-frame rebuild costs nothing extra — the same call budget {@link StatsScreenRenderer}/{@link
 * TechTreeRenderer} already spend.
 */
final class InfoOverlayRenderer {

    private static final float PADDING = 22f;
    private static final float PANEL_WIDTH = 620f;
    private static final float TITLE_HEIGHT = 28f;
    private static final float LABEL_HEIGHT = 18f;
    private static final float GRID_ROW_HEIGHT = 24f;
    private static final float LINE_HEIGHT = 20f;
    private static final float SECTION_GAP = 10f;
    private static final int GRID_COLS = 3;
    private static final float GRID_COL_WIDTH = (PANEL_WIDTH - PADDING * 2) / GRID_COLS;
    private static final float ICON_RADIUS = 7f;
    /** Defensive cap for a mod registering an unreasonable number of items — same reasoning as {@link StatsScreenRenderer#MAX_VISIBLE_ITEMS}. */
    private static final int MAX_GRID_ITEMS = 24;

    /** One item's row in a Produced/Inventory grid — position already resolved, drawn in three passes (icon fill, icon outline, letter+label+count text). */
    private record GridCell(ItemType item, String countText, float cx, float cy, float textX, float textY) {
    }

    /** One alert type's row — a colored square marker (not a chip dot, to read as "problem," not "item") plus its friendly label. */
    private record AlertRow(Color color, String text) {
    }

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    InfoOverlayRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    void render(World world, ProductionStatsView stats, ResearchView research, PlayerInventoryView inventory,
            ProductionLogView log) {
        Registry<ItemType> items = world.buildingFactory().items();
        List<GridCell> producedCells = gridCells(items, item -> stats.total(item));
        List<GridCell> inventoryCells = gridCells(items, inventory::amount);
        List<AlertRow> alertRows = alertRows(world.statusCounts());
        List<ItemType> recent = log.recent();

        int producedRows = Math.max(1, ceilDiv(producedCells.size(), GRID_COLS));
        int inventoryRows = Math.max(1, ceilDiv(inventoryCells.size(), GRID_COLS));
        int alertLineCount = Math.max(1, alertRows.size());

        float panelH = PADDING * 2 + TITLE_HEIGHT
                + LABEL_HEIGHT + producedRows * GRID_ROW_HEIGHT + SECTION_GAP
                + LABEL_HEIGHT + inventoryRows * GRID_ROW_HEIGHT + SECTION_GAP
                + LINE_HEIGHT + SECTION_GAP // research
                + LABEL_HEIGHT + LINE_HEIGHT + SECTION_GAP // recent
                + LABEL_HEIGHT + alertLineCount * LINE_HEIGHT;

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        float panelX = (screenW - PANEL_WIDTH) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float contentX = panelX + PADDING;

        // Position every icon cell up front — every pass below (fill/outline/letter+text) reads
        // the same resolved coordinates instead of three independent layout passes drifting apart.
        float y = panelY + panelH - PADDING - TITLE_HEIGHT;
        y -= LABEL_HEIGHT;
        float producedTop = y;
        positionCells(producedCells, contentX, producedTop);
        y -= producedRows * GRID_ROW_HEIGHT + SECTION_GAP;

        y -= LABEL_HEIGHT;
        float inventoryTop = y;
        positionCells(inventoryCells, contentX, inventoryTop);
        y -= inventoryRows * GRID_ROW_HEIGHT + SECTION_GAP;

        float researchY = y;
        y -= LINE_HEIGHT + SECTION_GAP;

        float recentLabelY = y;
        y -= LABEL_HEIGHT;
        float recentY = y;
        y -= LINE_HEIGHT + SECTION_GAP;

        float alertsLabelY = y;
        y -= LABEL_HEIGHT;
        float alertsTop = y;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        for (GridCell cell : producedCells) {
            ItemIcon.fill(shapes, cell.item(), cell.cx(), cell.cy(), ICON_RADIUS);
        }
        for (GridCell cell : inventoryCells) {
            ItemIcon.fill(shapes, cell.item(), cell.cx(), cell.cy(), ICON_RADIUS);
        }
        for (int i = 0; i < alertRows.size(); i++) {
            shapes.setColor(alertRows.get(i).color());
            shapes.rect(contentX, alertsTop - i * LINE_HEIGHT - 3f, 10f, 10f);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, PANEL_WIDTH, panelH);
        for (GridCell cell : producedCells) {
            ItemIcon.outline(shapes, cell.item(), cell.cx(), cell.cy(), ICON_RADIUS);
        }
        for (GridCell cell : inventoryCells) {
            ItemIcon.outline(shapes, cell.item(), cell.cx(), cell.cy(), ICON_RADIUS);
        }
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.1f);
        font.draw(batch, "Info  (I to close)", contentX, panelY + panelH - PADDING);
        font.getData().setScale(0.85f);

        font.setColor(Palette.HINT);
        font.draw(batch, "Produced", contentX, producedTop + LABEL_HEIGHT - 4f);
        font.draw(batch, "Inventory", contentX, inventoryTop + LABEL_HEIGHT - 4f);
        font.draw(batch, "Recent", contentX, recentLabelY - 4f);
        font.draw(batch, "Alerts", contentX, alertsLabelY - 4f);

        drawCellText(producedCells, "no production yet", contentX, producedTop);
        drawCellText(inventoryCells, "empty", contentX, inventoryTop);

        font.setColor(Color.WHITE);
        font.draw(batch, researchLine(research), contentX, researchY);

        font.setColor(recent.isEmpty() ? Palette.HINT : Color.WHITE);
        font.draw(batch, recentLine(recent), contentX, recentY);

        if (alertRows.isEmpty()) {
            font.setColor(Palette.OK);
            font.draw(batch, "none — everything's working", contentX, alertsTop);
        } else {
            for (int i = 0; i < alertRows.size(); i++) {
                AlertRow row = alertRows.get(i);
                font.setColor(row.color());
                font.draw(batch, row.text(), contentX + 16f, alertsTop - i * LINE_HEIGHT);
            }
        }
        font.setColor(Color.WHITE);
        font.getData().setScale(0.5f);
        for (GridCell cell : producedCells) {
            ItemIcon.letter(batch, font, cell.item(), cell.cx(), cell.cy(), ICON_RADIUS);
        }
        for (GridCell cell : inventoryCells) {
            ItemIcon.letter(batch, font, cell.item(), cell.cx(), cell.cy(), ICON_RADIUS);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** Icon + count text per nonzero item, in registry order, capped at {@link #MAX_GRID_ITEMS} — see that field's own javadoc. */
    private List<GridCell> gridCells(Registry<ItemType> items, ToLongFunction<ItemType> amount) {
        List<GridCell> cells = new ArrayList<>();
        for (ItemType item : items.iterate()) {
            long value = amount.applyAsLong(item);
            if (value > 0) {
                cells.add(new GridCell(item, Long.toString(value), 0f, 0f, 0f, 0f));
                if (cells.size() >= MAX_GRID_ITEMS) {
                    break;
                }
            }
        }
        return cells;
    }

    /** Fills in each cell's screen position in place, left to right then wrapping every {@link #GRID_COLS}. */
    private static void positionCells(List<GridCell> cells, float startX, float topY) {
        for (int i = 0; i < cells.size(); i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            float cx = startX + col * GRID_COL_WIDTH + ICON_RADIUS;
            float cy = topY - row * GRID_ROW_HEIGHT - ICON_RADIUS;
            GridCell resolved = new GridCell(cells.get(i).item(), cells.get(i).countText(), cx, cy,
                    cx + ICON_RADIUS + 6f, cy + 4f);
            cells.set(i, resolved);
        }
    }

    private void drawCellText(List<GridCell> cells, String emptyText, float x, float topY) {
        if (cells.isEmpty()) {
            font.setColor(Palette.HINT);
            font.draw(batch, emptyText, x, topY - GRID_ROW_HEIGHT + 6f);
            font.setColor(Color.WHITE);
            return;
        }
        for (GridCell cell : cells) {
            font.setColor(Color.WHITE);
            font.draw(batch, cell.item().label() + "  " + cell.countText(), cell.textX(), cell.textY());
        }
    }

    private static String researchLine(ResearchView research) {
        StringBuilder sb = new StringBuilder("Research: ").append(research.points()).append(" pts (T for tech tree)   ");
        sb.append("Unlocked: ");
        if (research.unlocked().isEmpty()) {
            sb.append('-');
        } else {
            for (Tech tech : Tech.values()) {
                if (research.isUnlocked(tech)) {
                    sb.append(tech.label()).append("  ");
                }
            }
        }
        return sb.toString();
    }

    private static String recentLine(List<ItemType> recent) {
        if (recent.isEmpty()) {
            return "Recent: -";
        }
        StringBuilder sb = new StringBuilder("Recent:   ");
        for (ItemType item : recent) {
            sb.append(item.label()).append("  ");
        }
        return sb.toString();
    }

    /** One row per nonzero {@link BuildingStatus} — same friendly labels the old always-on alerts row used. */
    private static List<AlertRow> alertRows(Map<BuildingStatus, Integer> counts) {
        List<AlertRow> rows = new ArrayList<>();
        for (BuildingStatus status : BuildingStatus.values()) {
            if (status == BuildingStatus.WORKING) {
                continue;
            }
            int count = counts.getOrDefault(status, 0);
            if (count > 0) {
                rows.add(new AlertRow(Palette.statusColor(status).orElseThrow(), alertLabel(status) + " x" + count));
            }
        }
        return rows;
    }

    private static String alertLabel(BuildingStatus status) {
        return switch (status) {
            case NO_ORE -> "no ore";
            case NO_FUEL -> "no fuel";
            case NO_INPUT -> "no input";
            case OUTPUT_FULL -> "output full";
            case WORKING -> throw new IllegalArgumentException("WORKING never reaches an alert row");
        };
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }
}

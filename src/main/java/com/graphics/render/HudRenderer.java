package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.Splitter;
import com.rustorio.domain.building.UndergroundBelt;
import com.rustorio.domain.world.ProductionStatsView;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * HUD — a slim, always-on top strip (title, pause/speed, an alerts badge, a produced preview, a
 * one-line hotkey reminder) and the buildings panel at the bottom — plus the inspection panel for
 * whatever's clicked. Everything the top strip used to carry as eight permanent rows (full
 * produced/inventory lists, research, the recent log, the full alert breakdown, the three-line
 * hotkey legend, FPS/UPS) now lives on demand instead: {@link InfoOverlayRenderer} (I key) for the
 * lists, {@code showHints}/{@code showFpsUps} ({@link HudState}, H/P keys) for the rest.
 *
 * <p>HUD redesign, live design feedback: the always-on version read as a cluttered debug console —
 * every one of those eight rows drawn every single frame whether or not the player had any reason
 * to look at it right then. A player who wants the full picture still gets it, just one keypress
 * away rather than permanently eating screen space (and reserved world-view height — see {@link
 * GfxConfig#HUD_TOP_HEIGHT}'s own note).
 *
 * <p>Slot selection in the bottom panel is still obvious — the same bright outline ({@link
 * Palette#SLOT_SELECTED}) this had before the redesign.
 */
final class HudRenderer {

    /** One nonzero item's worth of chip: an item plus its count as text — see {@link #producedChips}. */
    private record ItemChip(ItemType item, String text) {
    }

    /**
     * A chip's fully resolved screen position — computed once, drawn three times (an {@link
     * ItemIcon} fill/outline pair, a count number), and hit-tested a third time for {@link
     * #renderChipTooltip} (live bug report: color alone doesn't say which item is which).
     */
    private record ChipLayout(ItemType item, float circleX, float circleY, Color color, float textX, float textY,
            String text) {
    }

    /** {@link #summarizeAlerts}'s result — how many buildings have a problem, and which color best represents the worst one present. */
    private record AlertSummary(int total, Color color) {
    }

    private static final float COL_TITLE_X = 16f;
    private static final float COL_STATUS_X = 140f;
    private static final float COL_ALERTS_X = 250f;
    private static final float COL_FPS_X = 470f;
    private static final float COL_STATUS_MSG_X = 620f;

    private static final float ROW_HEADER = 22f;
    private static final float ROW_PRODUCED_PREVIEW = 48f;
    private static final float ROW_HINT_1 = 74f;
    private static final float ROW_HINT_2 = 90f;
    private static final float ROW_HINT_3 = 106f;

    /** Right after the "Produced" label — see {@link #layoutPreview}. */
    private static final float PREVIEW_ICON_X = 110f;
    private static final float PREVIEW_ICON_RADIUS = 7f;
    /** Breathing room between one chip's count text and the next chip's icon — see {@link #layoutPreview}. */
    private static final float PREVIEW_CHIP_GAP = 16f;
    /** How many of the produced-preview's top items get an icon on the strip before it just says "+N more". */
    private static final int PREVIEW_COUNT = 3;
    /** A few extra pixels around the icon itself — hitting the exact icon with a mouse cursor is unreasonably precise. */
    private static final float CHIP_HOVER_RADIUS = PREVIEW_ICON_RADIUS + 4f;
    private static final float ALERT_MARKER_SIZE = 10f;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;
    private final GlyphLayout glyphLayout = new GlyphLayout();

    /**
     * Cached produced-preview chips and the cheap signature they were built from (P4-05,
     * BUG_FIX_PROGRESS.md): rebuilding by walking every {@link ItemType} in the registry every
     * single frame is wasted work on the (overwhelming majority of) frames where production hasn't
     * changed since the last one. This is the only row {@link #renderInfoPanel} still draws on
     * every frame regardless of what the player has open — everything else moved to {@link
     * InfoOverlayRenderer}, which only ever runs while the player has that screen open, so it
     * doesn't need this same caching (see that class's own javadoc).
     */
    private long producedSignature = -1;
    private List<ItemChip> producedChipsCache = List.of();

    HudRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
    }

    void render(HudState hud, World world, TileRange visible, ProductionStatsView stats, int ups) {
        renderInfoPanel(world, stats, hud.paused(), hud.speed(), ups, hud.showHints(), hud.showFpsUps(),
                hud.statusMessage());
        renderHotbar(hud.hotbarSlots(), hud.selected(), hud.facing(), world.buildingFactory());
        renderMinimap(world, visible);
        renderInspectionPanel(world, hud.inspected());
    }

    /**
     * Top strip: title, pause/speed, an alerts badge, a produced preview (top {@link
     * #PREVIEW_COUNT} items), and a hotkey reminder — one line by default, the full legend when
     * {@code showHints} is on. Height is {@link GfxConfig#HUD_TOP_HEIGHT}, the same constant the
     * camera narrowed its viewport by ({@code GameCamera#resize}) — the panel and the "hole" in the
     * world it covers always agree by construction, not by two numbers in different files matching
     * by coincidence.
     */
    private void renderInfoPanel(World world, ProductionStatsView stats, boolean paused, int speed, int ups,
            boolean showHints, boolean showFpsUps, @Nullable String statusMessage) {
        Registry<ItemType> items = world.buildingFactory().items();
        int screenW = Gdx.graphics.getWidth();
        float top = Gdx.graphics.getHeight();
        float panelH = GfxConfig.HUD_TOP_HEIGHT;
        // Stop chips short of the minimap (100px square + 16px margin) in the top-right corner —
        // see renderMinimap — so a long preview row can never be drawn under/behind it.
        float maxX = screenW - 132f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(0, top - panelH, screenW, panelH);
        shapes.end();

        // Тонкая грань по нижнему краю — отделяет панель от мира визуально, не только
        // полупрозрачностью подложки.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.line(0, top - panelH, screenW, top - panelH);
        shapes.end();

        font.getData().setScale(1f); // layout below measures at THIS scale — fix it before laying out
        List<ItemChip> produced = producedChips(items, stats);
        List<ChipLayout> previewLayout = layoutPreview(topChips(produced, PREVIEW_COUNT), top - ROW_PRODUCED_PREVIEW, maxX);
        AlertSummary alerts = summarizeAlerts(world.statusCounts());

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (ChipLayout chip : previewLayout) {
            ItemIcon.fill(shapes, chip.item(), chip.circleX(), chip.circleY(), PREVIEW_ICON_RADIUS);
        }
        shapes.setColor(alerts.color());
        shapes.rect(COL_ALERTS_X - ALERT_MARKER_SIZE - 6f, top - ROW_HEADER - 8f, ALERT_MARKER_SIZE, ALERT_MARKER_SIZE);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ChipLayout chip : previewLayout) {
            ItemIcon.outline(shapes, chip.item(), chip.circleX(), chip.circleY(), PREVIEW_ICON_RADIUS);
        }
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", COL_TITLE_X, top - ROW_HEADER);

        font.setColor(paused ? Palette.IDLE : Palette.WORKING);
        font.draw(batch, paused ? "PAUSED" : ("Speed: " + speed + "x"), COL_STATUS_X, top - ROW_HEADER);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);

        // Alerts badge (art redesign, point 7 of the HUD review): a colored marker plus count, not
        // just another same-weight text row — the worst status currently present picks the color
        // (see summarizeAlerts), green "OK" when nothing's wrong.
        font.setColor(alerts.color());
        font.draw(batch, alerts.total() == 0 ? "Alerts: OK" : "Alerts: " + alerts.total(), COL_ALERTS_X, top - ROW_HEADER);
        font.setColor(Color.WHITE);

        // FPS/UPS — opt-in (P key) now, not a permanent debug line most players never asked for.
        if (showFpsUps) {
            font.setColor(Palette.HINT);
            font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond() + "  UPS: " + ups, COL_FPS_X, top - ROW_HEADER);
            font.setColor(Color.WHITE);
        }

        // Живой баг-репорт: F5/F9 раньше не показывали НИЧЕГО на экране — ни "сохранено", ни
        // "не вышло, вот почему" — см. HudState/InputHandler#showStatus. Гаснет сама через
        // InputHandler.STATUS_MESSAGE_SECONDS, отдельного "закрыть" не нужно.
        if (statusMessage != null) {
            font.setColor(Palette.HINT);
            font.draw(batch, statusMessage, COL_STATUS_MSG_X, top - ROW_HEADER);
            font.setColor(Color.WHITE);
        }

        // Produced preview — top PREVIEW_COUNT items by volume, not the full (mod-length) list;
        // the full list, with real labels instead of a one-letter icon, lives in InfoOverlayRenderer
        // (I key) now (point 3/4 of the HUD review).
        font.setColor(Palette.HINT);
        font.draw(batch, "Produced", COL_TITLE_X, top - ROW_PRODUCED_PREVIEW);
        if (previewLayout.isEmpty()) {
            font.draw(batch, "-", PREVIEW_ICON_X, top - ROW_PRODUCED_PREVIEW);
        } else {
            font.getData().setScale(0.5f);
            for (ChipLayout chip : previewLayout) {
                ItemIcon.letter(batch, font, chip.item(), chip.circleX(), chip.circleY(), PREVIEW_ICON_RADIUS);
            }
            font.getData().setScale(1f);
            font.setColor(Color.WHITE);
            for (ChipLayout chip : previewLayout) {
                font.draw(batch, chip.text(), chip.textX(), chip.textY());
            }
            if (produced.size() > previewLayout.size()) {
                ChipLayout last = previewLayout.get(previewLayout.size() - 1);
                glyphLayout.setText(font, last.text());
                float hintX = last.textX() + glyphLayout.width + PREVIEW_CHIP_GAP;
                font.setColor(Palette.HINT);
                font.draw(batch, "+" + (produced.size() - previewLayout.size()) + " more — I for full list",
                        hintX, top - ROW_PRODUCED_PREVIEW);
                font.setColor(Color.WHITE);
            }
        }

        // Hotkey reminder — one line by default, the old three-line legend only while H is held
        // toggled on (point 1 of the HUD review: this used to be three permanent rows).
        font.setColor(Palette.HINT);
        font.getData().setScale(0.8f);
        if (showHints) {
            font.draw(batch,
                    "R rotate   U upgrade   C recipe   F filter item   G grab chest   Ctrl+Z undo   Ctrl+Y redo   F5 save   F9 load   TAB recipes   B build menu   T techs   V stats   I info",
                    COL_TITLE_X, top - ROW_HINT_1);
            font.draw(batch, "WASD pan   wheel zoom   Space pause   [ ] speed   click or 1-9 to build",
                    COL_TITLE_X, top - ROW_HINT_2);
            font.draw(batch,
                    "right-click: demolish (refunds cost) / hand-mine an empty ore cell   H hide this   P fps/ups",
                    COL_TITLE_X, top - ROW_HINT_3);
        } else {
            font.draw(batch, "H: hotkeys   I: info   V: stats   T: techs   B: build menu   P: fps/ups",
                    COL_TITLE_X, top - ROW_HINT_1);
        }
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();

        renderChipTooltip(previewLayout);
    }

    /**
     * A small floating box naming whichever preview icon the mouse is currently over — the icon
     * alone (shape + letter + color) can still be ambiguous between two items sharing a first
     * letter (live bug report). Draws nothing when the cursor isn't over one of the {@link
     * #PREVIEW_COUNT} icons, so this costs nothing on every other frame.
     */
    private void renderChipTooltip(List<ChipLayout> layout) {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY(); // Gdx.input is top-down; this panel's own coordinates are bottom-up

        ChipLayout hovered = findHoveredChip(layout, mouseX, mouseY);
        if (hovered == null) {
            return;
        }

        String text = "Produced: " + hovered.item().label() + "  " + hovered.text();
        glyphLayout.setText(font, text);
        float boxW = glyphLayout.width + 16f;
        float boxH = glyphLayout.height + 14f;
        float boxX = mouseX + 14f;
        float boxY = mouseY - boxH - 6f; // below the cursor — chips live near the top of the screen, so "above" would run off-window

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(boxX, boxY, boxW, boxH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, text, boxX + 8f, boxY + boxH - 7f);
        batch.end();
    }

    private static @Nullable ChipLayout findHoveredChip(List<ChipLayout> layout, float mouseX, float mouseY) {
        for (ChipLayout chip : layout) {
            float dx = mouseX - chip.circleX();
            float dy = mouseY - chip.circleY();
            if (dx * dx + dy * dy <= CHIP_HOVER_RADIUS * CHIP_HOVER_RADIUS) {
                return chip;
            }
        }
        return null;
    }

    /** The {@code n} highest-count chips, most-produced first — what the strip's preview shows instead of registry order. */
    private static List<ItemChip> topChips(List<ItemChip> chips, int n) {
        return chips.stream()
                .sorted(Comparator.comparingLong((ItemChip c) -> Long.parseLong(c.text())).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /**
     * Positions the (already-trimmed-to-{@link #PREVIEW_COUNT}) preview chips left to right from
     * {@link #PREVIEW_ICON_X}, each chip's own width measured from its actual count text (live bug
     * report: a fixed per-chip gap let a wide count — three/four digits — run straight into the
     * next chip's icon; production totals only ever grow, so any fixed gap eventually collides).
     */
    private List<ChipLayout> layoutPreview(List<ItemChip> chips, float rowY, float maxX) {
        List<ChipLayout> layout = new ArrayList<>();
        float x = PREVIEW_ICON_X;
        for (ItemChip chip : chips) {
            glyphLayout.setText(font, chip.text());
            float width = PREVIEW_ICON_RADIUS * 2 + 8f + glyphLayout.width;
            if (x + width > maxX) {
                break;
            }
            layout.add(new ChipLayout(chip.item(), x + PREVIEW_ICON_RADIUS, rowY - 2f, Palette.itemColor(chip.item()),
                    x + PREVIEW_ICON_RADIUS * 2 + 8f, rowY, chip.text()));
            x += width + PREVIEW_CHIP_GAP;
        }
        return layout;
    }

    /**
     * Нижняя панель построек: слот на каждый закреплённый в хотбаре прототип (настраиваемый
     * список, был {@code BuildingType.values()} напрямую), выбранный — обведён ярко. Высота —
     * {@link GfxConfig#HUD_BOTTOM_HEIGHT}, та же, на которую камера сузила вьюпорт снизу (см.
     * {@link #renderInfoPanel} — тот же приём для верхней панели).
     */
    private void renderHotbar(List<ContentId> hotbarSlots, ContentId selected, Direction facing, BuildingFactory buildingFactory) {
        int screenW = Gdx.graphics.getWidth();
        int slotCount = hotbarSlots.size();
        float barH = GfxConfig.HUD_BOTTOM_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(0, 0, screenW, barH);
        for (int i = 0; i < slotCount; i++) {
            shapes.setColor(Palette.SLOT_BG);
            shapes.rect(HotbarLayout.slotX(i, screenW, slotCount), HotbarLayout.slotY(),
                    HotbarLayout.SLOT_SIZE, HotbarLayout.SLOT_SIZE);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.line(0, barH, screenW, barH); // грань по верхнему краю — см. renderInfoPanel's нижняя
        for (int i = 0; i < slotCount; i++) {
            boolean isSelected = hotbarSlots.get(i).equals(selected);
            shapes.setColor(isSelected ? Palette.SLOT_SELECTED : Palette.SLOT_BORDER);
            float x = HotbarLayout.slotX(i, screenW, slotCount);
            float y = HotbarLayout.slotY();
            shapes.rect(x, y, HotbarLayout.SLOT_SIZE, HotbarLayout.SLOT_SIZE);
            if (isSelected) {
                // Обвести дважды со сдвигом в 1px — тонкая линия одним проходом на выделении
                // теряется рядом с обычной рамкой соседних слотов, а лишний класс ради толщины
                // линии заводить незачем.
                shapes.rect(x + 1, y + 1, HotbarLayout.SLOT_SIZE - 2, HotbarLayout.SLOT_SIZE - 2);
            }
        }
        shapes.end();

        batch.begin();
        float iconPad = 8f;
        float iconSize = HotbarLayout.SLOT_SIZE - iconPad * 2;
        for (int i = 0; i < slotCount; i++) {
            ContentId prototypeId = hotbarSlots.get(i);
            BuildingPrototype prototype = buildingFactory.prototype(prototypeId);
            boolean isSelected = prototypeId.equals(selected);
            float x = HotbarLayout.slotX(i, screenW, slotCount);
            float y = HotbarLayout.slotY();
            TextureRegion icon = textures.forSprite(prototype.texture());
            font.setColor(Color.WHITE);
            batch.draw(icon, x + iconPad, y + iconPad, iconSize, iconSize);

            font.getData().setScale(0.75f);
            font.setColor(isSelected ? Palette.SLOT_SELECTED : Palette.HINT);
            if (i < 9) {
                font.draw(batch, Integer.toString(i + 1), x + 4, y + HotbarLayout.SLOT_SIZE - 3);
            }
            font.getData().setScale(0.62f);
            font.setColor(Palette.HINT);
            font.draw(batch, prototype.label(), x, y - 3);
        }

        font.getData().setScale(0.85f);
        font.setColor(Palette.HINT);
        font.draw(batch, "Facing: " + facing.name() + "  (R to rotate — only the belt/tunnel/furnace care)",
                16, HotbarLayout.slotY() + HotbarLayout.SLOT_SIZE + 16f);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Мини-карта (X-04, DEV_TASKS.md): фиксированный квадрат в правом верхнем углу верхней
     * панели — точка на каждое здание где-либо на карте (не только в кадре, в отличие от {@link
     * BuildingRenderer}, которому камера отдаёт только видимый {@link TileRange}) и рамка,
     * показывающая, что из этого сейчас реально видит камера.
     *
     * <p>Клетка {@code (0, 0)} — левый верхний угол карты (см. {@link GameCamera}), поэтому строка
     * {@code y} растёт ВНИЗ и в мировых координатах, и в этом квадрате — переворачивать нужно
     * только сам порядок отрисовки (низкий {@code y} рисуется у верхнего края квадрата), не оси.
     */
    private void renderMinimap(World world, TileRange visible) {
        float size = 100f;
        float screenW = Gdx.graphics.getWidth();
        float top = Gdx.graphics.getHeight();
        float panelX = screenW - size - 16f;
        float panelY = top - GfxConfig.HUD_TOP_HEIGHT + (GfxConfig.HUD_TOP_HEIGHT - size) / 2f;
        float scaleX = size / world.width();
        float scaleY = size / world.height();
        float dotW = Math.max(1f, scaleX);
        float dotH = Math.max(1f, scaleY);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.SLOT_BG);
        shapes.rect(panelX, panelY, size, size);
        shapes.setColor(Palette.HINT);
        world.forEachBuildingIn(0, 0, world.width() - 1, world.height() - 1, (bx, by, building) ->
                shapes.rect(panelX + bx * scaleX, panelY + size - (by + 1) * scaleY, dotW, dotH));
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Color.WHITE);
        float camX = panelX + visible.minX() * scaleX;
        float camY = panelY + size - (visible.maxY() + 1) * scaleY;
        float camW = (visible.maxX() - visible.minX() + 1) * scaleX;
        float camH = (visible.maxY() - visible.minY() + 1) * scaleY;
        shapes.rect(camX, camY, camW, camH);
        shapes.end();
    }

    /**
     * Inspection panel (F-03, DEV_TASKS.md): click any placed building ({@code InputHandler}) to
     * see its live internal state — a chest's contents by kind, a furnace/press's committed or
     * selected recipe and remaining fuel, any building's {@code speedLevel}, a tunnel
     * entrance's pairing. Reads straight off the actual {@link Building}, the same object {@code
     * World.tick} runs — nothing here is a separate copy that could drift from what's really
     * happening. {@code null}/an empty cell (the building got demolished since the click) simply
     * draws nothing — the panel just isn't there anymore, no explicit "close" needed.
     */
    private void renderInspectionPanel(World world, @Nullable TilePos at) {
        if (at == null) {
            return;
        }
        Optional<Building> found = world.peek(at.x(), at.y());
        if (found.isEmpty()) {
            return;
        }
        List<String> lines = inspectionLines(world, world.buildingFactory().items(), at, found.get());

        float lineH = 18f;
        float panelW = 340f; // wide enough for a two-input recipe line ("IRON_ORE + BRONZE_PLATE -> ALLOY_PLATE")
        float panelH = 20f + lines.size() * lineH;
        float screenW = Gdx.graphics.getWidth();
        float top = Gdx.graphics.getHeight();
        float panelX = screenW - panelW - 16f;
        float panelY = top - GfxConfig.HUD_TOP_HEIGHT - 16f - panelH;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        batch.begin();
        font.getData().setScale(0.85f);
        font.setColor(Color.WHITE);
        float ty = panelY + panelH - 14f;
        for (String line : lines) {
            font.draw(batch, line, panelX + 12f, ty);
            ty -= lineH;
        }
        font.getData().setScale(1f);
        batch.end();
    }

    /** One line per fact — kind-specific extras appended after the facts every building shares. */
    private static List<String> inspectionLines(World world, Registry<ItemType> items, TilePos at, Building building) {
        List<String> lines = new ArrayList<>();
        lines.add(building.type().label() + "  (" + at.x() + ", " + at.y() + ")");
        lines.add("Status: " + building.appearance().status());
        if (building.speedLevel() > 0) {
            lines.add("Speed modules: x" + building.speedLevel());
        }
        building.heldItem().ifPresent(item -> lines.add("Holding: " + item.label()));

        if (building instanceof Chest chest) {
            appendChestContents(lines, items, chest);
        } else if (building instanceof Furnace furnace) {
            appendFurnaceDetails(lines, building, furnace);
        } else if (building instanceof UndergroundBelt tunnel) {
            appendTunnelPairing(lines, world, at, building, tunnel);
        } else if (building instanceof Filter filter) {
            lines.add("Passes forward: " + filter.filterItem().label() + "  (F to change)");
            lines.add("Everything else -> secondary side");
        } else if (building instanceof Splitter) {
            lines.add("Round-robin: alternates forward / secondary side");
        }
        return lines;
    }

    private static void appendChestContents(List<String> lines, Registry<ItemType> items, Chest chest) {
        boolean any = false;
        for (ItemType item : items.iterate()) {
            int amount = chest.amount(item);
            if (amount > 0) {
                lines.add("  " + item.label() + ": " + amount);
                any = true;
            }
        }
        if (!any) {
            lines.add("  (empty)");
        }
    }

    /**
     * The full recipe — input(s) AND output, not just the output {@link Recipe#output()} —
     * because that's the actual live bug report: the old panel showed "-> IRON_PLATE" and nothing
     * about what to feed it. A furnace with nothing committed yet lists EVERY recipe its kind can
     * run at all, same reason: "не понятно что может производить" (unclear what it can even make)
     * when nothing's been fed to narrow it down to one.
     */
    private static void appendFurnaceDetails(List<String> lines, Building building, Furnace furnace) {
        Optional<Recipe> active = furnace.activeRecipe();
        if (active.isPresent()) {
            lines.add("Recipe: " + recipeLine(active.get()) + "  (cooking)");
        } else {
            Optional<Recipe> selected = furnace.selectedRecipeChoice();
            if (selected.isPresent()) {
                lines.add("Recipe: " + recipeLine(selected.get()) + "  (selected — C to change)");
            } else {
                lines.add("Recipe: none committed yet — can make:");
                for (Recipe recipe : furnace.possibleRecipes()) {
                    lines.add("  " + recipeLine(recipe));
                }
            }
        }
        lines.add("Ore buffer: " + furnace.oreBuffer());
        if (building.type() == BuildingType.FURNACE) {
            lines.add("Fuel: " + furnace.fuelBuffer());
        }
    }

    private static String recipeLine(Recipe recipe) {
        String inputs = recipe.ingredients().stream().map(ItemType::label).collect(Collectors.joining(" + "));
        return inputs + " -> " + recipe.output().label();
    }

    private static void appendTunnelPairing(List<String> lines, World world, TilePos at, Building building,
            UndergroundBelt tunnel) {
        if (building.type() != BuildingType.UNDERGROUND_IN) {
            lines.add("(exit — pairing shown at its entrance)");
            return;
        }
        boolean paired = tunnel.findPartner(world, at.x(), at.y()).isPresent();
        lines.add("Paired: " + (paired ? "yes" : "NO — out of range or no matching exit"));
    }

    /**
     * Chips for the produced preview — nonzero items only (live bug report; the old panel printed
     * every {@link ItemType} including the zero ones). Rebuilt only if the sum of all counters
     * changed (P4-05, BUG_FIX_PROGRESS.md) — a cheap signature: totals only ever grow over {@code
     * ProductionStats}'s lifetime (barring {@code restore}), so a matching sum reliably means
     * "nothing happened."
     */
    private List<ItemChip> producedChips(Registry<ItemType> items, ProductionStatsView stats) {
        long signature = 0;
        for (ItemType item : items.iterate()) {
            signature += stats.total(item);
        }
        if (signature == producedSignature) {
            return producedChipsCache;
        }
        producedSignature = signature;
        List<ItemChip> chips = new ArrayList<>();
        for (ItemType item : items.iterate()) {
            long total = stats.total(item);
            if (total > 0) {
                chips.add(new ItemChip(item, Long.toString(total)));
            }
        }
        return producedChipsCache = chips;
    }

    /**
     * Alerts badge summary (F-01, DEV_TASKS.md) — total count across every non-{@code WORKING}
     * {@link BuildingStatus}, plus the color of the first (by enum declaration order — the same
     * priority {@code BuildingStatus} itself documents, ore/fuel shortages before a full output)
     * nonzero status present, or {@link Palette#OK} when nothing's wrong. Reads {@link
     * World#statusCounts()}, which {@code World} keeps current incrementally as buildings
     * tick/place/demolish, not a per-frame walk of the whole map.
     *
     * <p>Not cached (unlike {@link #producedChips}): {@code BuildingStatus} only has four
     * non-{@code WORKING} values, so summing them is already cheaper than the cache-signature
     * bookkeeping would be.
     */
    private static AlertSummary summarizeAlerts(Map<BuildingStatus, Integer> counts) {
        int total = 0;
        Color worst = Palette.OK;
        boolean sawOne = false;
        for (BuildingStatus status : BuildingStatus.values()) {
            if (status == BuildingStatus.WORKING) {
                continue;
            }
            int count = counts.getOrDefault(status, 0);
            if (count > 0) {
                total += count;
                if (!sawOne) {
                    worst = Palette.statusColor(status).orElseThrow();
                    sawOne = true;
                }
            }
        }
        return new AlertSummary(total, worst);
    }
}

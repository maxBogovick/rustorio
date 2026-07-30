package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.ResearchView;
import com.rustorio.domain.Tech;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.Splitter;
import com.rustorio.domain.building.UndergroundBelt;
import com.rustorio.domain.world.PlayerInventoryView;
import com.rustorio.domain.world.ProductionLogView;
import com.rustorio.domain.world.ProductionStats;
import com.rustorio.domain.world.ProductionStatsView;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * HUD — информационная панель сверху (заголовок, статистика, исследования, лог, подсказки) и
 * панель построек снизу (кликабельные слоты — та же роль, что кнопки 1-9, только видно ГЛАЗАМИ,
 * что выбрано и чем можно строить, а не запоминать номера).
 *
 * <p>Раньше вся панель была голым текстом без подложки — читалась плохо на светлом фоне мира, а
 * выбор постройки был виден только цифрой в скобках посреди строки. Теперь обе панели рисуются
 * на тёмной полупрозрачной подложке ({@link Palette#PANEL_BG}) на всю ширину окна: текст не
 * теряется, какой бы ни была земля под ним, а слот выбранного здания обведён ярко
 * ({@link Palette#SLOT_SELECTED}).
 *
 * <p>Строка статистики и строка лога читают РАЗНЫХ, ничего не знающих друг о друге слушателей
 * одного и того же события «предмет произведён» ({@link ProductionStats}, {@code ProductionLog}
 * — урок 14). HUD дальше про них ничего не знает: просто читает и показывает через {@link
 * ProductionLogView} (P3-08, BUG_FIX_PROGRESS.md) — ту же дисциплину read-only вида, что уже
 * применена к {@link ProductionStatsView} и {@link ResearchView}.
 */
final class HudRenderer {

    private static final String NO_ALERTS = "Alerts: none";

    /** One nonzero item's worth of chip: a {@link Palette#itemColor} dot plus its count as text — see {@link #layoutChips}. */
    private record ItemChip(ItemType item, String text) {
    }

    /**
     * A chip's fully resolved screen position — computed once, drawn twice (a {@link
     * ShapeRenderer} circle, a {@link SpriteBatch} number), and hit-tested a third time for
     * {@link #renderChipTooltip} (live bug report: color alone doesn't say which item is which).
     */
    private record ChipLayout(ItemType item, float circleX, float circleY, Color color, float textX, float textY,
            String text) {
    }

    private static final float CHIP_ROW_X = 120f;
    private static final float CHIP_RADIUS = 6f;
    private static final float CHIP_GAP = 16f;
    /** A few extra pixels around the dot itself — hitting the exact 6px circle with a mouse cursor is unreasonably precise. */
    private static final float CHIP_HOVER_RADIUS = CHIP_RADIUS + 4f;

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;
    private final GlyphLayout glyphLayout = new GlyphLayout();

    /**
     * Cached HUD content and the cheap signature it was built from (P4-05, BUG_FIX_PROGRESS.md):
     * {@link #producedChips}/{@link #research}/{@link #recent} rebuilt their output every single
     * frame regardless of whether production, research or the log had actually changed since the
     * last one. {@code -1} never matches a real total/points value, so the first call always
     * (correctly) rebuilds.
     */
    private long producedSignature = -1;
    private List<ItemChip> producedChipsCache = List.of();
    private long researchSignature = -1;
    private String researchCache = "";
    private List<ItemType> recentSignature = List.of();
    private String recentCache = "";
    private long inventorySignature = -1;
    private List<ItemChip> inventoryChipsCache = List.of();
    private long alertsSignature = -1;
    private String alertsCache = "";

    HudRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
    }

    void render(HudState hud, World world, TileRange visible, ProductionStatsView stats, ResearchView research,
            PlayerInventoryView inventory, ProductionLogView log, int ups) {
        renderInfoPanel(world, stats, research, inventory, log, hud.paused(), hud.speed(), ups);
        renderHotbar(hud.selected(), hud.facing(), world.buildingFactory());
        renderMinimap(world, visible);
        renderInspectionPanel(world, hud.inspected());
    }

    /**
     * Верхняя панель: заголовок, пауза/скорость, статистика, исследования, лог, подсказки.
     *
     * <p>Высота панели — {@link GfxConfig#HUD_TOP_HEIGHT}, ТА ЖЕ константа, на которую камера
     * сузила свой вьюпорт ({@link GameCamera#resize}): подложка и «дыра» в мире, которую она
     * закрывает, всегда совпадают по построению, не по совпадению двух чисел в разных файлах.
     *
     * <p><b>Produced/Inventory as icon chips, not names (live bug report).</b> The old version
     * printed EVERY {@link ItemType}, including the zero ones — mostly noise once more than two or
     * three item kinds exist — as bare text ({@code "IRON_ORE 12    IRON_PLATE 4    ...""}), which
     * ran off the right edge of the window with all eleven kinds unlocked (nothing wrapped, nothing
     * was cut for space). {@link #producedChips}/{@link #inventoryChips} now filter to nonzero only,
     * and {@link #layoutChips} draws each as a small {@link Palette#itemColor} dot plus a number —
     * the same color language {@code ItemRenderer}/{@code RecipeBookRenderer} already use for cargo
     * and recipe icons, several times narrower per item than the old {@code "NAME count"} text.
     */
    private void renderInfoPanel(World world, ProductionStatsView stats, ResearchView research,
            PlayerInventoryView inventory, ProductionLogView log, boolean paused, int speed, int ups) {
        Registry<ItemType> items = world.buildingFactory().items();
        int screenW = Gdx.graphics.getWidth();
        float top = Gdx.graphics.getHeight();
        float panelH = GfxConfig.HUD_TOP_HEIGHT;
        // Stop chips short of the minimap (100px square + 16px margin) in the top-right corner —
        // see renderMinimap — so a long chip row can never be drawn under/behind it.
        float maxX = screenW - 132f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(0, top - panelH, screenW, panelH);
        shapes.end();

        font.getData().setScale(1f); // layout measures glyph widths at THIS scale — fix it before laying out
        List<ChipLayout> producedLayout = layoutChips(producedChips(items, stats), top - 36, maxX);
        List<ChipLayout> inventoryLayout = layoutChips(inventoryChips(items, inventory), top - 54, maxX);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        drawChipCircles(producedLayout);
        drawChipCircles(inventoryLayout);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", 16, top - 14);

        font.setColor(paused ? Palette.IDLE : Palette.WORKING);
        font.draw(batch, paused ? "PAUSED" : ("Speed: " + speed + "x"), 220, top - 14);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);

        // S-04, DEV_TASKS.md: FPS is libGDX's own already-smoothed measurement, read straight —
        // UPS is GameScreen's own count of real World.tick() calls in the last full second (not
        // the same thing: at 2x/4x speed UPS climbs past FPS, and the gap between them is itself
        // a useful signal if the simulation ever falls behind the requested multiplier).
        font.setColor(Palette.HINT);
        font.draw(batch, "FPS: " + Gdx.graphics.getFramesPerSecond() + "  UPS: " + ups, 380, top - 14);
        font.setColor(Color.WHITE);

        font.draw(batch, "Produced", 16, top - 36);
        drawChipNumbers(producedLayout, top - 36);
        font.draw(batch, "Inventory", 16, top - 54);
        drawChipNumbers(inventoryLayout, top - 54);

        font.draw(batch, research(research), 16, top - 72);
        font.draw(batch, recent(log), 16, top - 90);

        String alertsLine = alerts(world);
        font.setColor(alertsLine.equals(NO_ALERTS) ? Palette.WORKING : Palette.IDLE);
        font.draw(batch, alertsLine, 16, top - 108);
        font.setColor(Color.WHITE);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.8f);
        font.draw(batch, "R rotate   U upgrade   C recipe   F filter item   G grab chest   Ctrl+Z undo   Ctrl+Y redo   F5 save   F9 load   TAB recipes   T techs   V stats",
                16, top - 126);
        font.draw(batch, "WASD pan   wheel zoom   Space pause   [ ] speed   click or 1-9 to build",
                16, top - 142);
        // A live bug report: a player stuck with zero spendable resources had no idea right-click
        // refunds a demolished building's cost, or that an empty ore cell can be mined by hand —
        // both already existed (D-03; the manual-mine follow-up above) but were never documented
        // anywhere on screen.
        font.draw(batch, "right-click: demolish (refunds cost) / hand-mine an empty ore cell",
                16, top - 158);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();

        renderChipTooltip(producedLayout, inventoryLayout);
    }

    /**
     * A small floating box naming whichever chip the mouse is currently over — the color dot alone
     * doesn't say which {@link ItemType} it is (live bug report). Checks {@code producedLayout} first,
     * then {@code inventoryLayout}; draws nothing at all when the cursor isn't over either row's
     * dots, so this costs nothing on every other frame.
     */
    private void renderChipTooltip(List<ChipLayout> producedLayout, List<ChipLayout> inventoryLayout) {
        float mouseX = Gdx.input.getX();
        float mouseY = Gdx.graphics.getHeight() - Gdx.input.getY(); // Gdx.input is top-down; this panel's own coordinates are bottom-up

        ChipLayout hovered = findHoveredChip(producedLayout, mouseX, mouseY);
        String rowLabel = "Produced";
        if (hovered == null) {
            hovered = findHoveredChip(inventoryLayout, mouseX, mouseY);
            rowLabel = "Inventory";
        }
        if (hovered == null) {
            return;
        }

        String text = rowLabel + ": " + hovered.item().label() + "  " + hovered.text();
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

    /**
     * Positions one row of chips left to right from {@link #CHIP_ROW_X}, stopping (not wrapping)
     * once the next chip would cross {@code maxX} — with the color-dot format, even all eleven
     * {@link ItemType} kinds nonzero at once comfortably fits one row at the default window width,
     * so this is a defensive cap for unusually narrow windows, not an expected everyday case; a
     * wrapped second row would need every fixed-position line below it (research/recent/alerts/
     * hints) to shift down too, which isn't worth the complexity for a case this rare.
     */
    private List<ChipLayout> layoutChips(List<ItemChip> chips, float rowY, float maxX) {
        List<ChipLayout> layout = new ArrayList<>();
        float x = CHIP_ROW_X;
        for (ItemChip chip : chips) {
            glyphLayout.setText(font, chip.text());
            float width = CHIP_RADIUS * 2 + 4 + glyphLayout.width;
            if (x + width > maxX) {
                break;
            }
            layout.add(new ChipLayout(chip.item(), x + CHIP_RADIUS, rowY - CHIP_RADIUS + 3,
                    Palette.itemColor(chip.item()), x + CHIP_RADIUS * 2 + 4, rowY, chip.text()));
            x += width + CHIP_GAP;
        }
        return layout;
    }

    /**
     * A light ring behind each dot, THEN the item's own color on top — a live bug report:
     * {@link Palette#itemColor} was picked for contrast against cargo on a belt or ore on the
     * ground, never against this panel's own near-black background ({@link Palette#PANEL_BG}).
     * {@code COAL}/{@code IRON_ORE} are themselves dark grays close to that background — without
     * the ring, their chips were nearly invisible, exactly what was reported.
     */
    private void drawChipCircles(List<ChipLayout> layout) {
        for (ChipLayout chip : layout) {
            shapes.setColor(Palette.HINT);
            shapes.circle(chip.circleX(), chip.circleY(), CHIP_RADIUS + 1.5f, 12);
        }
        for (ChipLayout chip : layout) {
            shapes.setColor(chip.color());
            shapes.circle(chip.circleX(), chip.circleY(), CHIP_RADIUS, 12);
        }
    }

    private void drawChipNumbers(List<ChipLayout> layout, float rowY) {
        if (layout.isEmpty()) {
            font.draw(batch, "-", CHIP_ROW_X, rowY);
            return;
        }
        for (ChipLayout chip : layout) {
            font.draw(batch, chip.text(), chip.textX(), chip.textY());
        }
    }

    /**
     * Нижняя панель построек: слот на каждый {@link BuildingType}, выбранный — обведён ярко.
     * Высота — {@link GfxConfig#HUD_BOTTOM_HEIGHT}, та же, на которую камера сузила вьюпорт
     * снизу (см. {@link #renderInfoPanel} — тот же приём для верхней панели).
     */
    private void renderHotbar(BuildingType selected, Direction facing, BuildingFactory buildingFactory) {
        int screenW = Gdx.graphics.getWidth();
        BuildingType[] types = BuildingType.values();
        float barH = GfxConfig.HUD_BOTTOM_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(0, 0, screenW, barH);
        for (int i = 0; i < types.length; i++) {
            shapes.setColor(Palette.SLOT_BG);
            shapes.rect(HotbarLayout.slotX(i, screenW), HotbarLayout.slotY(),
                    HotbarLayout.SLOT_SIZE, HotbarLayout.SLOT_SIZE);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < types.length; i++) {
            shapes.setColor(types[i] == selected ? Palette.SLOT_SELECTED : Palette.SLOT_BORDER);
            float x = HotbarLayout.slotX(i, screenW);
            float y = HotbarLayout.slotY();
            shapes.rect(x, y, HotbarLayout.SLOT_SIZE, HotbarLayout.SLOT_SIZE);
            if (types[i] == selected) {
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
        for (int i = 0; i < types.length; i++) {
            BuildingType type = types[i];
            float x = HotbarLayout.slotX(i, screenW);
            float y = HotbarLayout.slotY();
            TextureRegion icon = textures.forSprite(buildingFactory.prototype(type).texture());
            font.setColor(Color.WHITE);
            batch.draw(icon, x + iconPad, y + iconPad, iconSize, iconSize);

            font.getData().setScale(0.75f);
            font.setColor(type == selected ? Palette.SLOT_SELECTED : Palette.HINT);
            font.draw(batch, Integer.toString(i + 1), x + 4, y + HotbarLayout.SLOT_SIZE - 3);
            font.getData().setScale(0.62f);
            font.setColor(Palette.HINT);
            font.draw(batch, type.label(), x, y - 3);
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
     * selected recipe and remaining fuel, any building's {@code SpeedModule} level, a tunnel
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

        Building real = Building.unwrap(building);
        if (real instanceof Chest chest) {
            appendChestContents(lines, items, chest);
        } else if (real instanceof Furnace furnace) {
            appendFurnaceDetails(lines, building, furnace);
        } else if (real instanceof UndergroundBelt tunnel) {
            appendTunnelPairing(lines, world, at, building, tunnel);
        } else if (real instanceof Filter filter) {
            lines.add("Passes forward: " + filter.filterItem().label() + "  (F to change)");
            lines.add("Everything else -> secondary side");
        } else if (real instanceof Splitter) {
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
     * Chips for the «Produced» row — nonzero items only (live bug report; see {@link
     * #renderInfoPanel}'s own note). Rebuilt only if the sum of all counters changed (P4-05,
     * BUG_FIX_PROGRESS.md) — a cheap signature: totals only ever grow over {@code
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
     * Chips for the «Inventory» row (D-03, DEV_TASKS.md) — nonzero items only, same as {@link
     * #producedChips} (live bug report).
     *
     * <p>Signature is a weighted sum (weight = {@code rawId+1}), not a bare sum like
     * {@link #producedChips}: there, the total only ever grows; here amounts can fall (spending) or
     * rise (refunds), so "nothing changed" can't be told apart from "two items changed and the sum
     * happened to match" without a weight that distinguishes which item moved, not just by how much.
     */
    private List<ItemChip> inventoryChips(Registry<ItemType> items, PlayerInventoryView inventory) {
        long signature = 0;
        for (ItemType item : items.iterate()) {
            signature += (long) (items.rawId(item.id()) + 1) * inventory.amount(item);
        }
        if (signature == inventorySignature) {
            return inventoryChipsCache;
        }
        inventorySignature = signature;
        List<ItemChip> chips = new ArrayList<>();
        for (ItemType item : items.iterate()) {
            int amount = inventory.amount(item);
            if (amount > 0) {
                chips.add(new ItemChip(item, Integer.toString(amount)));
            }
        }
        return inventoryChipsCache = chips;
    }

    /**
     * Global alerts line (F-01, DEV_TASKS.md): «Alerts: NO_ORE x2   OUTPUT_FULL x1» — the card's
     * "aggregated list of problems, visible without clicking each building individually." Reads
     * {@link World#statusCounts()}, which {@code World} keeps current incrementally as buildings
     * tick/place/demolish — not a per-frame walk of the whole map (S1, CODE_REVIEW_2026-07-28.md:
     * this used to call {@link World#forEachBuilding} and allocate an {@link
     * com.rustorio.domain.Appearance} per building, every render frame, regardless of whether
     * anything had changed since the last one).
     *
     * <p>Signature is weighted like {@link #inventoryChips} (counts can rise AND fall as buildings
     * recover), not summed like {@link #producedChips} (whose totals only ever grow).
     */
    private String alerts(World world) {
        Map<BuildingStatus, Integer> counts = world.statusCounts();

        long signature = 0;
        for (BuildingStatus status : BuildingStatus.values()) {
            signature += (long) (status.ordinal() + 1) * counts.getOrDefault(status, 0);
        }
        if (signature == alertsSignature) {
            return alertsCache;
        }
        alertsSignature = signature;

        StringBuilder sb = new StringBuilder("Alerts:   ");
        boolean any = false;
        for (BuildingStatus status : BuildingStatus.values()) {
            if (status == BuildingStatus.WORKING) {
                continue;
            }
            int count = counts.getOrDefault(status, 0);
            if (count > 0) {
                sb.append(status.name()).append(" x").append(count).append("    ");
                any = true;
            }
        }
        return alertsCache = any ? sb.toString() : NO_ALERTS;
    }

    /**
     * Строка исследований: «Research: 12 pts (T for tech tree)   Unlocked: Fast mining». Больше не
     * показывает «Next» (P-02, DEV_TASKS.md) — с ветвящимся деревом и явным выбором «следующий по
     * порядку» ничего не значит; подробности (цены, предпосылки, что можно открыть прямо сейчас)
     * теперь в {@link TechTreeRenderer}, эта строка — только беглый итог.
     *
     * <p>Признак смены — {@code points}, упакованные вместе с битовой маской разблокированного
     * (P4-05, идея сохранена, но не просто {@code points} сам по себе): раньше очки только росли,
     * так что их одних хватало как признака. Теперь {@link Research#unlock} их тратит — значит
     * {@code points} может пройти 20 → 0 → снова 20, а разблокированный набор при этом отличается;
     * без маски кэш ошибочно счёл бы такую смену «ничего не изменилось».
     */
    private String research(ResearchView research) {
        int unlockedMask = 0;
        for (Tech tech : Tech.values()) {
            if (research.isUnlocked(tech)) {
                unlockedMask |= 1 << tech.ordinal();
            }
        }
        long signature = ((long) unlockedMask << 32) | (research.points() & 0xFFFFFFFFL);
        if (signature == researchSignature) {
            return researchCache;
        }
        researchSignature = signature;
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
        return researchCache = sb.toString();
    }

    /**
     * Строка лога: «Recent:   Iron Ore  Iron Plate  Iron Ore» — самый свежий слева. {@code
     * ProductionLogView} не отдаёт ничего дешевле самого списка (P4-05), так что {@code
     * log.recent()} всё равно копируется каждый кадр — кеш здесь экономит только пересборку
     * строки, не саму копию.
     */
    private String recent(ProductionLogView log) {
        List<ItemType> current = log.recent();
        if (current.equals(recentSignature)) {
            return recentCache;
        }
        recentSignature = current;
        StringBuilder sb = new StringBuilder("Recent:   ");
        if (current.isEmpty()) {
            return recentCache = sb.append('-').toString();
        }
        for (ItemType item : current) {
            sb.append(item.label()).append("  ");
        }
        return recentCache = sb.toString();
    }
}

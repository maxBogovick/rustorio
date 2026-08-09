package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Cell;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.PlacementRule;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TransportNode;
import com.rustorio.domain.building.UndergroundBelt;
import com.rustorio.domain.world.World;
import java.util.List;
import java.util.Optional;

/**
 * Слой «поверх мира» (подсветки, линии, тосты). Красит две вещи: стрелку направления над каждым
 * зданием, у которого оно вообще есть, и красную рамку вокруг входа подземной ленты без пары в
 * пределах дальности.
 *
 * <p><b>Зачем стрелка.</b> Лента/печь/туннель держат направление молча внутри себя — ни один
 * спрайт в игре не нарисован с явной «мордой», так что даже после C-01 (DEV_TASKS.md, поворот
 * спрайта под направление в {@link BuildingRenderer}) угол поворота плейсхолдер-графики на глаз
 * не читается вообще. Первая попытка спрятать стрелку за Alt (раз спрайт «и так» повёрнут) была
 * ошибкой — живая проверка сразу показала, что без неё непонятно, куда что везёт. Обе стрелки —
 * основная (см. {@link Building#outputDirection()}) и вторая, сплиттера ({@link
 * Building#secondaryOutputDirection()}) — снова всегда видимы, как до C-01.
 *
 * <p><b>Зачем подсветка тоннеля.</b> Вход и выход подземки визуально ничем не отличаются от
 * рабочей пары — те же спрайты, то же поведение на экране, если по ним ничего не едет. Игрок,
 * поставивший вход дальше {@code MAX_RANGE} от любого подходящего выхода (или с несовпадающим
 * направлением), не получает НИКАКОГО сигнала — тоннель просто тихо не работает, и это неотличимо
 * от «пока нечего везти». Красная рамка вокруг такого входа — тот самый сигнал.
 *
 * <p><b>Зачем призрак постройки (F-02, DEV_TASKS.md).</b> До этого игрок кликал вслепую: {@code
 * World.place} уже умело отвечать «нельзя» ({@link com.rustorio.domain.building.BuildingFactory#canPlace})
 * и уже проверяло ресурсы игрока (D-03), но об этом никто не спрашивал ДО клика — единственным
 * сигналом был сам неудавшийся клик. {@link #renderBuildGhost} рисует то же самое здание,
 * полупрозрачно, под курсором (или вдоль всей ещё не отпущенной протяжки — {@link HudState#dragTiles()}),
 * и красит рамку каждой клетки по факту, разрешит ли {@code World.place} построить здесь И
 * хватит ли на это ресурсов — те же две проверки, что реально выполнит клик, просто заранее.
 *
 * <p><b>Причина невозможности (живой баг-репорт).</b> Красная рамка сама по себе говорит только
 * «нельзя», не «почему нельзя» — занята клетка, непроходимая земля, буру нужна руда, или просто не
 * хватает {@code IRON_PLATE}, выглядят ОДИНАКОВО. {@link #reasonInvalid} различает эти случаи и
 * {@link #renderBuildGhost} печатает конкретную причину текстом над курсором — только для одной
 * клетки под курсором (не во время протяжки: причина у каждой клетки протяжки может быть своя, а
 * места под текст на каждую — нет).
 *
 * <p><b>Alt-режим (F-04, DEV_TASKS.md).</b> Текстовые подписи ({@link #renderInfoLabels} — статус
 * здания словом, содержимое ящика по сортам) рисуются только пока зажат Alt ({@link
 * HudState#altOverlay()}) — это НОВАЯ информация, которой раньше на карте не было вовсе, есть
 * смысл прятать её по требованию. Обе стрелки и рамка непарного туннеля — НЕ спрятаны: и то, и
 * другое единственный сигнал того, что показывают (куда едет груз; что тоннель непарен), прятать
 * за Alt нечем компенсировать — см. класс-javadoc выше про C-01.
 */
final class OverlayRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final Textures textures;
    private final BitmapFont font;
    private final GameCamera camera;
    private final Grid grid;

    OverlayRenderer(SpriteBatch batch, ShapeRenderer shapes, Textures textures, BitmapFont font, GameCamera camera,
            Grid grid) {
        this.batch = batch;
        this.shapes = shapes;
        this.textures = textures;
        this.font = font;
        this.camera = camera;
        this.grid = grid;
    }

    /**
     * Direction arrows and the orphaned-tunnel outline — always visible, exactly as before F-04 —
     * plus, only while Alt is held ({@link HudState#altOverlay()}), text labels for building
     * status and chest contents by kind (see {@link #renderInfoLabels}).
     */
    void renderWorld(World world, TileRange visible, HudState hud) {
        float tile = GfxConfig.TILE;
        int minX = visible.minX();
        int minY = visible.minY();
        int maxX = visible.maxX();
        int maxY = visible.maxY();

        // Both arrows, always visible — a live bug report: C-01's sprite rotation alone doesn't
        // read as "facing a direction" on this placeholder art (nothing about it visually signals
        // an orientation), so hiding the arrow behind Alt left the map genuinely unreadable, not
        // just redundant. Back to always-on, exactly as before C-01.
        //
        // Art redesign exception: BELT's new sprite draws its own amber chevrons pointing the way
        // cargo flows — the arrow here would just repeat what the belt already shows (live bug
        // report). Every OTHER directional building still gets the arrow; their sprites don't
        // (yet) make direction obvious on their own the way the belt's chevrons do. Compared by
        // {@code type()}, not {@code instanceof Belt} — same convention this file already uses
        // for UNDERGROUND_IN below, and the ArchUnit ratchet (E0-03, ENGINE_TASKS.md) caps
        // instanceof-on-concrete-building-type specifically, not this.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.DIRECTION_ARROW);
        world.forEachBuildingIn(minX, minY, maxX, maxY, (x, y, building) -> {
            // A transport node's own sprite already shows where it points (the belt's chevrons),
            // so an arrow on top is noise. A capability check, not `type() == BELT`: a mod's own
            // conveyor is a TransportNode too and used to get the redundant arrow, because it
            // borrows BELT only by convention.
            if (building instanceof TransportNode) {
                return;
            }
            float cx = grid.x(x) + tile / 2f;
            float cy = grid.yBottom(y) + tile / 2f;
            building.outputDirection().ifPresent(direction -> drawArrow(cx, cy, direction, tile));
            building.secondaryOutputDirection().ifPresent(direction -> drawArrow(cx, cy, direction, tile));
        });
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.T_BAD);
        world.forEachBuildingIn(minX, minY, maxX, maxY, (x, y, building) -> {
            if (building instanceof UndergroundBelt in
                    && in.isEntrance()
                    && in.findPartner(world, x, y).isEmpty()) {
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        });
        shapes.end();

        if (hud.altOverlay()) {
            renderNetworkOverlay(world, visible, tile);
            renderInfoLabels(world, visible, tile);
        }
    }

    /**
     * Under Alt, two things that are otherwise invisible on the map: which fluid network each pipe
     * or tank belongs to (a faint fill in the network's colour), and how far each pole's grid
     * reaches (a square outline in the grid's colour). Both colours come from {@link
     * Palette#networkHue} of the network's anchor, so every tile of one network — and every pole of
     * one grid — is drawn the same, deterministically (see {@link NetworkTint}).
     *
     * <p>Two passes because a filled tint and a line outline are different {@link ShapeRenderer}
     * modes that cannot share one {@code begin}/{@code end}. Both walk only the visible range and
     * only while Alt is held, the same budget the label pass below already spends — this is an
     * on-demand overlay, not the per-frame hot path.
     *
     * <p>A pole is spotted by its data, not its class: any prototype whose {@link PowerSpec} has a
     * coverage {@code radius} draws a reach square, so a mod's own pole works here with no change.
     */
    private void renderNetworkOverlay(World world, TileRange visible, float tile) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(), (x, y, building) -> {
            Optional<Cell> fluidNet = world.fluidNetworkAt(x, y);
            if (fluidNet.isPresent()) {
                Color hue = Palette.networkHue(fluidNet.get());
                shapes.setColor(hue.r, hue.g, hue.b, 0.30f);
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        });
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(), (x, y, building) -> {
            PowerSpec spec = world.buildingFactory().prototype(building.prototypeId()).power();
            if (spec == null || spec.radius() <= 0) {
                return;
            }
            Optional<Cell> grid_ = world.powerNetworkAt(x, y);
            if (grid_.isEmpty()) {
                return;
            }
            int r = spec.radius();
            Color hue = Palette.networkHue(grid_.get());
            shapes.setColor(hue.r, hue.g, hue.b, 0.85f);
            float side = tile * (2 * r + 1);
            shapes.rect(grid.x(x - r), grid.yBottom(y + r), side, side);
        });
        shapes.end();
    }

    /**
     * One small text line above any cell that has something worth saying: its {@code
     * BuildingStatus} when it isn't {@code WORKING} (F-01), and — for a chest — its contents by
     * kind (D-02). Both share one line/one pass since a full chest shows both at once.
     */
    private void renderInfoLabels(World world, TileRange visible, float tile) {
        Registry<ItemType> items = world.buildingFactory().items();
        batch.begin();
        font.getData().setScale(0.5f);
        font.setColor(Color.WHITE);
        world.forEachBuildingIn(visible.minX(), visible.minY(), visible.maxX(), visible.maxY(), (x, y, building) -> {
            String line = infoLine(building, items);
            if (!line.isEmpty()) {
                font.draw(batch, line, grid.x(x), grid.yBottom(y) + tile + 11f);
            }
        });
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /** Package-private (not private) so {@link OverlayRendererGhostTest}-style headless tests can call it directly — pure logic, no libGDX. */
    static String infoLine(Building building, Registry<ItemType> items) {
        StringBuilder sb = new StringBuilder();
        BuildingStatus status = building.appearance().status();
        if (status != BuildingStatus.WORKING) {
            sb.append(status).append(' ');
        }
        if (building instanceof Chest chest) {
            for (ItemType item : items.iterate()) {
                int amount = chest.amount(item);
                if (amount > 0) {
                    sb.append(item.label()).append(':').append(amount).append(' ');
                }
            }
        }
        return sb.toString().stripTrailing();
    }

    /**
     * Треугольник-стрелка с центром клетки {@code (cx, cy)}, остриём в сторону {@code direction}.
     *
     * <p>{@code direction.dy()} — координата СЕТКИ (вниз = увеличение строки, урок 11), а экранный
     * Y растёт ВВЕРХ ({@link Grid}) — тот же переворот знака, что уже делает {@code Grid.yBottom}
     * для клеток, здесь нужен явно, потому что стрелка считает пиксели сама, в обход {@code Grid}.
     */
    private void drawArrow(float cx, float cy, Direction direction, float tile) {
        float dx = direction.dx();
        float dy = -direction.dy();
        float perpX = -dy;
        float perpY = dx;

        float tipX = cx + dx * tile * 0.34f;
        float tipY = cy + dy * tile * 0.34f;
        float baseX = cx + dx * tile * 0.14f;
        float baseY = cy + dy * tile * 0.14f;
        float halfWidth = tile * 0.13f;

        shapes.triangle(
                tipX, tipY,
                baseX + perpX * halfWidth, baseY + perpY * halfWidth,
                baseX - perpX * halfWidth, baseY - perpY * halfWidth);
    }

    /**
     * A translucent copy of {@code hud.selected()}, one per planned cell, each outlined green or
     * red by whether THAT cell can actually be built on (F-02, DEV_TASKS.md). Mid-drag ({@link
     * HudState#dragTiles()} non-empty) shows the whole line touched so far, exactly as {@code
     * CompositeAction} will apply it tile-by-tile on release; otherwise just the single cell under
     * the cursor — and only while the cursor is actually over the world, not one of the HUD
     * panels, where {@link GameCamera#pickTile} would still return SOME map cell, just not the one
     * the player is looking at.
     */
    void renderBuildGhost(World world, HudState hud) {
        boolean dragging = !hud.dragTiles().isEmpty();
        if (!dragging && !cursorOverWorld()) {
            return;
        }
        List<TilePos> tiles = dragging
                ? hud.dragTiles()
                : List.of(camera.pickTile(Gdx.input.getX(), Gdx.input.getY()));

        ContentId type = hud.selected();
        BuildingPrototype prototype = world.buildingFactory().prototype(type);
        boolean[] afford = affordability(world, prototype, tiles);

        float tile = GfxConfig.TILE;
        float footprintW = tile * prototype.footprintWidth();
        float footprintH = tile * prototype.footprintHeight();
        TextureRegion region = textures.forSprite(prototype.texture());
        batch.begin();
        batch.setColor(1f, 1f, 1f, 0.55f);
        for (TilePos t : tiles) {
            batch.draw(region, grid.x(t.x()), grid.yBottom(t.y()), footprintW, footprintH);
        }
        batch.setColor(Color.WHITE);
        if (!dragging) {
            TilePos t = tiles.get(0);
            if (!(canPlaceHere(world, prototype, t.x(), t.y()) && afford[0])) {
                font.getData().setScale(0.6f);
                font.setColor(Palette.GHOST_INVALID);
                font.draw(batch, reasonInvalid(world, prototype, t.x(), t.y()), grid.x(t.x()), grid.yBottom(t.y()) + footprintH + 14f);
                font.getData().setScale(1f);
                font.setColor(Color.WHITE);
            }
        }
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (int i = 0; i < tiles.size(); i++) {
            TilePos t = tiles.get(i);
            shapes.setColor(canPlaceHere(world, prototype, t.x(), t.y()) && afford[i]
                    ? Palette.GHOST_VALID : Palette.GHOST_INVALID);
            shapes.rect(grid.x(t.x()), grid.yBottom(t.y()), footprintW, footprintH);
        }
        shapes.end();
    }

    /**
     * The specific reason {@code prototype} can't go at {@code (x, y)} right now — a live bug
     * report: the red outline alone couldn't tell "occupied" from "no ore here" from "can't afford
     * it." Checked in the same order {@link #canPlaceHere}/{@code World.place} itself would fail —
     * the first one that's actually wrong is the one reported, not every problem at once.
     */
    /** Package-private (not private) so a headless test can call it directly — pure logic, no libGDX, same reason as {@link #infoLine}. */
    static String reasonInvalid(World world, BuildingPrototype prototype, int x, int y) {
        int w = prototype.footprintWidth();
        int h = prototype.footprintHeight();
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!world.inBounds(cx, cy)) {
                    return "out of bounds";
                }
                if (!world.isFree(cx, cy)) {
                    return "cell occupied";
                }
            }
        }
        OreLayout oreLayout = world.buildingFactory().oreLayout();
        if (!oreLayout.isPassable(x, y)) {
            return "impassable terrain";
        }
        // Compares the RULE itself, not a BuildingType — any prototype (vanilla MINER or a modded
        // one) registered with the "needs ore" rule gets the same specific message, not just the
        // one closed constant this used to name explicitly.
        if (prototype.placementRule() == PlacementRule.NEEDS_ORE && !oreLayout.hasOre(x, y)) {
            return "no ore here";
        }
        BuildingCost cost = prototype.cost();
        int have = world.inventory().amount(cost.item());
        if (have < cost.amount()) {
            return "need " + cost.amount() + " " + cost.item().label() + " (have " + have + ")";
        }
        // canPlaceHere() and affordability() both agreed this tile is fine — reasonInvalid() is
        // only ever called after they disagreed, so this is unreachable in practice; still, an
        // empty string is a safe, visible-if-wrong fallback rather than throwing mid-render.
        return "";
    }

    /**
     * Whether {@code prototype} can afford to be placed at each of {@code tiles}, IN ORDER — a
     * running balance, not each cell checked against the player's full current stock
     * independently: {@code CompositeAction} spends one {@code PlaceAction} at a time as it walks
     * the same list on release, so a five-tile belt line the player can only afford three of must
     * show exactly the first three as buildable, not all five (each independently affordable) or
     * none (the total unaffordable). A cell that fails the geometry check contributes nothing to
     * the running balance — a failed {@code PlaceAction} refunds immediately, so it never actually
     * spends.
     */
    private static boolean[] affordability(World world, BuildingPrototype prototype, List<TilePos> tiles) {
        BuildingCost cost = prototype.cost();
        ItemType item = cost.item();
        int remaining = world.inventory().amount(item);
        boolean[] afford = new boolean[tiles.size()];
        for (int i = 0; i < tiles.size(); i++) {
            TilePos t = tiles.get(i);
            if (!canPlaceHere(world, prototype, t.x(), t.y())) {
                afford[i] = true; // a blocked cell doesn't itself cost anything — see the javadoc above
                continue;
            }
            afford[i] = remaining >= cost.amount();
            if (afford[i]) {
                remaining -= cost.amount();
            }
        }
        return afford;
    }

    /**
     * The exact geometry check {@link World#place} itself makes, read-only — see the class
     * javadoc's F-02 note. Walks the whole footprint, not just the anchor cell (C1, live bug
     * report): before this, a multi-cell {@code ASSEMBLER} ghost only ever checked its anchor, so
     * it could show green over a cell whose neighbor was occupied — the click would then silently
     * do nothing, since {@code World.place} itself checks every cell.
     *
     * <p>Package-private, not {@code private} (A4, CODE_REVIEW_2026-07-28.md): this is the one
     * piece of the ghost that's pure {@code World}/{@code BuildingPrototype} arithmetic with no
     * libGDX involved, so {@code OverlayRendererGhostTest} (same package) calls it directly to pin
     * down that it never again drifts out of sync with {@link World#place}, without needing a
     * windowed environment to construct an {@code OverlayRenderer} at all.
     */
    static boolean canPlaceHere(World world, BuildingPrototype prototype, int x, int y) {
        int w = prototype.footprintWidth();
        int h = prototype.footprintHeight();
        for (int dx = 0; dx < w; dx++) {
            for (int dy = 0; dy < h; dy++) {
                int cx = x + dx;
                int cy = y + dy;
                if (!world.inBounds(cx, cy) || !world.isFree(cx, cy)
                        || !world.buildingFactory().canPlace(prototype.id(), cx, cy)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * The world viewport sits between the HUD's top and bottom bars ({@link Renderer#render}) —
     * {@link GameCamera#pickTile} doesn't know or care, it'll happily map a cursor over either
     * panel to whatever map cell the math lands on. Logical points, same convention {@code
     * Gdx.input}/{@link GameCamera#pickTile} already use, not backbuffer pixels.
     *
     * <p>The formula itself is {@link GfxConfig#isOverWorld}, shared with the input layer rather
     * than written out here a second time: this predicate hiding the ghost while the drag collector
     * did NOT hide the gesture is exactly what let a click on the bottom build panel place an
     * invisible building below the screen edge.
     */
    private static boolean cursorOverWorld() {
        return GfxConfig.isOverWorld(Gdx.input.getY(), Gdx.graphics.getHeight());
    }
}

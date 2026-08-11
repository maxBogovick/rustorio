package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.IntMap;
import com.graphics.GfxConfig;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.api.content.vanilla.VanillaItems;
import java.util.Optional;

/**
 * Ground layer: dirt, ore patches, terrain obstacles and grid lines. Only walks the visible cells
 * from {@link TileRange}.
 *
 * <p>Which ore (if any) lies under a cell comes from the same {@link OreLayout} the world builds
 * miners against — injected here rather than read from a static table, so this layer stays in
 * sync with whatever layout a given {@code World} was actually configured with. Iron and bronze
 * are colored differently ({@link #oreColor}) so a player can tell them apart before ever
 * building a miner, instead of checking the HUD after the fact.
 *
 * <p><b>Owner decision (X-02, DEV_TASKS.md):</b> terrain is checked and colored before ore — the
 * two never actually overlap ({@link OreLayout#terrainAt}'s implementations guarantee ore always
 * wins any accidental overlap at generation time), so the order here is only a tie-break that in
 * practice never triggers, not a real precedence decision.
 *
 * <p><b>Owner decision (D-05, DEV_TASKS.md):</b> coal gets BOTH a translucent tint ({@link
 * Palette#ORE_COAL}, same treatment as iron/bronze — see {@link #oreTint}) AND {@code
 * resources/coal_ore.png} drawn on top, in the same {@link SpriteBatch} pass as the base terrain
 * tile — the card's acceptance criterion names the file specifically ("resources/coal_ore.png
 * используется в рендере"). Art redesign: the base ground/water/rock layer used to be a flat
 * {@link ShapeRenderer} fill; it's now a real tiled texture ({@link Textures#terrainGround} and
 * friends), with ore drawn as a translucent color wash on top rather than replacing it outright —
 * a vein should read as something IN the ground, not a same-shape tile stacked over it.
 */
final class WorldRenderer {

    /** Ore tint drawn over the ground texture — translucent, not opaque (see {@link #render}'s own comment). */
    private static final float ORE_TINT_ALPHA = 0.5f;

    private final ShapeRenderer shapes;
    private final SpriteBatch batch;
    private final Grid grid;
    private final OreLayout oreLayout;
    private final Textures textures;

    WorldRenderer(ShapeRenderer shapes, SpriteBatch batch, Grid grid, OreLayout oreLayout, Textures textures) {
        this.shapes = shapes;
        this.batch = batch;
        this.grid = grid;
        this.oreLayout = oreLayout;
        this.textures = textures;
    }

    void render(TileRange range) {
        float tile = GfxConfig.TILE;

        // Базовый слой ландшафта — раньше плоская заливка цветом (ShapeRenderer), теперь тайловая
        // текстура (арт-редизайн, см. Textures#terrainGround/terrainWater/terrainRock); уголь тут
        // же поверх своей клетки, в ТОМ ЖЕ проходе SpriteBatch — не отдельным batch.begin/end, как
        // раньше, раз оба рисуются одним и тем же слоем поверх одного и того же тайла.
        batch.begin();
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                batch.draw(terrainTexture(x, y), grid.x(x), grid.yBottom(y), tile, tile);
                if (oreLayout.oreAt(x, y).equals(Optional.of(VanillaItems.COAL))) {
                    batch.draw(textures.coalOre(), grid.x(x), grid.yBottom(y), tile, tile);
                }
            }
        }
        batch.end();

        // Руда — полупрозрачный цветной оверлей ПОВЕРХ текстуры земли, не сплошная заливка: жила
        // руды должна читаться как что-то В земле, а не как отдельный чужеродный плиточный слой.
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                // Terrain first: the two are mutually exclusive (terrain never carries ore — see
                // oreTint), so this is one pass over the cells, not two overlapping ones. Written
                // out rather than as terrainTint(...).or(() -> oreTint(x, y)): the loop counters a
                // lambda would capture are not effectively final.
                Optional<Color> terrain = terrainTint(x, y);
                Optional<Color> tint = terrain.isPresent() ? terrain : oreTint(x, y);
                if (tint.isPresent()) {
                    shapes.setColor(tint.get());
                    shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
                }
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.GRID);
        for (int y = range.minY(); y <= range.maxY(); y++) {
            for (int x = range.minX(); x <= range.maxX(); x++) {
                shapes.rect(grid.x(x), grid.yBottom(y), tile, tile);
            }
        }
        shapes.end();
    }

    /**
     * Which base tile texture a cell draws — terrain wins over ore (see the class javadoc for why
     * the order never actually matters). No cell is left without a tile: a terrain kind this build
     * has no art for still draws the ground tile, and {@link #terrainTint} colors it.
     *
     * <p>An {@code if} chain over the two vanilla obstacles rather than the exhaustive {@code
     * switch} this used to be: terrain is content now, so there is no complete set of cases to
     * cover — the vanilla two keep their hand-drawn tiles, and everything else (a mod's, or one
     * authored in the content editor) is handled by the same fallback, exactly the way {@code
     * oreColor} below already treats vanilla ores versus everybody else's.
     */
    private TextureRegion terrainTexture(int x, int y) {
        Optional<ItemType> terrain = oreLayout.terrainAt(x, y);
        if (terrain.isEmpty()) {
            return textures.terrainGround();
        }
        ItemType kind = terrain.get();
        if (kind.equals(VanillaItems.WATER)) {
            return textures.terrainWater();
        }
        if (kind.equals(VanillaItems.ROCK)) {
            return textures.terrainRock();
        }
        return textures.terrainGround(); // a mod's own terrain — terrainTint colors this tile
    }

    /**
     * An opaque overlay for a terrain kind with no tile of its own, empty for the vanilla two (whose
     * own textures already say what they are) and for plain ground. Opaque, unlike the ore tint: ore
     * reads as something IN the ground, while terrain IS the ground — a translucent obstacle would
     * read as a patch of dirt with a stain on it rather than as a thing you can't build on.
     */
    private Optional<Color> terrainTint(int x, int y) {
        return oreLayout.terrainAt(x, y)
                .filter(kind -> !VanillaItems.isVanillaTerrain(kind))
                .map(Palette::itemColor);
    }

    /** Translucent ore color for {@link #render}'s overlay pass — empty for a cell with no ore (including any non-{@code GROUND} terrain, which never carries ore — see the class javadoc). */
    private Optional<Color> oreTint(int x, int y) {
        if (oreLayout.terrainAt(x, y).isPresent()) {
            return Optional.empty();
        }
        return oreLayout.oreAt(x, y).map(WorldRenderer::oreColor);
    }

    /**
     * Ground-tile ore color, at {@link #ORE_TINT_ALPHA}. The three vanilla ores keep their own
     * hand-picked palette entries, deliberately NOT {@link ItemType#colorRgb} (the same ore reads
     * as blue on the map but neutral gray once picked up — see {@code Palette}'s own comment on
     * why); any other ore — a mod's, or one authored in the content editor — falls back to its
     * own {@link ItemType#colorRgb} via {@link Palette#itemColor}, tinted, rather than one flat
     * default: a mod's ore must be tellable apart from every OTHER ore on the map, not just from
     * the vanilla three.
     *
     * <p>Matched by {@link ItemType#equals}, not {@code ==}: a map loaded through the mod pipeline
     * (including one authored in the content editor) resolves its ore {@link ItemType}s from that
     * map's own {@code Registry}, a DIFFERENT instance than {@link VanillaItems}' constants even
     * for the very same vanilla id. Reference equality never matched for such a map, so every ore
     * on it fell through to the same default tint regardless of its actual kind.
     */
    static Color oreColor(ItemType ore) {
        if (ore.equals(VanillaItems.BRONZE_ORE)) {
            return TINT_ORE_BRONZE;
        }
        if (ore.equals(VanillaItems.COAL)) {
            return TINT_ORE_COAL;
        }
        if (ore.equals(VanillaItems.IRON_ORE)) {
            return TINT_ORE;
        }
        // get/put, not computeIfAbsent: a lambda that captures ore would itself be a fresh
        // allocation on every call on a miss AND (per the JLS, capturing lambdas are not guaranteed
        // to be reused across invocations the way a non-capturing one's singleton instance is) —
        // defeats the exact hot-path-allocation avoidance this cache exists for. IntMap (libGDX),
        // not a JDK Map<Integer,Color>: a boxed Integer key would itself allocate on every call for
        // any rgb outside the JVM's cached Integer range (-128..127), which real ore colors always
        // are — the very allocation this whole cache exists to avoid, just moved into the key.
        int rgb = ore.colorRgb();
        Color cached = CUSTOM_ORE_TINTS.get(rgb);
        if (cached != null) {
            return cached;
        }
        Color tint = withAlpha(Palette.itemColor(ore), ORE_TINT_ALPHA);
        CUSTOM_ORE_TINTS.put(rgb, tint);
        return tint;
    }

    // Precomputed once, not reallocated per tile per frame — same reasoning as Palette#itemColor's
    // own cache (that one's comment explains why a fresh Color per call is a hot-path allocation).
    private static final Color TINT_ORE = withAlpha(Palette.ORE, ORE_TINT_ALPHA);
    private static final Color TINT_ORE_BRONZE = withAlpha(Palette.ORE_BRONZE, ORE_TINT_ALPHA);
    private static final Color TINT_ORE_COAL = withAlpha(Palette.ORE_COAL, ORE_TINT_ALPHA);

    /**
     * Memoizes the alpha-tinted color for a non-vanilla ore, keyed by its raw {@code colorRgb}
     * (not by {@link ItemType} identity) — same reasoning as {@link Palette#itemColor}'s own
     * cache: called once per visible ground tile EVERY FRAME, and keying by the packed int rather
     * than the item survives a mod's {@code Registry.update()} changing a color after the first
     * tile using it was already drawn.
     */
    private static final IntMap<Color> CUSTOM_ORE_TINTS = new IntMap<>();

    private static Color withAlpha(Color base, float alpha) {
        return new Color(base.r, base.g, base.b, alpha);
    }
}

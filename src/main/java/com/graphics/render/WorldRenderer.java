package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.Terrain;
import com.rustorio.domain.VanillaItems;
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
                Optional<Color> tint = oreTint(x, y);
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

    /** Which base tile texture a cell draws — terrain wins over ore (see the class javadoc for why the order never actually matters). */
    private TextureRegion terrainTexture(int x, int y) {
        return switch (oreLayout.terrainAt(x, y)) {
            case WATER -> textures.terrainWater();
            case ROCK -> textures.terrainRock();
            case GROUND -> textures.terrainGround();
        };
    }

    /** Translucent ore color for {@link #render}'s overlay pass — empty for a cell with no ore (including any non-{@code GROUND} terrain, which never carries ore — see the class javadoc). */
    private Optional<Color> oreTint(int x, int y) {
        if (oreLayout.terrainAt(x, y) != Terrain.GROUND) {
            return Optional.empty();
        }
        return oreLayout.oreAt(x, y).map(WorldRenderer::oreColor);
    }

    /**
     * Ground-tile ore color, at {@link #ORE_TINT_ALPHA} — deliberately its OWN palette (not
     * {@link ItemType#colorRgb}, which colors the mined cargo instead): the same ore reads as blue
     * on the map but neutral gray once picked up (see {@code Palette}'s own comment on why).
     * Reference equality against {@link VanillaItems}' constants, not a {@code switch}: {@link
     * ItemType} is a record with no fixed case set — a mod's ore isn't one of these three, so it
     * falls through to the {@code IRON_ORE}-style default rather than failing to compile the
     * moment a new ore is registered.
     */
    private static Color oreColor(ItemType ore) {
        if (ore == VanillaItems.BRONZE_ORE) {
            return TINT_ORE_BRONZE;
        }
        if (ore == VanillaItems.COAL) {
            return TINT_ORE_COAL;
        }
        return TINT_ORE; // IRON_ORE, and any other/modded ore — the only other kind oreAt() ever actually returns today
    }

    // Precomputed once, not reallocated per tile per frame — same reasoning as Palette#itemColor's
    // own cache (that one's comment explains why a fresh Color per call is a hot-path allocation).
    private static final Color TINT_ORE = withAlpha(Palette.ORE, ORE_TINT_ALPHA);
    private static final Color TINT_ORE_BRONZE = withAlpha(Palette.ORE_BRONZE, ORE_TINT_ALPHA);
    private static final Color TINT_ORE_COAL = withAlpha(Palette.ORE_COAL, ORE_TINT_ALPHA);

    private static Color withAlpha(Color base, float alpha) {
        return new Color(base.r, base.g, base.b, alpha);
    }
}

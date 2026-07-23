package com.graphics.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.BuildingType;
import com.rustorio.Direction;
import com.rustorio.Item;
import com.rustorio.ProductionLog;
import com.rustorio.ProductionStats;
import com.rustorio.Research;
import com.rustorio.Sprite;
import com.rustorio.Tech;

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
 * одного и того же события «предмет произведён» ({@link ProductionStats}, {@link ProductionLog}
 * — урок 14). HUD дальше про них ничего не знает: просто читает и показывает.
 */
final class HudRenderer {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;
    private final Textures textures;

    HudRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font, Textures textures) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
        this.textures = textures;
    }

    void render(BuildingType selected, Direction facing, ProductionStats stats, Research research,
            ProductionLog log, boolean paused, int speed) {
        renderInfoPanel(stats, research, log, paused, speed);
        renderHotbar(selected, facing);
    }

    /**
     * Верхняя панель: заголовок, пауза/скорость, статистика, исследования, лог, подсказки.
     *
     * <p>Высота панели — {@link GfxConfig#HUD_TOP_HEIGHT}, ТА ЖЕ константа, на которую камера
     * сузила свой вьюпорт ({@link GameCamera#resize}): подложка и «дыра» в мире, которую она
     * закрывает, всегда совпадают по построению, не по совпадению двух чисел в разных файлах.
     */
    private void renderInfoPanel(ProductionStats stats, Research research, ProductionLog log,
            boolean paused, int speed) {
        int screenW = Gdx.graphics.getWidth();
        float top = Gdx.graphics.getHeight();
        float panelH = GfxConfig.HUD_TOP_HEIGHT;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(0, top - panelH, screenW, panelH);
        shapes.end();

        batch.begin();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.2f);
        font.draw(batch, "Rustorio", 16, top - 14);

        font.setColor(paused ? Palette.IDLE : Palette.WORKING);
        font.draw(batch, paused ? "PAUSED" : ("Speed: " + speed + "x"), 220, top - 14);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);

        font.draw(batch, produced(stats), 16, top - 36);
        font.draw(batch, research(research), 16, top - 54);
        font.draw(batch, recent(log), 16, top - 72);

        font.setColor(Palette.HINT);
        font.getData().setScale(0.8f);
        font.draw(batch, "R rotate   U upgrade   Ctrl+Z undo   Ctrl+Y redo   F5 save   F9 load",
                16, top - 92);
        font.draw(batch, "WASD pan   wheel zoom   Space pause   [ ] speed   click or 1-9 to build",
                16, top - 108);
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Нижняя панель построек: слот на каждый {@link BuildingType}, выбранный — обведён ярко.
     * Высота — {@link GfxConfig#HUD_BOTTOM_HEIGHT}, та же, на которую камера сузила вьюпорт
     * снизу (см. {@link #renderInfoPanel} — тот же приём для верхней панели).
     */
    private void renderHotbar(BuildingType selected, Direction facing) {
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
            TextureRegion icon = textures.forSprite(menuSprite(type));
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
     * Внешность здания «в покое» для иконки в меню — независима от состояния конкретной
     * постройки (буфер, груз): в меню нет живого здания, только сорт, который можно выбрать.
     * Печь и пресс делят один и тот же спрайт (см. {@link com.rustorio.Furnace}, урок 16).
     */
    private static Sprite menuSprite(BuildingType type) {
        return switch (type) {
            case MINER -> Sprite.MINER;
            case CHEST -> Sprite.CHEST;
            case FURNACE, PRESS -> Sprite.FURNACE_COLD;
            case BELT -> Sprite.BELT_EMPTY;
            case SPLITTER -> Sprite.SPLITTER;
            case UNDERGROUND_IN -> Sprite.UNDERGROUND_IN;
            case UNDERGROUND_OUT -> Sprite.UNDERGROUND_OUT;
            case LAB -> Sprite.LAB;
        };
    }

    /** Строка статистики: «Produced:   IRON_ORE 12    IRON_PLATE 4». */
    private static String produced(ProductionStats stats) {
        StringBuilder sb = new StringBuilder("Produced:   ");
        for (Item item : Item.values()) {
            sb.append(item.name()).append(' ').append(stats.total(item)).append("    ");
        }
        return sb.toString();
    }

    /** Строка исследований: «Research: 12 pts   Next: Fast smelting (20)   Unlocked: Fast mining». */
    private static String research(Research research) {
        StringBuilder sb = new StringBuilder("Research: ").append(research.points()).append(" pts   ");
        Tech next = nextLocked(research);
        if (next != null) {
            sb.append("Next: ").append(next.label()).append(" (").append(next.cost()).append(")   ");
        }
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

    /** Первая по порядку ещё не открытая технология — null, если открыты уже все. */
    private static Tech nextLocked(Research research) {
        for (Tech tech : Tech.values()) {
            if (!research.isUnlocked(tech)) {
                return tech;
            }
        }
        return null;
    }

    /** Строка лога: «Recent:   IRON_ORE  IRON_PLATE  IRON_ORE» — самый свежий слева. */
    private static String recent(ProductionLog log) {
        StringBuilder sb = new StringBuilder("Recent:   ");
        if (log.recent().isEmpty()) {
            return sb.append('-').toString();
        }
        for (Item item : log.recent()) {
            sb.append(item.name()).append("  ");
        }
        return sb.toString();
    }
}

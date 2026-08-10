package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.Disposable;
import com.rustorio.domain.building.BuildingImage;
import org.jspecify.annotations.Nullable;

/**
 * Рисует картинку, отданную зданием ({@code ViewableBuilding}), во весь экран поверх мира.
 * Арифметика — в {@link PageViewLayout}, здесь только текстура и отрисовка.
 *
 * <p><b>Текстура одна и живёт ровно столько, сколько открыт просмотр.</b> Страница — это
 * несколько мегабайт на видеокарте, и libGDX не освобождает их сборщиком мусора: {@link Texture}
 * пересоздаётся, только когда здание отдало ДРУГОЙ экземпляр {@link BuildingImage} (сравнение по
 * ссылке — ровно тот контракт, который обещает {@code ViewableBuilding}), и освобождается сразу,
 * как только просмотр закрыт. Иначе каждое открытие оставляло бы на GPU по копии страницы.
 *
 * <p>Проверить это в headless-сессии нельзя: {@link Texture} требует GL-контекста, окно игры
 * агентом не запускается. Поэтому порядок «освободить старую до создания новой» и освобождение в
 * {@link #dispose()} вычитаны глазами, а не прогоном.
 */
final class PageViewRenderer implements Disposable {

    private final SpriteBatch batch;
    private final ShapeRenderer shapes;
    private final BitmapFont font;

    /** Источник {@link #texture}, сравниваемый по ССЫЛКЕ — см. javadoc класса. */
    private @Nullable BuildingImage uploaded;
    private @Nullable Texture texture;

    PageViewRenderer(SpriteBatch batch, ShapeRenderer shapes, BitmapFont font) {
        this.batch = batch;
        this.shapes = shapes;
        this.font = font;
    }

    /** {@code view == null} — просмотр закрыт: текстура освобождается здесь же, а не откладывается до {@link #dispose()}. */
    void render(@Nullable PageView view, int screenWidth, int screenHeight) {
        if (view == null) {
            release();
            return;
        }
        ensureUploaded(view.image());
        Texture uploadedTexture = texture;
        if (uploadedTexture == null) {
            return;
        }
        float panelX = PageViewLayout.panelX(screenWidth);
        float panelY = PageViewLayout.panelY(screenHeight);
        float panelW = PageViewLayout.panelWidth(screenWidth);
        float panelH = PageViewLayout.panelHeight(screenHeight);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(Palette.PANEL_BG);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        BuildingImage image = view.image();
        float scale = PageViewLayout.scale(image.width(), screenWidth);
        int visible = Math.min(image.height() - view.scroll(),
                PageViewLayout.visibleSourceHeight(image.width(), screenWidth, screenHeight));
        float drawnWidth = image.width() * scale;
        float drawnHeight = visible * scale;

        batch.begin();
        batch.setColor(Color.WHITE);
        // Без flipY. Эта перегрузка уже отсчитывает srcY от ВЕРХА текстуры и кладёт верх
        // источника на верх назначения, так что картинка с «верх сверху» — а Pixmap именно такая —
        // рисуется правильной стороной сама. Живой баг: с flipY=true страница выходила вверх
        // ногами, и это было видно на первом же запуске.
        batch.draw(uploadedTexture,
                panelX, panelY + PageViewLayout.viewportHeight(screenHeight) - drawnHeight,
                drawnWidth, drawnHeight,
                0, view.scroll(), image.width(), visible,
                false, false);
        batch.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(Palette.PANEL_BORDER);
        shapes.rect(panelX, panelY, panelW, panelH);
        shapes.end();

        batch.begin();
        font.getData().setScale(HudText.FONT_SCALE);
        font.setColor(Color.WHITE);
        font.draw(batch, HudText.keepStart(view.title(), panelW - 2 * InspectionPanelLayout.TEXT_PAD),
                panelX + InspectionPanelLayout.TEXT_PAD, panelY + panelH - 6f);
        font.setColor(Palette.HINT);
        String hint = view.scroll() < PageViewLayout.maxScroll(image.width(), image.height(), screenWidth, screenHeight)
                ? "wheel to scroll  ·  Esc to close"
                : "Esc to close";
        font.draw(batch, hint, panelX + panelW - 200f, panelY + panelH - 6f);
        font.getData().setScale(1f);
        batch.end();
    }

    private void ensureUploaded(BuildingImage image) {
        if (image == uploaded && texture != null) {
            return;
        }
        release(); // старую освобождаем ДО создания новой, чтобы обе не жили на GPU разом
        Pixmap pixmap = new Pixmap(image.width(), image.height(), Pixmap.Format.RGBA8888);
        // Байты кладутся явно в порядке R,G,B,A: у Pixmap это именно байтовый порядок, а запись
        // int'ами зависела бы от порядка байт машины и на little-endian дала бы синюю страницу.
        byte[] rgba = new byte[image.width() * image.height() * 4];
        int[] argb = image.argb();
        for (int i = 0, out = 0; i < argb.length; i++) {
            int pixel = argb[i];
            rgba[out++] = (byte) (pixel >> 16);
            rgba[out++] = (byte) (pixel >> 8);
            rgba[out++] = (byte) pixel;
            rgba[out++] = (byte) (pixel >>> 24);
        }
        pixmap.getPixels().put(rgba).flip();
        texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear); // страница не пиксель-арт
        pixmap.dispose(); // пиксели скопированы в текстуру
        uploaded = image;
    }

    private void release() {
        if (texture != null) {
            texture.dispose();
            texture = null;
        }
        uploaded = null;
    }

    @Override
    public void dispose() {
        release();
    }
}

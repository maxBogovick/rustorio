package com.rustorio.game.view;

import com.rustorio.core.Tint;

import java.util.ArrayList;
import java.util.List;

/**
 * Слой поверх мира: что показать на экране сверх самих зданий — панели, подсветки клеток,
 * уведомления. Это «графический движок для логики»: {@code game} наполняет overlay ДАННЫМИ,
 * а {@code render} их рисует, ничего не зная об их происхождении.
 *
 * <p><b>Зачем он есть заранее.</b> Чтобы будущий урок-логика, дойдя до «а покажи это на
 * экране», не открывал {@code render}: нужные методы ({@link #panel}, {@link #highlight},
 * {@link #toast}) уже здесь. Добавить новый вид визуализации — расширить эту модель данных, а
 * не движок.
 *
 * <p><b>Две скорости жизни.</b> Панели и подсветки ЭФЕМЕРНЫ: их пересобирают каждый кадр
 * ({@link #clearFrame} → заново), потому что они отражают текущее состояние. Тосты ЖИВУТ во
 * времени: их добавляют по событию, а {@link #age} их старит.
 */
public final class Overlay {

    /** Сколько секунд по умолчанию живёт уведомление. */
    private static final float TOAST_SECONDS = 4f;

    private final List<HudPanel> panels = new ArrayList<>();
    private final List<TileHighlight> highlights = new ArrayList<>();
    private final List<WorldLabel> labels = new ArrayList<>();
    private final List<ConnLine> lines = new ArrayList<>();
    private final List<Toast> toasts = new ArrayList<>();

    // ── Эфемерное (пересобирается каждый кадр) ───────────────────────

    /** Убрать эфемерные элементы прошлого кадра перед сборкой нового. */
    public void clearFrame() {
        panels.clear();
        highlights.clear();
        labels.clear();
        lines.clear();
    }

    public void panel(HudPanel panel) {
        panels.add(panel);
    }

    public void highlight(int x, int y, Tint tint) {
        highlights.add(new TileHighlight(x, y, tint));
    }

    /** Плавающая метка над клеткой. */
    public void label(int x, int y, String text, Tint tint) {
        labels.add(new WorldLabel(x, y, text, tint));
    }

    /** Линия между центрами двух клеток. */
    public void line(int x1, int y1, int x2, int y2, Tint tint) {
        lines.add(new ConnLine(x1, y1, x2, y2, tint));
    }

    // ── Живущее во времени ────────────────────────────────────────────

    /** Показать уведомление на {@value #TOAST_SECONDS} секунд. */
    public void toast(String text) {
        toasts.add(new Toast(text, TOAST_SECONDS));
    }

    /** Состарить уведомления на прошедшее время кадра и убрать просроченные. */
    public void age(float dt) {
        for (Toast toast : toasts) {
            toast.age(dt);
        }
        toasts.removeIf(Toast::expired);
    }

    // ── Чтение (для render) ───────────────────────────────────────────
    public List<HudPanel> panels() {
        return panels;
    }

    public List<TileHighlight> highlights() {
        return highlights;
    }

    public List<WorldLabel> labels() {
        return labels;
    }

    public List<ConnLine> lines() {
        return lines;
    }

    public List<Toast> toasts() {
        return toasts;
    }
}

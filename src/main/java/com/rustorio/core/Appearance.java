package com.rustorio.core;

import org.jspecify.annotations.Nullable;

/**
 * Как показать здание — в игровых терминах, без единого пикселя.
 *
 * <p><b>Зачем этот тип появился.</b> Раньше слой отрисовки знал КАЖДОЕ здание в лицо:
 * {@code switch} по sealed-типу выбирал спрайт, стрелку, полоску прогресса, иконку. Это
 * значило, что новое здание нельзя добавить, не открыв графику, — {@code switch} не
 * компилировался, пока туда не впишут новую ветку. Для учебного проекта это ровно та
 * связь, которую мы хотим разорвать: студент думает про ЛОГИКУ, а не про пиксели.
 *
 * <p><b>Как теперь.</b> Здание само рассказывает о себе этим {@code record} в игровых
 * терминах («у меня стрелка туда, полоска вот настолько, на мне лежит вот такой предмет»),
 * а слой {@code render} просто ИСПОЛНЯЕТ описание. Незнакомое зданию рисуется подписанной
 * плашкой автоматически — поэтому новое здание <b>не требует ни строчки в графике</b>.
 * Красивый спрайт для него можно добавить потом, отдельной необязательной задачей.
 *
 * <p>Тип живёт в {@code core} и ссылается только на свои же {@link Direction} и {@link Item}:
 * никакого libGDX. Собирается через удобные методы — {@link #of(String)} задаёт основу, а
 * {@code arrow/progress/icon/counter/alert} добавляют детали, каждый возвращая НОВЫЙ
 * (неизменяемый) экземпляр.
 *
 * @param label    подпись на плашке, если готового спрайта у здания нет
 * @param arrow    направление стрелки-накладки; {@code null} — стрелки нет
 * @param working  цвет стрелки: работает (зелёная) против простаивает (красная)
 * @param progress полоска прогресса 0..1; {@link Float#NaN} — полоски нет
 * @param alert    тревожная рамка (например, бур стоит не на руде)
 * @param icon     предмет-иконка на здании; {@code null} — иконки нет
 * @param counter  число на здании (сколько в ящике, очков в лаборатории); отрицательное — не показывать
 */
public record Appearance(
        String label,
        @Nullable Direction arrow,
        boolean working,
        float progress,
        boolean alert,
        @Nullable Item icon,
        int counter) {

    /** Основа: одна подпись, без стрелок, полосок, иконок и счётчиков. */
    public static Appearance of(String label) {
        return new Appearance(label, null, false, Float.NaN, false, null, -1);
    }

    /** Добавить стрелку направления с признаком «работает» (задаёт её цвет). */
    public Appearance arrow(Direction direction, boolean working) {
        return new Appearance(label, direction, working, progress, alert, icon, counter);
    }

    /** Добавить полоску прогресса (0..1). */
    public Appearance progress(float fraction) {
        return new Appearance(label, arrow, working, fraction, alert, icon, counter);
    }

    /** Включить тревожную рамку. */
    public Appearance alert(boolean on) {
        return new Appearance(label, arrow, working, progress, on, icon, counter);
    }

    /** Показать на здании иконку предмета ({@code null} — не показывать). */
    public Appearance icon(@Nullable Item item) {
        return new Appearance(label, arrow, working, progress, alert, item, counter);
    }

    /** Показать на здании число ({@code >= 0} — показать, иначе скрыть). */
    public Appearance counter(int value) {
        return new Appearance(label, arrow, working, progress, alert, icon, value);
    }

    /** Есть ли полоска прогресса (не {@link Float#NaN})? */
    public boolean hasProgress() {
        return !Float.isNaN(progress);
    }

    /** Нужно ли показывать счётчик? */
    public boolean hasCounter() {
        return counter >= 0;
    }
}

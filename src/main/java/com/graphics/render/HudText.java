package com.graphics.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Как текст HUD ложится в отведённую ему ширину — измерение и три способа подгонки, общие для
 * всех панелей. Ни одного импорта libGDX: панель, которая умеет мерить текст сама, тестируется
 * без окна, а панель, которой для этого нужен {@code BitmapFont}, — нет (тот же довод, по
 * которому геометрия живёт в {@link InspectionPanelLayout}, а рисование — в {@link HudRenderer}).
 *
 * <p>Отдельный класс, а не метод в панели осмотра, появился, когда та же беда нашлась в модалке
 * настроек: длинный URL уезжал за рамку. Таблица ширин — вещь, которую нельзя иметь в двух
 * экземплярах, иначе панели начнут расходиться во мнении, что помещается на строку.
 *
 * <p><b>Любой непечатный символ здесь — пробел</b>, серии пробелов схлопываются. Причина в том,
 * как рисует libGDX: {@code BitmapFont.draw} честно отрабатывает {@code '\n'} и опускается на
 * следующую строку, тогда как панель, посчитавшая строки заранее, отвела под этот текст ровно
 * одну. Живой случай — pretty-printed тело HTTP-ответа: тридцать строк JSON рисовались одна
 * поверх другой и вылезали за нижнюю границу панели.
 */
final class HudText {

    /** Масштаб, который {@link HudRenderer} ставит шрифту для панелей. Арифметика ниже — в уже масштабированных пикселях. */
    static final float FONT_SCALE = 0.85f;

    private static final String ELLIPSIS = "...";
    /**
     * Насколько глубоко в заполненной строке должен стоять последний пробел, чтобы перенос
     * случился по нему, а не посреди слова. Раньше — значит рано: перенос по нему оставил бы
     * треть строки пустой, что на URL или JSON выглядит хуже разрезанного слова.
     */
    private static final float MIN_BREAK_FRACTION = 0.6f;

    /**
     * Ширина каждого печатного ASCII-символа от {@code ' '} (индекс 0) до {@code '~'} в
     * немасштабированных пикселях — взята как есть из {@code lsans-15.fnt}, шрифта, который
     * загружает {@code new BitmapFont()} и который {@link Renderer} отдаёт панелям.
     *
     * <p>Таблица нужна потому, что шрифт пропорциональный, а подгонка раньше делала вид, что нет:
     * она считала 56 символов на строку — примерно верно для плотной пунктуации JSON и в
     * полтора раза шире нужного для обычных слов. 56 заглавных букв — это больше 500 px при 316
     * px, которые реально есть у панели осмотра, и такие строки рисовались сквозь её рамку.
     *
     * <p>Заменят шрифт HUD — числа устареют, и подгонка станет свободнее или туже, но не
     * сломается. Перегенерировать из {@code xadvance} нового {@code .fnt}.
     */
    private static final byte[] ADVANCE_LSANS_15 = {
            4, 5, 5, 8, 8, 13, 10, 3, 5, 5, 6, 9, 4, 5, 4, 4,
            8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 4, 4, 9, 9, 9, 8,
            15, 9, 10, 11, 11, 10, 9, 11, 10, 3, 7, 10, 8, 11, 10, 12,
            10, 12, 11, 10, 9, 10, 9, 15, 9, 9, 8, 4, 4, 4, 5, 8,
            5, 8, 8, 8, 8, 8, 4, 8, 8, 3, 3, 7, 3, 13, 8, 8,
            8, 8, 5, 8, 4, 8, 7, 11, 7, 7, 8, 5, 3, 5, 9,
    };
    /** Ширина, которая назначается всему, чего нет в таблице выше, — медиана её значений: случайный не-ASCII символ не порвёт строку и не схлопнет её. */
    private static final byte DEFAULT_ADVANCE = 8;

    private HudText() {
    }

    /** Нарисованная ширина {@code text} в масштабированных пикселях. */
    static float widthOf(CharSequence text) {
        float width = 0f;
        for (int i = 0; i < text.length(); i++) {
            width += advanceOf(text.charAt(i));
        }
        return width;
    }

    /**
     * {@code text}, разложенный по строкам шириной не больше {@code rowWidth}, но не более
     * {@code maxRows} штук; остаток обрезается с многоточием.
     *
     * <p>Стоимость ограничена ВЫВОДОМ, а не вводом: разбор прекращается, как только набрано
     * {@code maxRows} строк, поэтому тело в 8 КБ стоит тех же нескольких сотен символов работы,
     * что и короткое. Это важно: подгонка выполняется на каждом кадре, пока панель открыта.
     */
    static List<String> wrap(String text, float rowWidth, int maxRows) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        float rowWidthSoFar = 0f;
        int consumed = 0;
        while (consumed < text.length() && rows.size() < maxRows) {
            char c = printable(text.charAt(consumed));
            consumed++;
            if (c == ' ' && (row.isEmpty() || row.charAt(row.length() - 1) == ' ')) {
                continue; // ни ведущего пробела в строке, ни двух подряд
            }
            float advance = advanceOf(c);
            if (rowWidthSoFar + advance <= rowWidth) {
                row.append(c);
                rowWidthSoFar += advance;
                continue;
            }
            int space = row.lastIndexOf(" ");
            String carry = "";
            if (space >= row.length() * MIN_BREAK_FRACTION) {
                rows.add(row.substring(0, space));
                carry = row.substring(space + 1);
            } else {
                rows.add(row.toString());
            }
            row.setLength(0);
            row.append(carry).append(c);
            rowWidthSoFar = widthOf(row);
        }
        if (rows.size() < maxRows && !row.isEmpty()) {
            rows.add(row.toString());
        } else if (!row.isEmpty() || hasMoreThanSpaces(text, consumed)) {
            rows.set(rows.size() - 1, ellipsize(rows.getLast(), rowWidth));
        }
        // Факт из одних пробелов всё равно занимает свою строку: панель считает свою высоту по
        // числу строк, и вернуть здесь ноль означало бы дыру под последней нарисованной.
        return rows.isEmpty() ? List.of("") : rows;
    }

    /**
     * Начало {@code text}, помещающееся в {@code width}, с многоточием на месте отрезанного
     * хвоста. Для строки, которую только читают: смысл её обычно в начале.
     */
    static String keepStart(String text, float width) {
        String flat = flatten(text);
        if (widthOf(flat) <= width) {
            return flat;
        }
        float budget = width - widthOf(ELLIPSIS);
        int end = 0;
        float taken = 0f;
        while (end < flat.length()) {
            float advance = advanceOf(flat.charAt(end));
            if (taken + advance > budget) {
                break;
            }
            taken += advance;
            end++;
        }
        return flat.substring(0, end) + ELLIPSIS;
    }

    /**
     * Хвост {@code text}, помещающийся в {@code width}, с многоточием на месте отрезанного
     * начала. Для строки, которую ПРАВЯТ: ввод идёт в конец буфера, и видеть игрок должен то,
     * что печатает, а не начало давно уехавшего URL.
     */
    static String keepEnd(String text, float width) {
        String flat = flatten(text);
        if (widthOf(flat) <= width) {
            return flat;
        }
        float budget = width - widthOf(ELLIPSIS);
        int from = flat.length();
        float taken = 0f;
        while (from > 0) {
            float advance = advanceOf(flat.charAt(from - 1));
            if (taken + advance > budget) {
                break;
            }
            taken += advance;
            from--;
        }
        return ELLIPSIS + flat.substring(from);
    }

    /** {@code row} с многоточием вместо хвоста, отрезанного ради того, чтобы само многоточие влезло в {@code width}: обрыв должен быть виден, а не проглочен молча. */
    private static String ellipsize(String row, float width) {
        float budget = width - widthOf(ELLIPSIS);
        float taken = widthOf(row);
        int end = row.length();
        while (end > 0 && taken > budget) {
            end--;
            taken -= advanceOf(row.charAt(end));
        }
        return row.substring(0, end) + ELLIPSIS;
    }

    /** {@code text} без непечатных символов и без повторных пробелов; та же строка, если менять нечего. */
    private static String flatten(String text) {
        StringBuilder flat = null;
        for (int i = 0; i < text.length(); i++) {
            char c = printable(text.charAt(i));
            boolean drop = c == ' ' && (i == 0 || printable(text.charAt(i - 1)) == ' ');
            if (flat == null && c == text.charAt(i) && !drop) {
                continue;
            }
            if (flat == null) {
                flat = new StringBuilder(text.length()).append(text, 0, i);
            }
            if (!drop) {
                flat.append(c);
            }
        }
        return flat == null ? text : flat.toString();
    }

    /**
     * Символ, который шрифт HUD способен нарисовать. Управляющий (включая DEL и C1) — пробел;
     * всё за пределами Latin-1 — знак вопроса.
     *
     * <p>Замена на {@code '?'} выглядит грубо, но альтернатива хуже: у встроенного
     * {@code lsans-15} 168 глифов в диапазоне 0..255 и ни одного дальше — ни длинного тире, ни
     * кириллицы. Символ без глифа libGDX просто не рисует, то есть страница на русском дала бы в
     * панели пустое место, неотличимое от «ответ пустой». Вопросительный знак хотя бы сообщает,
     * что текст есть, а показать его нечем. Настоящее лекарство — шрифт с нужными глифами, и это
     * отдельное решение владельца, а не правка по дороге.
     */
    private static char printable(char c) {
        if (c < ' ' || (c >= 0x7f && c <= 0x9f)) {
            return ' ';
        }
        if (c <= 0xff) {
            return c == 0xa0 ? ' ' : c; // неразрывный пробел рисуется, но переносить по нему нельзя
        }
        // Типографика сводится к ASCII-двойникам, а не к «?»: настоящее длинное тире шрифту
        // недоступно, а дефис на его месте читается как тире и не сообщает читателю ни о чём
        // постороннем. Список короткий сознательно — это те символы, которые реально приезжают
        // из веба (jsoup раскодирует &mdash; и &rsquo; в них), а не попытка таблицы Unicode.
        return switch (c) {
            case '‐', '‑', '‒', '–', '—', '―', '−' -> '-';
            case '‘', '’', '‚', '‛', '′' -> '\'';
            case '“', '”', '„', '‟', '″' -> '"';
            case '…' -> '.'; // многоточие одним символом не выразить, точка тише «?»
            case '•', '·', '⁃' -> '*';
            case ' ', ' ', ' ', ' ', ' ' -> ' ';
            case '​', '‌', '‍', '﻿' -> ' '; // нулевой ширины: рисовать нечего
            default -> '?';
        };
    }

    /** Осталось ли в {@code text} с позиции {@code from} что-нибудь кроме пробелов — хвост из пустоты многоточия не заслуживает. */
    private static boolean hasMoreThanSpaces(String text, int from) {
        for (int i = from; i < text.length(); i++) {
            if (text.charAt(i) > ' ') {
                return true;
            }
        }
        return false;
    }

    private static float advanceOf(char c) {
        byte advance = c >= ' ' && c < ' ' + ADVANCE_LSANS_15.length
                ? ADVANCE_LSANS_15[c - ' ']
                : DEFAULT_ADVANCE;
        return advance * FONT_SCALE;
    }
}

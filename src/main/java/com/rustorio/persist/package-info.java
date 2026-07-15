/**
 * Сохранение и загрузка игры снимком (паттерны Memento + DTO-граница + версия схемы).
 *
 * <p>Пакет — «хранитель» снимка: он знает про JSON (Jackson) и файлы, а домен про это НЕ
 * знает. Между ними — плоские DTO ({@link com.rustorio.persist.GameSnapshot} и соседи),
 * которые не жаль менять вместе с форматом файла. Слой лежит НАД доменом (зависит от
 * {@code game}/{@code model}/{@code core}), но домен от него не зависит — цикла нет.
 *
 * <p>Учебная нить: почему сериализуем плоский снимок, а не живые объекты домена, — см.
 * {@link com.rustorio.persist.GameSnapshot}.
 */
@NullMarked
package com.rustorio.persist;

import org.jspecify.annotations.NullMarked;

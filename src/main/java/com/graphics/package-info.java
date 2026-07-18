/**
 * Графический движок (libGDX): всё, что рисует игру на экране, принимает ввод и
 * крутит игровой цикл. Точка входа ({@link com.graphics.Main}) и корневой класс
 * игры ({@link com.graphics.RustorioGame}) живут здесь.
 *
 * <p>Слои движка: {@code render} (отрисовка) + {@code input} (ввод) +
 * {@code screen} (экраны) → app-загрузчик. Движок зависит от домена
 * {@link com.rustorio}, но домен от движка — нет: граница «политика не знает про
 * детали». Заменить движок можно, не трогая логику игры.
 */
@NullMarked
package com.graphics;

import org.jspecify.annotations.NullMarked;

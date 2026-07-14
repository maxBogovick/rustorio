/**
 * Rustorio — мини-Factorio на Java 25 + libGDX. Точка входа ({@link com.rustorio.Main})
 * и корневой класс игры ({@link com.rustorio.RustorioGame}).
 *
 * <p>Слои (зависимости смотрят вниз): {@code core → model → sim → game →
 * render/input/screen → app}. Ядро {@code core+model+sim+game} не зависит от
 * libGDX и тестируется без окна.
 */
@NullMarked
package com.rustorio;

import org.jspecify.annotations.NullMarked;

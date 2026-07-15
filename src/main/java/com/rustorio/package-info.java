/**
 * Rustorio — мини-Factorio на Java 25 + libGDX. Точка входа ({@link com.rustorio.Main})
 * и корневой класс игры ({@link com.rustorio.RustorioGame}).
 *
 * <p>Слои (зависимости смотрят вниз): {@code core → model → game →
 * render/input/screen → app}. Ядро {@code core+model+game} не зависит от
 * libGDX и тестируется без окна. По ходу курса между {@code model} и
 * {@code game} появится слой симуляции {@code sim}.
 */
@NullMarked
package com.rustorio;

import org.jspecify.annotations.NullMarked;

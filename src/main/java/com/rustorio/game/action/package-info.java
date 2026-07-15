/**
 * Действия игрока как объекты-команды (паттерн Command + Composite).
 *
 * <p>Пакет лежит в домене и НЕ знает про движок: команды выполняются и откатываются в
 * тестах без окна. Они меняют {@code model.World} и хранятся в истории отмен
 * ({@code game.ActionHistory}). Слой ввода лишь ПЕРЕВОДИТ сырые клики в эти команды —
 * так намерение игрока отделено от мутации мира.
 *
 * <p>Учебная нить: почему «поставить здание» стоило сделать объектом, а не вызовом —
 * см. {@link com.rustorio.game.action.PlayerAction}.
 */
@NullMarked
package com.rustorio.game.action;

import org.jspecify.annotations.NullMarked;

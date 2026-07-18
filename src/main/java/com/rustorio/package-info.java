/**
 * Rustorio — логика игры (мини-Factorio на Java 25). Здесь живёт домен: правила,
 * здания, рецепты, симуляция и состояние партии. Этот пакет НЕ зависит от libGDX
 * и тестируется без окна.
 *
 * <p>Слои домена (зависимости смотрят вниз): {@code core → model → sim → game}.
 * Ядро {@code core} — фундамент, зависит только от себя и JDK.
 *
 * <p>Графический движок вынесен в отдельный корень {@link com.graphics}: отрисовка,
 * ввод и экраны libGDX знают про домен, но домен про них — нет.
 */
@NullMarked
package com.rustorio;

import org.jspecify.annotations.NullMarked;

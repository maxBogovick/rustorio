package com.rustorio.persist;

import com.rustorio.core.Item;
import org.jspecify.annotations.Nullable;

/**
 * Состояние развилки: удерживаемый предмет и позиция «круга» раздачи.
 *
 * <p>{@code nextOutput} обязателен для сохранения: без него после загрузки развилка начала бы
 * раздавать с нуля, и поток на выходах перестал бы делиться поровну ровно там, где игрок
 * этого не ждёт.
 */
public record SplitterDto(@Nullable Item held, int nextOutput) {
}

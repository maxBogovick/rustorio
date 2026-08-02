package com.graphics.render;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One frame's worth of the in-game pause menu ({@code com.graphics.screen.PauseMenu} owns the
 * state machine; this is just the read-only snapshot {@link Renderer} draws) — the same split
 * {@code HudState} already uses for the rest of the HUD.
 *
 * @param textEntry the live-typed save name while naming a new slot, or {@code null} outside that
 *                   one sub-state — {@link #items}/{@link #selectedIndex} are meaningless then
 *                   ({@link PauseMenuRenderer} shows this single editable line instead of the list)
 */
public record PauseMenuView(String title, List<String> items, int selectedIndex, int hoverIndex,
        @Nullable String status, @Nullable String textEntry) {
}

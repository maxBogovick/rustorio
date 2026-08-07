package com.graphics.render;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * What {@code com.graphics.input.SettingsModal} hands {@link HudRenderer} to draw — one generic
 * shape for every {@code EditableBuilding}, not one view record per building the way {@code
 * PauseMenuView}'s {@code textEntry} was still a single hardcoded field. {@code null} (via {@code
 * HudState#settingsModal}) means no modal is open at all.
 *
 * @param fieldValues the CURRENT typed buffer for each field, same order as {@code fieldLabels} —
 *                     what's actually on screen right now, not the value the building had when the
 *                     modal opened
 * @param focusedIndex which field is receiving keystrokes right now — highlighted, and the only one
 *                      the trailing cursor ({@code "_"}) is drawn on
 */
public record SettingsModalView(String title, List<String> readOnlyLines, List<String> fieldLabels,
        List<String> fieldValues, int focusedIndex, @Nullable String hint) {
}

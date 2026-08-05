package com.graphics.screen;

import com.rustorio.mod.SkippedMod;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Строки статуса меню, которые собираются из данных, — отдельно от экранов, потому что экран без
 * окна не поднять, а строку проверить тестом можно (тот же приём, что у {@code CameraViewport}).
 */
final class MenuStatus {

    /** Больше этого числа имён в строку не влезает: она рисуется в один ряд под списком пунктов. */
    private static final int MAX_NAMES_SHOWN = 3;

    private MenuStatus() {
    }

    /**
     * Что показать игроку про моды, которые не загрузились, или {@code null}, если таких нет.
     * Называет сами моды, а не только их количество: «пропущен один мод» не говорит, какую папку
     * удалять. Причину не печатает — она бывает длиной в абзац и уже ушла в лог загрузки.
     */
    static @Nullable String skippedMods(List<SkippedMod> skipped) {
        if (skipped.isEmpty()) {
            return null;
        }
        String names = skipped.stream()
                .limit(MAX_NAMES_SHOWN)
                .map(mod -> mod.id().toString())
                .collect(Collectors.joining(", "));
        String tail = skipped.size() > MAX_NAMES_SHOWN ? " and " + (skipped.size() - MAX_NAMES_SHOWN) + " more" : "";
        String noun = skipped.size() == 1 ? "mod" : "mods";
        return skipped.size() + " " + noun + " skipped: " + names + tail + " — see the log for why.";
    }
}

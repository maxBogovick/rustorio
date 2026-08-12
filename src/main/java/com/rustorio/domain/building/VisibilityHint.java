package com.rustorio.domain.building;

import com.rustorio.domain.VisibilityContext;
import java.util.Optional;

/** Human-readable text for a locked building in the build menu. */
public final class VisibilityHint {

    private VisibilityHint() {
    }

    public static Optional<String> lockedMessage(BuildingPrototype prototype, VisibilityContext context, String locale) {
        String normalized = locale == null ? "en" : locale.toLowerCase(java.util.Locale.ROOT);
        return prototype.visibleWhen()
                .filter(rule -> !rule.satisfied(context))
                .map(rule -> prefix(normalized) + rule.describeClause(context, normalized));
    }

    private static String prefix(String locale) {
        return "ru".equals(locale) ? "Заблокировано — " : "Locked — ";
    }
}

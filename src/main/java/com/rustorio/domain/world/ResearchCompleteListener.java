package com.rustorio.domain.world;

import com.rustorio.api.content.ContentId;

/** Observer pattern: notified when {@link World#tryUnlockTech} actually spends the cost and unlocks {@code tech} — never on a refused attempt. */
@FunctionalInterface
public interface ResearchCompleteListener {
    void onResearchComplete(ContentId tech);
}

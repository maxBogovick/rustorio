package com.rustorio.domain.world;

import com.rustorio.domain.Tech;

/** Observer pattern: notified when {@link World#tryUnlockTech} actually spends the cost and unlocks {@code tech} — never on a refused attempt. */
@FunctionalInterface
public interface ResearchCompleteListener {
    void onResearchComplete(Tech tech);
}

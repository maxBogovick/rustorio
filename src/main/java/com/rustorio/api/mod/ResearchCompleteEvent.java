package com.rustorio.api.mod;

import com.rustorio.domain.Tech;

/** Published when a technology is actually unlocked (spent out of the research pool), not on a failed/refused attempt. */
public record ResearchCompleteEvent(Tech tech) {
}

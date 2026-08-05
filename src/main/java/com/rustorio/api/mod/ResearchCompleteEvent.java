package com.rustorio.api.mod;

import com.rustorio.api.content.ContentId;

/** Published when a technology is actually unlocked (spent out of the research pool), not on a failed/refused attempt. */
public record ResearchCompleteEvent(ContentId tech) {
}

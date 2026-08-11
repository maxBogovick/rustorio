package com.rustorio.api.mod;

import com.rustorio.api.content.ContentId;

/**
 * A {@link TechEffect} that only needs an id and label — enough for {@link
 * com.rustorio.domain.ResearchView#hasEffect} checks and load validation.
 */
public record MarkerTechEffect(ContentId id, String label) implements TechEffect {
}

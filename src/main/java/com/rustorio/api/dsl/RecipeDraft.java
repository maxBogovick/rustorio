package com.rustorio.api.dsl;

import com.rustorio.api.content.ContentId;

/** Fluent registration of one {@link com.rustorio.api.content.model.Recipe}. */
public interface RecipeDraft {

    RecipeDraft input(String itemPathOrId);

    RecipeDraft inputs(String... itemPathOrId);

    RecipeDraft output(String itemPathOrId);

    RecipeDraft time(int ticks);

    /** Places the recipe in the shared vanilla furnace pool. */
    RecipeDraft inFurnace();

    RecipeDraft inPress();

    RecipeDraft inAssembler();

    /** Explicit recipe-kind id (private pool or another mod's kind). */
    RecipeDraft kind(ContentId recipeKind);

    ContentId register();
}

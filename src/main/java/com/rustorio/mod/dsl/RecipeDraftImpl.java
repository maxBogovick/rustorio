package com.rustorio.mod.dsl;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.dsl.RecipeDraft;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.domain.BuildingType;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.model.Recipe;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

final class RecipeDraftImpl implements RecipeDraft {

    private final RegistrationContext context;
    private final ContentId id;
    private final List<ContentId> ingredientIds = new ArrayList<>();
    private @Nullable ContentId outputId;
    private int time = 1;
    private @Nullable ContentId kind;

    RecipeDraftImpl(RegistrationContext context, ContentId id) {
        this.context = context;
        this.id = id;
    }

    @Override
    public RecipeDraft input(String itemPathOrId) {
        ingredientIds.add(DslIds.resolve(context, itemPathOrId));
        return this;
    }

    @Override
    public RecipeDraft inputs(String... itemPathOrId) {
        for (String path : itemPathOrId) {
            input(path);
        }
        return this;
    }

    @Override
    public RecipeDraft output(String itemPathOrId) {
        this.outputId = DslIds.resolve(context, itemPathOrId);
        return this;
    }

    @Override
    public RecipeDraft time(int ticks) {
        this.time = ticks;
        return this;
    }

    @Override
    public RecipeDraft inFurnace() {
        this.kind = BuildingType.FURNACE.contentId();
        return this;
    }

    @Override
    public RecipeDraft inPress() {
        this.kind = BuildingType.PRESS.contentId();
        return this;
    }

    @Override
    public RecipeDraft inAssembler() {
        this.kind = BuildingType.ASSEMBLER.contentId();
        return this;
    }

    @Override
    public RecipeDraft kind(ContentId recipeKind) {
        this.kind = recipeKind;
        return this;
    }

    @Override
    public ContentId register() {
        if (ingredientIds.isEmpty()) {
            throw new IllegalStateException("recipe '" + id + "' needs at least one input()");
        }
        if (outputId == null) {
            throw new IllegalStateException("recipe '" + id + "' needs output()");
        }
        if (kind == null) {
            throw new IllegalStateException("recipe '" + id + "' needs inFurnace()/inPress()/inAssembler() or kind()");
        }
        List<ItemType> ingredients = ingredientIds.stream().map(context::requireItem).toList();
        ItemType output = context.requireItem(outputId);
        context.recipes().register(id, new Recipe(id, ingredients, output, time, kind));
        return id;
    }
}

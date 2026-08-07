package com.example.rustoriomod;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.BuildingCost;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Codec;
import com.rustorio.domain.building.PlacementRule;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * This mod's entry point — the class named by {@code mod.json}'s {@code entryPoint} and found by
 * {@code ServiceLoader} through {@code META-INF/services/com.rustorio.api.mod.RustorioMod}.
 *
 * <p>Both files matter and they must agree: the loader checks what {@code ServiceLoader} finds
 * against what {@code mod.json} claims, so a stale one is reported instead of silently ignored.
 *
 * <p>The engine instantiates this reflectively, so it needs a public no-argument constructor —
 * the implicit one here is fine; do not add a constructor with parameters.
 */
public final class TemplateMod implements RustorioMod {

    private static final ContentId GENERATOR_ID = ContentId.of("template:spark_generator");
    /** Declared in {@code content/items/spark.json} — JSON content loads before this jar runs. */
    private static final ContentId SPARK_ITEM_ID = ContentId.of("template:spark");

    /**
     * Turns {@link SparkGenerator.SparkState} into plain JDK types and back. That is the whole
     * persistence contract: the engine writes whatever {@link #encode} returns, and no part of this
     * mod ever touches JSON itself.
     */
    private static final Codec<SparkGenerator.SparkState> CODEC = new Codec<>() {
        @Override
        public Object encode(SparkGenerator.SparkState state) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("direction", state.direction().name());
            data.put("ticksUntilNextSpark", state.ticksUntilNextSpark());
            return data;
        }

        @Override
        public SparkGenerator.SparkState decode(Object raw, Registry<ItemType> items) {
            Map<?, ?> data = (Map<?, ?>) raw;
            return new SparkGenerator.SparkState(
                    Direction.valueOf(Codec.requireField(data, "direction")),
                    Codec.requireField(data, "ticksUntilNextSpark"));
        }
    };

    /**
     * Everything this mod adds. Called while registration is still OPEN — which is why the item is
     * read with {@link Registry#peek} rather than {@code get}: the other read methods need the
     * frozen id index, which does not exist yet, and getting this wrong does not fail loudly. The
     * mod is skipped and the only trace is a warning in the log.
     */
    @Override
    public void registerContent(RegistrationContext context) {
        ItemType spark = context.items().peek(SPARK_ITEM_ID).orElseThrow(() -> new IllegalStateException(
                "mod 'template': item " + SPARK_ITEM_ID + " is missing — it is declared in "
                        + "content/items/spark.json, which must be packaged beside this jar"));

        context.buildings().register(GENERATOR_ID, new BuildingPrototype(
                GENERATOR_ID,
                "Spark Generator",
                new BuildingCost(VanillaItems.IRON_PLATE, 2),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.MINER, // a mod ships its own art by adding a textures/ directory
                1, 1,          // footprint
                0, 1, false,   // buffer, speed multiplier, accepts speed modules
                (self, direction, factory) ->
                        new SparkGenerator(self.id(), direction, factory.items().get(SPARK_ITEM_ID)),
                (self, decodedState, factory) -> {
                    SparkGenerator.SparkState state = (SparkGenerator.SparkState) decodedState;
                    return new SparkGenerator(self.id(), state.direction(),
                            factory.items().get(SPARK_ITEM_ID), state.ticksUntilNextSpark());
                },
                CODEC));
    }
}

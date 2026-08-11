package com.example.rustoriomod;

import com.rustorio.api.mod.RegistrationContext;
import com.rustorio.api.mod.RustorioMod;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.vanilla.VanillaSprites;

/**
 * Canonical <b>L2</b> entry point: {@code ContentDsl} + {@link
 * com.rustorio.domain.building.SimpleCrafter} — no hand-written {@code BuildingPrototype}, no
 * custom {@code Codec}.
 *
 * <p>Copy this project (or run {@code ./gradlew initMod -PmodId=…} in the engine) and replace the
 * item/building ids. For a custom {@code Building} class, services, or networks see the engine's
 * in-repo L3 samples ({@code webminer}, petrochem in the modding guide) — not this template.
 *
 * <p>No {@code recipe()} here on purpose: {@code SimpleCrafter} cooks from its own input/output
 * spec, not from the recipe book. Add JSON/DSL recipes when you want a vanilla furnace/assembler
 * (or the book) to show the same conversion — that is a separate L0/L1 concern.
 */
public final class TemplateMod implements RustorioMod {

    @Override
    public void registerContent(RegistrationContext ctx) {
        ctx.content().item("spark_ore")
                .label("Spark Ore")
                .color(0xF0C040)
                .shape(ItemShape.CIRCLE)
                .register();
        ctx.content().item("spark")
                .label("Spark")
                .color(0xFFE080)
                .shape(ItemShape.SQUARE)
                .register();

        ctx.content().building("polisher")
                .label("Spark Polisher")
                .cost("rustorio:iron_plate", 4)
                .placement("needs_passable_terrain")
                .texture(VanillaSprites.ASSEMBLER)
                .simpleCrafter("spark_ore", "spark", 10)
                .register();

        ctx.content().map("spark_yard")
                .ore("spark_ore", 16, 16, 4)
                .ore("rustorio:coal", 24, 16, 3)
                .register();
    }
}

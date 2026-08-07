package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a building is FOR — the axis the build panel groups its tabs by.
 *
 * <p>The panel used to group by {@code id().namespace()}, which is who WROTE a building rather than
 * what it does: a player looking for something that moves fluid does not think "waterworks". With
 * one mod installed that distinction is invisible; with four it makes the panel unusable, and the
 * hotbar ends up carrying the whole catalogue instead — which is exactly what overflowed the screen.
 *
 * <p>Five categories, chosen by the owner. They are PUBLIC API from the moment a mod writes one in
 * its {@code content/buildings/*.json}, so they are not renameable — see {@code AGENTS.md} on names
 * that reach mods and saves.
 *
 * <p>A building that declares nothing lands in {@link #OTHER}. That is deliberate rather than an
 * error: {@code "category"} is a new optional key, and every mod written before it existed must
 * keep loading unchanged.
 *
 * <p>A mod may also declare a category id of its OWN. Nothing here rejects that — the panel shows
 * an unknown id as its own tab, labelled by the id's path. What this class holds is the vanilla
 * five and their ORDER, which is what a fixed tab order needs and what a {@code Set} could not
 * give (its iteration order is randomised per JVM run — this project keeps a rule about that).
 */
public final class VanillaCategories {

    /** Anything that takes raw material out of the ground. */
    public static final ContentId MINING = ContentId.of("rustorio:mining");
    /** Anything that moves, sorts or holds items. */
    public static final ContentId LOGISTICS = ContentId.of("rustorio:logistics");
    /** Anything that turns items into other items. */
    public static final ContentId PRODUCTION = ContentId.of("rustorio:production");
    /** Anything that moves or holds a fluid. */
    public static final ContentId FLUIDS = ContentId.of("rustorio:fluids");
    /** Anything that generates, carries or consumes electricity as its whole purpose. */
    public static final ContentId POWER = ContentId.of("rustorio:power_category");

    /** Where a building that declares no category goes — never empty in practice once any mod ships without the key. */
    public static final ContentId OTHER = ContentId.of("rustorio:other");

    /**
     * Which trait carries a prototype's category. A trait rather than a component of {@link
     * BuildingPrototype} for the reason that record's own javadoc gives: a new optional property is
     * one key and one parser, not another rung on four constructors.
     *
     * <p>{@code dataKey} is {@code "category"} — the name a modder writes in JSON, and therefore the
     * part of this that can never change.
     */
    public static final TraitKey<ContentId> CATEGORY =
            new TraitKey<>(ContentId.of("rustorio:category"), "category", ContentId.class);

    /** The vanilla categories in tab order, {@link #OTHER} last — a list, because this order is drawn on screen and must not vary between runs. */
    public static List<ContentId> all() {
        return List.of(MINING, LOGISTICS, PRODUCTION, FLUIDS, POWER, OTHER);
    }

    /**
     * Display labels for the vanilla five, by locale. Kept here beside the ids rather than in the
     * renderer: which categories exist is content, and drawing them is not.
     *
     * <p>ASCII for {@code "en"} — the HUD's bitmap font has no glyphs beyond it, which is how an
     * em-dash became a white square on screen once already.
     */
    public static String label(ContentId category, String locale) {
        Map<ContentId, String> labels = "ru".equals(locale) ? RU : EN;
        String label = labels.get(category);
        return label != null ? label : category.path();
    }

    private static final Map<ContentId, String> EN = labels(
            "Mining", "Logistics", "Production", "Fluids", "Power", "Other");
    private static final Map<ContentId, String> RU = labels(
            "Добыча", "Логистика", "Производство", "Жидкости", "Энергия", "Прочее");

    private static Map<ContentId, String> labels(String mining, String logistics, String production,
            String fluids, String power, String other) {
        Map<ContentId, String> byId = new LinkedHashMap<>();
        byId.put(MINING, mining);
        byId.put(LOGISTICS, logistics);
        byId.put(PRODUCTION, production);
        byId.put(FLUIDS, fluids);
        byId.put(POWER, power);
        byId.put(OTHER, other);
        return byId;
    }

    /** The category {@code prototype} declares, or {@link #OTHER} when it declares none. The one place that default lives. */
    public static ContentId of(BuildingPrototype prototype) {
        return prototype.traits().get(CATEGORY).orElse(OTHER);
    }

    private VanillaCategories() {
    }
}

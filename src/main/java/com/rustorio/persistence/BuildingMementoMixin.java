package com.rustorio.persistence;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.rustorio.domain.building.BuildingMemento;

/**
 * Jackson type-discriminator info for {@link BuildingMemento}, attached via {@code
 * ObjectMapper.addMixIn} instead of annotating {@code BuildingMemento} directly. The domain
 * module must not import Jackson at all (see {@code build.gradle}: "домен про JSON не знает") —
 * a mixin lets this persistence-only package teach Jackson how to (de)serialize a sealed
 * interface it doesn't own, with zero Jackson annotations anywhere in {@code
 * com.rustorio.domain.building}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.WRAPPER_OBJECT)
@JsonSubTypes({
        @JsonSubTypes.Type(value = BuildingMemento.MinerState.class, name = "MINER"),
        @JsonSubTypes.Type(value = BuildingMemento.ChestState.class, name = "CHEST"),
        @JsonSubTypes.Type(value = BuildingMemento.FurnaceState.class, name = "FURNACE"),
        @JsonSubTypes.Type(value = BuildingMemento.BeltState.class, name = "BELT"),
        @JsonSubTypes.Type(value = BuildingMemento.SplitterState.class, name = "SPLITTER"),
        @JsonSubTypes.Type(value = BuildingMemento.FilterState.class, name = "FILTER"),
        @JsonSubTypes.Type(value = BuildingMemento.InserterState.class, name = "INSERTER"),
        @JsonSubTypes.Type(value = BuildingMemento.LabState.class, name = "LAB"),
        @JsonSubTypes.Type(value = BuildingMemento.UndergroundBeltState.class, name = "UNDERGROUND_BELT"),
})
interface BuildingMementoMixin {
}

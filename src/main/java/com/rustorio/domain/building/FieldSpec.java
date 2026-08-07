package com.rustorio.domain.building;

/** One editable field a settings modal shows for an {@link EditableBuilding} — its on-screen label and character-set kind. */
public record FieldSpec(String label, FieldType type) {
}

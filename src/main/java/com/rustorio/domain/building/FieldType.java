package com.rustorio.domain.building;

/**
 * What characters {@code com.graphics.input}'s generic settings modal accepts while typing into a
 * given {@link FieldSpec} — {@code TEXT} allows the URL/field-list character set, {@code NUMBER}
 * digits only. Declared here, next to {@link EditableBuilding}, rather than in the UI layer: which
 * kind of value a field holds is a fact about the building's own data, not a rendering choice.
 */
public enum FieldType {
    TEXT,
    NUMBER
}

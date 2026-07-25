/**
 * Save/load, isolated behind {@link com.rustorio.persistence.SaveRepository} (Repository
 * pattern). This is the ONLY package allowed to import Jackson — {@code com.rustorio.domain} and
 * {@code com.rustorio.domain.building} know nothing about JSON or files; {@link
 * com.rustorio.persistence.BuildingMementoMixin} teaches Jackson how to (de)serialize the sealed
 * {@code BuildingMemento} hierarchy from the outside, via {@code ObjectMapper.addMixIn}, instead
 * of the domain module carrying Jackson annotations itself.
 */
@NullMarked
package com.rustorio.persistence;

import org.jspecify.annotations.NullMarked;

package com.rustorio.persistence;

import com.rustorio.domain.world.World;

/**
 * Repository pattern: reading and writing a {@code World}'s persisted state, behind an interface
 * the rest of the game depends on instead of a concrete file format. {@link JsonSaveRepository} is
 * the only implementation today; a cloud-save or a second save slot would be another one, with no
 * change to whoever calls {@link #save}/{@link #load}.
 */
public interface SaveRepository {

    /** Persist {@code world}'s current state. See {@link SaveResult} for how failure is reported. */
    SaveResult save(World world);

    /**
     * Load persisted state into {@code world}, replacing whatever it currently holds. On {@link
     * SaveResult.Failure} (nothing to load, or the file was unreadable), {@code world} is left
     * untouched.
     */
    SaveResult load(World world);
}

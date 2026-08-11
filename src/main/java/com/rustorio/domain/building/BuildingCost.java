package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.api.content.model.ItemType;

/**
 * What a building costs to place, from the player's inventory — one item kind and amount per
 * {@link BuildingType} (D-03, DEV_TASKS.md). Closes the loop §1 of the design audit calls out:
 * before this, {@code World.place} checked geometry only, so produced goods never fed back into
 * the player's ability to expand the factory.
 *
 * <p>The actual numbers per building live in {@link VanillaBuildings#registerAll}, as data on
 * each {@link BuildingPrototype} — not a {@code switch} here — so a new building variant is a new
 * registered value, not a new {@code case}.
 *
 * <p>Owner's balance pass, not derived from anything in the audit (which names no numbers): every
 * building a player needs to bootstrap their FIRST production line ({@code MINER}, {@code BELT},
 * {@code FURNACE}, {@code CHEST}, {@code SPLITTER}, the tunnel pair, and — importantly — {@code
 * PRESS}) costs only {@code IRON_PLATE}, obtainable from the starting stock or a furnace alone.
 * {@code PRESS} deliberately does NOT cost {@code GEAR}: {@code GEAR} is only ever produced BY a
 * press ({@code RecipeBook}'s {@code IRON_PLATE -> GEAR} recipe runs on {@code BuildingType.PRESS}),
 * so pricing a press in the very item it's needed to create would make the first press
 * unbuildable — a deadlock caught while writing this class's tests, not by inspection. {@code LAB}
 * is the one building priced in {@code GEAR}: by the time a player wants a lab, a press is already
 * assumed to be running, so gating it behind the refined good is a real, reachable milestone
 * instead of a second bootstrap trap.
 *
 * <p><b>Owner decision (X-01, DEV_TASKS.md):</b> {@code FILTER} priced the same as {@code
 * SPLITTER} (3) — it's the same size of decision the old combined building already was. {@code
 * INSERTER} priced a step above a plain {@code BELT} (1) but below a tunnel (2): mechanically it's
 * one belt tile, but it's the deliberate, explicit "restore direct insertion" building the design
 * audit's §2.1 asks for, so it should cost a LITTLE more than the transport primitive it's built
 * from, not the same.
 */
public record BuildingCost(ItemType item, int amount) {
}

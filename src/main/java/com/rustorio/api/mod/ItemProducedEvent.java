package com.rustorio.api.mod;

import com.rustorio.api.content.model.ItemType;

/**
 * Published once per single produced item — the same occasion {@code ProductionListener} already
 * reports. No quantity field: the domain's own notification is one call per one item (a batch of
 * five publishes this five times), so a count here would always read {@code 1} and mean nothing.
 */
public record ItemProducedEvent(ItemType item) {
}

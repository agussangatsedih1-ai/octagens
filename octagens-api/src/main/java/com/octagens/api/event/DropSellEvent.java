package com.octagens.api.event;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Map;

/**
 * Fired once per currency group when a player sells drops. Cancelling keeps the items.
 */
public final class DropSellEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final String currency;
    private final Map<String, Integer> items;
    private final double multiplier;
    private double total;

    public DropSellEvent(Player player, String currency, Map<String, Integer> items, double total, double multiplier) {
        this.player = player;
        this.currency = currency;
        this.items = Collections.unmodifiableMap(items);
        this.total = total;
        this.multiplier = multiplier;
    }

    /** @return the seller */
    public Player getPlayer() { return player; }

    /** @return currency id used for the payout */
    public String getCurrency() { return currency; }

    /** @return sold amount per generator type id */
    public Map<String, Integer> getItems() { return items; }

    /** @return active sell multiplier that is already included in the total */
    public double getMultiplier() { return multiplier; }

    /** @return total payout */
    public double getTotal() { return total; }

    /** Changes the payout (minimum 0). */
    public void setTotal(double total) { this.total = Math.max(0, total); }
}

package com.octagens.api.event;

import org.bukkit.entity.Player;

/** Fired before a player upgrades the maximum Magicule capacity. The price can be changed. */
public final class McuMaxUpgradeEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final double oldMax;
    private final double newMax;
    private final int levels;
    private final String currency;
    private double price;

    public McuMaxUpgradeEvent(Player player, double oldMax, double newMax, int levels, double price, String currency) {
        this.player = player;
        this.oldMax = oldMax;
        this.newMax = newMax;
        this.levels = levels;
        this.price = price;
        this.currency = currency;
    }

    /** @return the buyer */
    public Player getPlayer() { return player; }

    /** @return capacity before the upgrade */
    public double getOldMax() { return oldMax; }

    /** @return capacity after the upgrade */
    public double getNewMax() { return newMax; }

    /** @return number of upgrade levels bought */
    public int getLevels() { return levels; }

    /** @return currency id */
    public String getCurrency() { return currency; }

    /** @return price that will be charged */
    public double getPrice() { return price; }

    /** Changes the price that will be charged (minimum 0). */
    public void setPrice(double price) { this.price = Math.max(0, price); }
}

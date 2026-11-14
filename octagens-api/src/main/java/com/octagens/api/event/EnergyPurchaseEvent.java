package com.octagens.api.event;

import org.bukkit.entity.Player;

/** Fired before a player buys an energy from the energy shop (ingredients are checked separately). */
public final class EnergyPurchaseEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final String energyId;
    private final String currency;
    private double price;

    public EnergyPurchaseEvent(Player player, String energyId, double price, String currency) {
        this.player = player;
        this.energyId = energyId;
        this.price = price;
        this.currency = currency;
    }

    /** @return the buyer */
    public Player getPlayer() { return player; }

    /** @return energy type id */
    public String getEnergyId() { return energyId; }

    /** @return currency id */
    public String getCurrency() { return currency; }

    /** @return money price (0 means ingredients only) */
    public double getPrice() { return price; }

    /** Changes the money price (minimum 0). */
    public void setPrice(double price) { this.price = Math.max(0, price); }
}

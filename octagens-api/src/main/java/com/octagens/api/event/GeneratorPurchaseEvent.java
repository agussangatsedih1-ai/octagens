package com.octagens.api.event;

import org.bukkit.entity.Player;

/** Fired before a player buys a generator from the shop. */
public final class GeneratorPurchaseEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final String generatorId;
    private final String currency;
    private double price;

    public GeneratorPurchaseEvent(Player player, String generatorId, double price, String currency) {
        this.player = player;
        this.generatorId = generatorId;
        this.price = price;
        this.currency = currency;
    }

    /** @return the buyer */
    public Player getPlayer() { return player; }

    /** @return generator type id */
    public String getGeneratorId() { return generatorId; }

    /** @return currency id, e.g. {@code vault} or {@code excellenteconomy:gems} */
    public String getCurrency() { return currency; }

    /** @return price that will be charged */
    public double getPrice() { return price; }

    /** Changes the price that will be charged (minimum 0). */
    public void setPrice(double price) { this.price = Math.max(0, price); }
}

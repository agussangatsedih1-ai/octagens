package com.octagens.api.event;

import com.octagens.api.model.GeneratorInfo;
import org.bukkit.entity.Player;

/** Fired before a placed generator is upgraded to the next tier (manage GUI). Cancelling keeps the old tier. */
public final class GeneratorUpgradeEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final GeneratorInfo generator;
    private final String fromId;
    private final String toId;
    private final String currency;
    private double price;

    public GeneratorUpgradeEvent(Player player, GeneratorInfo generator, String fromId, String toId, double price, String currency) {
        this.player = player;
        this.generator = generator;
        this.fromId = fromId;
        this.toId = toId;
        this.price = price;
        this.currency = currency;
    }

    /** @return the player paying for the upgrade */
    public Player getPlayer() { return player; }

    /** @return the generator being upgraded (still of the old tier while the event runs) */
    public GeneratorInfo getGenerator() { return generator; }

    /** @return current generator type id */
    public String getFromId() { return fromId; }

    /** @return target generator type id */
    public String getToId() { return toId; }

    /** @return currency id */
    public String getCurrency() { return currency; }

    /** @return price that will be charged */
    public double getPrice() { return price; }

    /** Changes the price that will be charged (minimum 0). */
    public void setPrice(double price) { this.price = Math.max(0, price); }
}

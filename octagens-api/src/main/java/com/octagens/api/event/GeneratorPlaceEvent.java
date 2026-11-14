package com.octagens.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Fired before a player places a generator. Cancelling blocks the placement. */
public final class GeneratorPlaceEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final String generatorId;
    private final Location location;

    public GeneratorPlaceEvent(Player player, String generatorId, Location location) {
        this.player = player;
        this.generatorId = generatorId;
        this.location = location;
    }

    /** @return the placing player */
    public Player getPlayer() { return player; }

    /** @return generator type id */
    public String getGeneratorId() { return generatorId; }

    /** @return target block location */
    public Location getLocation() { return location; }
}

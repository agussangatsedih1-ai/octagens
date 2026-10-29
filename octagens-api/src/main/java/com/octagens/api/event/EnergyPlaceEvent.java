package com.octagens.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Fired before a player places an energy block. */
public final class EnergyPlaceEvent extends CancellableOctaGensEvent {
    private final Player player;
    private final String energyId;
    private final Location location;

    public EnergyPlaceEvent(Player player, String energyId, Location location) {
        this.player = player;
        this.energyId = energyId;
        this.location = location;
    }

    /** @return the placing player */
    public Player getPlayer() { return player; }

    /** @return energy type id */
    public String getEnergyId() { return energyId; }

    /** @return target block location */
    public Location getLocation() { return location; }
}

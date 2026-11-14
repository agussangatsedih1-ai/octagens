package com.octagens.api.model;

import org.bukkit.Location;

import java.util.UUID;

/** Read-only view of a placed energy block. */
public interface EnergyInfo {
    /** @return owner UUID */
    UUID getOwner();

    /** @return last known owner name (may be null) */
    String getOwnerName();

    /** @return energy type id as in energy.yml */
    String getTypeId();

    /** @return world name */
    String getWorldName();

    /** @return block X */
    int getX();

    /** @return block Y */
    int getY();

    /** @return block Z */
    int getZ();

    /** @return block location, or null if the world is not loaded */
    Location getLocation();

    /** @return output in Mcu/s */
    double getMcuPerSecond();
}

package com.octagens.plugin.model;

import com.octagens.api.model.EnergyInfo;
import com.octagens.plugin.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** An energy block placed in the world. Only touched from the main thread. */
public final class PlacedEnergy implements EnergyInfo {
    public final UUID owner;
    public String ownerName;
    public final String typeId;
    public final BlockKey key;
    public final long placedAt;
    public double mcu;

    public PlacedEnergy(UUID owner, String ownerName, String typeId, BlockKey key, long placedAt, double mcu) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.typeId = typeId;
        this.key = key;
        this.placedAt = placedAt;
        this.mcu = mcu;
    }

    @Override public UUID getOwner() { return owner; }
    @Override public String getOwnerName() { return ownerName; }
    @Override public String getTypeId() { return typeId; }
    @Override public String getWorldName() { return key.world(); }
    @Override public int getX() { return key.x(); }
    @Override public int getY() { return key.y(); }
    @Override public int getZ() { return key.z(); }
    @Override public double getMcuPerSecond() { return mcu; }

    @Override
    public Location getLocation() {
        World w = Bukkit.getWorld(key.world());
        return w == null ? null : new Location(w, key.x(), key.y(), key.z());
    }
}

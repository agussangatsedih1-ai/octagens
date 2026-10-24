package com.octagens.plugin.model;

import com.octagens.api.model.GeneratorInfo;
import com.octagens.plugin.util.BlockKey;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/** A generator placed in the world. Only touched from the main thread. */
public final class PlacedGenerator implements GeneratorInfo {
    public enum Status { ACTIVE, NO_ENERGY, NO_CAPACITY, NOT_CONNECTED, FULL, OWNER_OFFLINE, UNKNOWN }

    public final UUID owner;
    public String ownerName;
    public String typeId;
    public final BlockKey key;
    public final long placedAt;

    public double progress;
    public double lastEfficiency = -1;
    public boolean powered;
    public long holoUntil;
    public double supply;
    public double required;
    public int supplyVersion = -1;
    public Status status = Status.ACTIVE;
    public UUID holoId;
    public String holoText = "";
    public boolean holoDropMode;
    public long nextFullCheck;

    public PlacedGenerator(UUID owner, String ownerName, String typeId, BlockKey key, long placedAt) {
        this.owner = owner;
        this.ownerName = ownerName;
        this.typeId = typeId;
        this.key = key;
        this.placedAt = placedAt;
    }

    @Override public UUID getOwner() { return owner; }
    @Override public String getOwnerName() { return ownerName; }
    @Override public String getTypeId() { return typeId; }
    @Override public String getWorldName() { return key.world(); }
    @Override public int getX() { return key.x(); }
    @Override public int getY() { return key.y(); }
    @Override public int getZ() { return key.z(); }
    @Override public double getRequiredMcu() { return required; }

    @Override
    public Location getLocation() {
        World w = Bukkit.getWorld(key.world());
        return w == null ? null : new Location(w, key.x(), key.y(), key.z());
    }
}

package com.octagens.plugin.util;

import org.bukkit.Location;
import org.bukkit.block.Block;

/** Immutable block coordinate key (world name + xyz). */
public record BlockKey(String world, int x, int y, int z) {
    public static BlockKey of(Block b) {
        return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    public static BlockKey of(Location l) {
        return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public ChunkKey chunk() {
        return new ChunkKey(world, x >> 4, z >> 4);
    }
}

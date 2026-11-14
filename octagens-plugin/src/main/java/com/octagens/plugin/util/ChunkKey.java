package com.octagens.plugin.util;

import org.bukkit.Chunk;

/** Immutable chunk key (world name + chunk coordinates). */
public record ChunkKey(String world, int x, int z) {
    public static ChunkKey of(Chunk c) {
        return new ChunkKey(c.getWorld().getName(), c.getX(), c.getZ());
    }
}

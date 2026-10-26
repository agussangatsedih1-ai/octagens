package com.octagens.plugin.listener;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

/** Generators only run (and show holograms) in loaded chunks. */
public final class ChunkListener implements Listener {
    private final OctaGensPlugin plugin;

    public ChunkListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLoad(ChunkLoadEvent e) {
        plugin.generators().onChunkLoad(e.getChunk());
        plugin.energy().onChunkLoad(e.getChunk());
    }

    @EventHandler
    public void onUnload(ChunkUnloadEvent e) {
        plugin.generators().onChunkUnload(e.getChunk());
    }
}

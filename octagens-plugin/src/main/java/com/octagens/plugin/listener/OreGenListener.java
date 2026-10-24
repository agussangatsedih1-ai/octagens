package com.octagens.plugin.listener;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

/** Hooks lava/water flow for the stone and basalt generators. */
public final class OreGenListener implements Listener {
    private final OctaGensPlugin plugin;

    public OreGenListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent e) {
        plugin.ore().handle(e);
    }
}

package com.octagens.plugin.listener;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

/** Join/quit housekeeping (no per-player memory is kept after quit). */
public final class PlayerListener implements Listener {
    private final OctaGensPlugin plugin;

    public PlayerListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        plugin.weekend().onJoin(e.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        plugin.slots().clear(id);
        plugin.sell().clear(id);
        plugin.weekend().onQuit(e.getPlayer());
        plugin.energy().onQuit(id);
    }
}

package com.octagens.plugin.listener;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.gui.OctaGui;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Anti-dupe GUI handling: every click/drag touching an OctaGens GUI is cancelled. */
public final class GuiListener implements Listener {
    private final OctaGensPlugin plugin;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public GuiListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getInventory();
        if (!(top.getHolder() instanceof OctaGui gui)) return;
        e.setCancelled(true); // covers shift-click, hotbar swap, double click, offhand swap...
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != top) return;
        long now = System.currentTimeMillis();
        Long last = lastClick.get(p.getUniqueId());
        if (last != null && now - last < plugin.settings().guiClickCooldownMs) return;
        lastClick.put(p.getUniqueId(), now);
        try {
            gui.handle(e.getRawSlot(), e.getClick());
        } catch (Exception ex) {
            plugin.getLogger().warning("Error pada klik GUI " + gui.template.id + ": " + ex);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof OctaGui) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastClick.remove(e.getPlayer().getUniqueId());
    }
}

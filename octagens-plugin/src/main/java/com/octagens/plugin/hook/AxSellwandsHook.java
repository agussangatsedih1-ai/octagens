package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;

/**
 * Hook for the AxSellwands plugin (Artillex-Studios). OctaGens does not ship its own sellwand.
 * <p>
 * When a player right-clicks a container with an AxSellwands wand, this hook sells the OctaGens drops stored in
 * that container at OctaGens prices (AxSellwands cannot price PDC-tagged drops). Whatever remains is handled by
 * AxSellwands itself. A wand is recognised by its PersistentDataContainer keys (namespace "axsellwands"),
 * so no compile-time dependency on AxSellwands is needed.
 */
public final class AxSellwandsHook {
    private static final String NAMESPACE = "axsellwands";

    private final OctaGensPlugin plugin;
    private final Listener listener = new Listener() {
    };

    public AxSellwandsHook(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    /** Registers the interact listener with the priority from config (hooks.axsellwands.priority). */
    public void register() {
        EventPriority priority;
        try {
            priority = EventPriority.valueOf(plugin.settings().axPriority.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("hooks.axsellwands.priority tidak valid, memakai LOW");
            priority = EventPriority.LOW;
        }
        plugin.getServer().getPluginManager().registerEvent(PlayerInteractEvent.class, listener, priority,
                (l, ev) -> {
                    if (ev instanceof PlayerInteractEvent pie) onInteract(pie);
                }, plugin, true);
    }

    public void unregister() {
        HandlerList.unregisterAll(listener);
    }

    private void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        if (e.useInteractedBlock() == Event.Result.DENY) return;
        ItemStack item = e.getItem();
        if (!isWand(item)) return;
        Block block = e.getClickedBlock();
        if (block == null || !isAllowedContainer(block.getType())) return;
        if (!(block.getState() instanceof Container container)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("octagens.use")) return;

        double mult = plugin.settings().axReadMultiplier ? multiplier(item) : 1.0;
        java.util.UUID ownerOnly = plugin.settings().axOwnerOnly ? p.getUniqueId() : null;
        int sold = plugin.sell().sellContainer(p, container.getInventory(), mult, ownerOnly);
        if (sold > 0 && plugin.settings().cpSellwand) {
            plugin.hooks().cp().logContainerTransaction(p.getName(), block.getLocation());
        }
    }

    private boolean isAllowedContainer(org.bukkit.Material m) {
        String n = m.name();
        for (String c : plugin.settings().axContainers) {
            if (n.equals(c) || (c.equals("SHULKER_BOX") && n.endsWith("SHULKER_BOX"))) return true;
        }
        return false;
    }

    /** True when the item carries any PDC key in the AxSellwands namespace. */
    public static boolean isWand(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        for (NamespacedKey k : item.getItemMeta().getPersistentDataContainer().getKeys()) {
            if (k.getNamespace().equalsIgnoreCase(NAMESPACE)) return true;
        }
        return false;
    }

    /** Best-effort read of a "multiplier" value from the wand's PDC (1.0 when not found). */
    public static double multiplier(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 1.0;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        for (NamespacedKey k : pdc.getKeys()) {
            if (!k.getNamespace().equalsIgnoreCase(NAMESPACE) || !k.getKey().toLowerCase(Locale.ROOT).contains("multiplier")) continue;
            try {
                Double d = pdc.get(k, PersistentDataType.DOUBLE);
                if (d != null) return Math.max(0, d);
            } catch (IllegalArgumentException ignored) {
            }
            try {
                String s = pdc.get(k, PersistentDataType.STRING);
                if (s != null) return Math.max(0, Double.parseDouble(s));
            } catch (IllegalArgumentException ignored) {
            }
            try {
                Integer i = pdc.get(k, PersistentDataType.INTEGER);
                if (i != null) return Math.max(0, i);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return 1.0;
    }
}

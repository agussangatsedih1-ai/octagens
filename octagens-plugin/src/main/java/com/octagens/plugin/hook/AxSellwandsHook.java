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
        if (!isWand(item)) {
            if (plugin.settings().debug && item != null && item.hasItemMeta() && e.getClickedBlock() != null
                    && e.getClickedBlock().getState() instanceof Container) {
                plugin.getLogger().info("[AxSellwands hook] item " + item.getType() + " bukan wand (keys: "
                        + item.getItemMeta().getPersistentDataContainer().getKeys() + ")");
            }
            return;
        }
        Block block = e.getClickedBlock();
        if (block == null || !isAllowedContainer(block.getType())) return;
        if (!(block.getState() instanceof Container container)) return;
        Player p = e.getPlayer();
        if (!p.hasPermission("octagens.use")) return;

        double mult = plugin.settings().axReadMultiplier ? multiplier(item) : 1.0;
        java.util.UUID ownerOnly = plugin.settings().axOwnerOnly ? p.getUniqueId() : null;
        int sold = plugin.sell().sellContainer(p, container.getInventory(), mult, ownerOnly);
        if (plugin.settings().debug) plugin.getLogger().info("[AxSellwands hook] " + p.getName() + " menjual " + sold + " drop");
        if (sold > 0 && plugin.settings().cpSellwand) {
            plugin.hooks().cp().logContainerTransaction(p.getName(), block.getLocation());
        }
    }

    public boolean isAllowedContainer(org.bukkit.Material m) {
        String n = m.name();
        for (String c : plugin.settings().axContainers) {
            if (n.equals(c) || (c.equals("SHULKER_BOX") && n.endsWith("SHULKER_BOX"))) return true;
        }
        return false;
    }

    /**
     * True when the item looks like an AxSellwands wand: it carries a PDC key in one of the configured namespaces
     * (default "axsellwands"), or its name/lore contains one of the configured texts (wand-text-contains).
     */
    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        for (NamespacedKey k : meta.getPersistentDataContainer().getKeys()) {
            if (plugin.settings().axWandNamespaces.contains(k.getNamespace().toLowerCase(Locale.ROOT))) return true;
        }
        if (!plugin.settings().axWandText.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            if (meta.hasDisplayName()) sb.append(meta.getDisplayName()).append('\n');
            if (meta.hasLore() && meta.getLore() != null) for (String l : meta.getLore()) sb.append(l).append('\n');
            String text = com.octagens.plugin.util.Text.strip(sb.toString()).toLowerCase(Locale.ROOT);
            for (String t : plugin.settings().axWandText) {
                if (!t.isBlank() && text.contains(t)) return true;
            }
        }
        return false;
    }

    /** Human readable diagnostics for /octagens axdebug. */
    public java.util.List<String> diagnose(Player p) {
        java.util.List<String> out = new java.util.ArrayList<>();
        var s = plugin.settings();
        out.add("AxSellwands hook: priority=" + s.axPriority + ", owner-only=" + s.axOwnerOnly
                + ", read-multiplier=" + s.axReadMultiplier);
        var ax = plugin.getServer().getPluginManager().getPlugin("AxSellwands");
        out.add("Plugin AxSellwands: " + (ax == null ? "TIDAK ADA" : ax.getDescription().getVersion()));
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir()) {
            out.add("Item di tangan: kosong (pegang sellwand AxSellwands lalu jalankan lagi)");
        } else {
            out.add("Item di tangan: " + hand.getType() + " | dikenali sebagai wand: " + isWand(hand));
            if (hand.hasItemMeta()) {
                ItemMeta m = hand.getItemMeta();
                StringBuilder keys = new StringBuilder();
                for (NamespacedKey k : m.getPersistentDataContainer().getKeys()) keys.append(k).append(' ');
                out.add("Key PDC pada item: " + (keys.length() == 0 ? "(tidak ada)" : keys.toString().trim()));
                out.add("Multiplier terbaca: " + multiplier(hand));
                if (m.hasDisplayName()) out.add("Nama item: " + com.octagens.plugin.util.Text.strip(m.getDisplayName()));
            }
        }
        Block target = p.getTargetBlockExact(6);
        if (target == null) {
            out.add("Arahkan crosshair ke chest/barrel (maks 6 block) untuk memeriksa isinya.");
            return out;
        }
        out.add("Block yang dilihat: " + target.getType() + " | container diizinkan: " + isAllowedContainer(target.getType()));
        if (target.getState() instanceof Container c) {
            int drops = 0, owned = 0, untagged = 0, others = 0;
            for (ItemStack it : c.getInventory().getContents()) {
                if (it == null || plugin.items().dropId(it) == null) continue;
                drops += it.getAmount();
                String o = plugin.items().dropOwner(it);
                if (o == null) untagged += it.getAmount();
                else if (o.equals(p.getUniqueId().toString())) owned += it.getAmount();
                else others += it.getAmount();
            }
            out.add("Drop OctaGens di container: total=" + drops + ", milikmu=" + owned + ", tanpa tag pemilik=" + untagged
                    + ", milik pemain lain=" + others);
            if (s.axOwnerOnly) out.add("owner-only AKTIF: hanya drop 'milikmu' yang dijual oleh hook.");
        }
        return out;
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

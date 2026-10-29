package com.octagens.plugin.listener;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.SellwandType;
import com.octagens.plugin.util.Fmt;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * OctaGens Sellwand: right-click a CHEST (or another allowed container) to sell the OctaGens drops stored inside it.
 * It does not sell items lying on top of a generator. Protection plugins are respected.
 */
public final class SellwandListener implements Listener {
    private final OctaGensPlugin plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public SellwandListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (plugin.items().wandId(e.getItemInHand()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        lastUse.remove(e.getPlayer().getUniqueId());
    }

    // HIGHEST: runs after protection plugins, so useInteractedBlock() already reflects a denied access.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_BLOCK && a != Action.RIGHT_CLICK_AIR) return;
        ItemStack item = e.getItem();
        String wandId = plugin.items().wandId(item);
        if (wandId == null) return;
        boolean denied = e.useInteractedBlock() == Event.Result.DENY;
        e.setCancelled(true); // a sellwand is never used as a normal item and never opens the container
        if (a != Action.RIGHT_CLICK_BLOCK) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Player p = e.getPlayer();

        BlockState state = block.getState();
        if (!(state instanceof Container container) || !plugin.registry().isWandContainer(block.getType())) {
            plugin.messages().send(p, "sellwand-invalid-container");
            return;
        }
        if (denied) {
            plugin.messages().send(p, "sellwand-no-access");
            return;
        }
        if (!p.hasPermission("octagens.sellwand.use")) {
            plugin.messages().send(p, "no-permission");
            return;
        }
        SellwandType type = plugin.registry().wand(wandId);
        if (type == null) {
            plugin.messages().send(p, "sellwand-unknown");
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUse.get(p.getUniqueId());
        if (last != null && now - last < type.cooldownMs) {
            plugin.messages().send(p, "sellwand-cooldown", "seconds", Fmt.number((type.cooldownMs - (now - last)) / 1000.0));
            return;
        }
        lastUse.put(p.getUniqueId(), now);

        double mult = plugin.items().wandMultiplier(item);
        int sold = plugin.sell().sellContainer(p, container.getInventory(), mult);
        if (sold <= 0) {
            plugin.messages().send(p, "sellwand-empty");
            return;
        }
        if (plugin.settings().cpSellwand) plugin.hooks().cp().logContainerTransaction(p.getName(), block.getLocation());
        String ok = plugin.registry().wandSuccessSound();
        if (!ok.isBlank()) p.playSound(p.getLocation(), ok, 0.8f, 1.3f);

        int uses = plugin.items().wandUses(item);
        if (uses > 0) {
            uses--;
            if (uses <= 0) {
                p.getInventory().setItemInMainHand(null);
                String br = plugin.registry().wandBrokenSound();
                if (!br.isBlank()) p.playSound(p.getLocation(), br, 0.8f, 1.0f);
                plugin.messages().send(p, "sellwand-broken");
            } else {
                ItemStack hand = p.getInventory().getItemInMainHand();
                plugin.items().refreshWand(hand, type, uses, mult);
                p.getInventory().setItemInMainHand(hand);
                plugin.messages().send(p, "sellwand-uses", "uses", String.valueOf(uses));
            }
        }
    }
}

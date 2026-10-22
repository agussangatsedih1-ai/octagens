package com.octagens.plugin.listener;

import com.octagens.api.event.EnergyPlaceEvent;
import com.octagens.api.event.GeneratorPlaceEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedEnergy;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.manager.SlotManager;
import com.octagens.plugin.util.Fmt;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/** Placement, breaking and interaction of generator and energy blocks. */
public final class GeneratorListener implements Listener {
    private final OctaGensPlugin plugin;

    public GeneratorListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    private String limitText(int v) {
        return v == SlotManager.UNLIMITED ? plugin.messages().get("placeholder-unlimited") : String.valueOf(v);
    }

    // ---------------------------------------------------------------- place

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlaceCheck(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        String gid = plugin.items().generatorId(hand);
        if (gid != null) {
            checkGenerator(e, gid);
            return;
        }
        String eid = plugin.items().energyId(hand);
        if (eid != null) checkEnergy(e, eid);
    }

    private boolean basicChecks(BlockPlaceEvent e, Player p) {
        if (!p.hasPermission("octagens.use")) {
            plugin.messages().send(p, "no-permission");
            return false;
        }
        if (plugin.settings().blacklistedWorlds.contains(p.getWorld().getName().toLowerCase())) {
            plugin.messages().send(p, "place-world-blocked");
            return false;
        }
        return true;
    }

    private void checkGenerator(BlockPlaceEvent e, String id) {
        Player p = e.getPlayer();
        GeneratorType t = plugin.registry().generator(id);
        if (t == null) {
            e.setCancelled(true);
            plugin.messages().send(p, "generator-unknown-type");
            return;
        }
        if (!basicChecks(e, p)) {
            e.setCancelled(true);
            return;
        }
        int limit = plugin.slots().generatorLimit(p);
        if (plugin.generators().count(p.getUniqueId()) >= limit) {
            e.setCancelled(true);
            plugin.messages().send(p, "generator-slot-full", "max", limitText(limit));
            return;
        }
        if (plugin.settings().requireFreeSpace && !e.getBlock().getRelative(BlockFace.UP).isPassable()) {
            e.setCancelled(true);
            plugin.messages().send(p, "generator-no-space");
            return;
        }
        GeneratorPlaceEvent ev = new GeneratorPlaceEvent(p, id, e.getBlock().getLocation());
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) e.setCancelled(true);
    }

    private void checkEnergy(BlockPlaceEvent e, String id) {
        Player p = e.getPlayer();
        EnergyType t = plugin.registry().energy(id);
        if (t == null) {
            e.setCancelled(true);
            plugin.messages().send(p, "energy-unknown-type");
            return;
        }
        if (!basicChecks(e, p)) {
            e.setCancelled(true);
            return;
        }
        int limit = plugin.slots().energyLimit(p);
        if (plugin.energy().count(p.getUniqueId()) >= limit) {
            e.setCancelled(true);
            plugin.messages().send(p, "energy-slot-full", "max", limitText(limit));
            return;
        }
        EnergyPlaceEvent ev = new EnergyPlaceEvent(p, id, e.getBlock().getLocation());
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlaceCommit(BlockPlaceEvent e) {
        ItemStack hand = e.getItemInHand();
        Player p = e.getPlayer();
        String gid = plugin.items().generatorId(hand);
        if (gid != null) {
            GeneratorType t = plugin.registry().generator(gid);
            if (t == null) return;
            plugin.generators().place(p, e.getBlock(), t);
            plugin.messages().send(p, "generator-placed", "name", t.displayName);
            return;
        }
        String eid = plugin.items().energyId(hand);
        if (eid != null) {
            EnergyType t = plugin.registry().energy(eid);
            if (t == null) return;
            plugin.energy().place(p, e.getBlock(), t);
            plugin.messages().send(p, "energy-placed", "name", t.displayName, "mcu", Fmt.number(t.mcuPerSecond));
        }
    }

    // ---------------------------------------------------------------- break

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        Player p = e.getPlayer();
        PlacedGenerator g = plugin.generators().get(b);
        if (g != null) {
            e.setCancelled(true);
            if (!plugin.canEdit(p, g.owner)) {
                plugin.messages().send(p, "break-denied", "owner", g.ownerName == null ? "?" : g.ownerName);
                return;
            }
            plugin.generators().pickup(p, g, true);
            return;
        }
        PlacedEnergy en = plugin.energy().get(b);
        if (en != null) {
            e.setCancelled(true);
            if (!plugin.canEdit(p, en.owner)) {
                plugin.messages().send(p, "break-denied", "owner", en.ownerName == null ? "?" : en.ownerName);
                return;
            }
            plugin.energy().pickup(p, en);
        }
    }

    // ---------------------------------------------------------------- interact

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block b = e.getClickedBlock();
        if (b == null) return;
        PlacedGenerator g = plugin.generators().get(b);
        PlacedEnergy en = g == null ? plugin.energy().get(b) : null;
        if (g == null && en == null) return;

        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        boolean handEmpty = hand.getType().isAir();
        java.util.UUID owner = g != null ? g.owner : en.owner;

        if (p.isSneaking()) {
            if (plugin.settings().pickupRequireEmptyHand && !handEmpty) return;
            e.setCancelled(true);
            if (!plugin.canEdit(p, owner)) {
                plugin.messages().send(p, "edit-denied");
                return;
            }
            if (g != null) plugin.generators().pickup(p, g, false);
            else plugin.energy().pickup(p, en);
            return;
        }
        if (!handEmpty && hand.getType().isBlock()) return; // allow building next to it
        if (g != null && plugin.settings().manageGui) {
            e.setCancelled(true);
            if (!plugin.canEdit(p, g.owner)) {
                plugin.messages().send(p, "edit-denied");
                return;
            }
            plugin.guis().openManage(p, g);
        } else if (en != null && handEmpty) {
            EnergyType t = plugin.registry().energy(en.typeId);
            plugin.messages().send(p, "energy-info", "name", t == null ? en.typeId : t.displayName,
                    "mcu", Fmt.number(en.mcu), "owner", en.ownerName == null ? "?" : en.ownerName);
        }
    }
}

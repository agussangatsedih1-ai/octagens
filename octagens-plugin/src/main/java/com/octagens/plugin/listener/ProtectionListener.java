package com.octagens.plugin.listener;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;

import java.util.List;

/** Anti-exploit: pistons, explosions, dispensers, fluids, hoppers, entity block changes. */
public final class ProtectionListener implements Listener {
    private final OctaGensPlugin plugin;

    public ProtectionListener(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean any(List<Block> blocks) {
        for (Block b : blocks) {
            if (plugin.isOctaBlock(b)) return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (plugin.settings().protPiston && any(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (plugin.settings().protPiston && any(e.getBlocks())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        if (plugin.settings().protExplosion) e.blockList().removeIf(plugin::isOctaBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        if (plugin.settings().protExplosion) e.blockList().removeIf(plugin::isOctaBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (!plugin.settings().protDispenser) return;
        if (plugin.items().generatorId(e.getItem()) != null || plugin.items().energyId(e.getItem()) != null) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFluid(BlockFromToEvent e) {
        if (plugin.settings().protFluid && plugin.isOctaBlock(e.getToBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityChange(EntityChangeBlockEvent e) {
        if (plugin.isOctaBlock(e.getBlock())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHopperPickup(InventoryPickupItemEvent e) {
        if (plugin.settings().allowHopperPickup) return;
        if (plugin.items().dropId(e.getItem().getItemStack()) != null) e.setCancelled(true);
    }
}

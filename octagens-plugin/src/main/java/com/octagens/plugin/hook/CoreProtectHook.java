package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** CoreProtect logging. Every call is wrapped so a CoreProtect error never breaks gameplay. */
public final class CoreProtectHook {
    private final OctaGensPlugin plugin;
    private CoreProtectAPI api;

    public CoreProtectHook(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    /** @return true when CoreProtect (API v10+) is usable */
    public boolean init() {
        try {
            Plugin p = Bukkit.getPluginManager().getPlugin("CoreProtect");
            if (!(p instanceof CoreProtect cp)) return false;
            CoreProtectAPI a = cp.getAPI();
            if (a == null || !a.isEnabled() || a.APIVersion() < 10) return false;
            api = a;
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    public void logPlacement(String who, Block b) {
        if (api == null) return;
        try {
            api.logPlacement(who, b.getLocation(), b.getType(), b.getBlockData());
        } catch (Throwable t) {
            debug(t);
        }
    }

    public void logPlacement(String who, Location loc, Material type, BlockData data) {
        if (api == null) return;
        try {
            api.logPlacement(who, loc, type, data);
        } catch (Throwable t) {
            debug(t);
        }
    }

    /** Call BEFORE the block is changed. */
    public void logRemoval(String who, Block b) {
        if (api == null) return;
        try {
            api.logRemoval(who, b.getLocation(), b.getType(), b.getBlockData());
        } catch (Throwable t) {
            debug(t);
        }
    }

    /** Logs a transaction as a pseudo-command line in the player's CoreProtect history. */
    public void logTransaction(Player p, String text) {
        if (api == null) return;
        try {
            api.logCommand(p, text);
        } catch (Throwable t) {
            debug(t);
        }
    }

    private void debug(Throwable t) {
        if (plugin.settings().debug) plugin.getLogger().warning("CoreProtect error: " + t);
    }
}

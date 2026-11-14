package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyPool;
import com.octagens.plugin.model.PlacedEnergy;
import com.octagens.plugin.model.PlacedGenerator;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Human-readable per-player snapshot. Live Magicule and block records remain canonical in the SQL database. */
public final class PlayerDataManager {
    private final OctaGensPlugin plugin;
    private BukkitTask task;

    public PlayerDataManager(OctaGensPlugin plugin) { this.plugin = plugin; }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) save(p);
        }, 6000L, 6000L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void save(Player player) {
        if (player == null || plugin.energy() == null || plugin.generators() == null) return;
        UUID id = player.getUniqueId();
        EnergyPool pool = plugin.energy().pool(id);
        YamlConfiguration y = new YamlConfiguration();
        y.set("schema", 1);
        y.set("uuid", id.toString());
        y.set("name", player.getName());
        y.set("last-seen", System.currentTimeMillis());
        y.set("magicule.current", pool.current);
        y.set("magicule.max", pool.max);
        y.set("magicule.level", plugin.energy().level(pool));
        y.set("magicule.regen-per-second", plugin.energy().regenRate(id));
        y.set("slots.generators", plugin.generators().count(id));
        y.set("slots.collectors", plugin.energy().count(id));
        List<String> generators = new ArrayList<>();
        for (PlacedGenerator g : plugin.generators().list(id)) generators.add(location(g.key.world(), g.key.x(), g.key.y(), g.key.z(), g.typeId));
        List<String> collectors = new ArrayList<>();
        for (PlacedEnergy e : plugin.energy().list(id)) collectors.add(location(e.key.world(), e.key.x(), e.key.y(), e.key.z(), e.typeId));
        y.set("placed.generators", generators);
        y.set("placed.collectors", collectors);
        if (plugin.missions() != null) y.set("daily-missions", plugin.missions().snapshot(id));
        if (plugin.boosters() != null) y.set("booster-claims", plugin.boosters().claimSnapshot(id));
        File file = file(id);
        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) return;
        try { y.save(file); }
        catch (IOException ex) { plugin.getLogger().warning("Gagal menyimpan player data " + player.getName() + ": " + ex.getMessage()); }
    }

    public YamlConfiguration load(UUID id) {
        File f = file(id);
        return f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
    }

    public File file(UUID id) { return new File(plugin.getDataFolder(), "player-data/" + id + ".yml"); }

    private String location(String world, int x, int y, int z, String type) {
        return world + "," + x + "," + y + "," + z + "," + type;
    }
}

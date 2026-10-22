package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** TextDisplay holograms above generators (non-persistent, recreated when the chunk loads). */
public final class HologramManager {
    private final OctaGensPlugin plugin;
    private BukkitTask task;

    public HologramManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.settings().holoEnabled) return;
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (PlacedGenerator g : plugin.generators().activeSnapshot()) {
                ensure(g);
                update(g);
            }
        }, 20L, plugin.settings().holoInterval);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    public void ensureLater(List<PlacedGenerator> gens) {
        if (!plugin.settings().holoEnabled || gens.isEmpty()) return;
        List<PlacedGenerator> copy = new ArrayList<>(gens);
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (PlacedGenerator g : copy) {
                if (plugin.generators().isActive(g)) ensure(g);
            }
        });
    }

    public void ensure(PlacedGenerator g) {
        Settings s = plugin.settings();
        if (!s.holoEnabled) return;
        World w = Bukkit.getWorld(g.key.world());
        if (w == null || !w.isChunkLoaded(g.key.x() >> 4, g.key.z() >> 4)) return;
        Entity existing = g.holoId == null ? null : Bukkit.getEntity(g.holoId);
        if (existing != null && existing.isValid()) return;
        Location loc = new Location(w, g.key.x() + 0.5, g.key.y() + s.holoHeight, g.key.z() + 0.5);
        String text = format(g);
        TextDisplay td = w.spawn(loc, TextDisplay.class, d -> {
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(false);
            d.setDefaultBackground(false);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setViewRange(s.holoViewRange);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setText(text);
        });
        g.holoId = td.getUniqueId();
        g.holoText = text;
    }

    public void update(PlacedGenerator g) {
        if (g.holoId == null) return;
        Entity e = Bukkit.getEntity(g.holoId);
        if (!(e instanceof TextDisplay td) || !e.isValid()) return;
        String text = format(g);
        if (!text.equals(g.holoText)) {
            td.setText(text);
            g.holoText = text;
        }
    }

    public void remove(PlacedGenerator g) {
        if (g.holoId != null) {
            Entity e = Bukkit.getEntity(g.holoId);
            if (e != null) e.remove();
        }
        g.holoId = null;
        g.holoText = "";
    }

    public void removeAll() {
        for (PlacedGenerator g : plugin.generators().all()) remove(g);
    }

    public void reloadAll() {
        removeAll();
        start();
        for (PlacedGenerator g : plugin.generators().activeSnapshot()) ensure(g);
    }

    private String format(PlacedGenerator g) {
        GeneratorType t = plugin.registry().generator(g.typeId);
        Map<String, String> r = new HashMap<>();
        double eff = 1;
        if (t != null) {
            eff = plugin.energy().efficiency(g, t);
            r.putAll(plugin.items().generatorRepl(t));
        } else {
            r.put("name", g.typeId);
        }
        r.put("owner", g.ownerName == null ? "?" : g.ownerName);
        r.put("supply", Fmt.number(g.supply));
        r.put("required", Fmt.number(g.required));
        r.put("efficiency", Fmt.percent(plugin.settings().energyRequired ? eff : 1.0));
        r.put("status", plugin.settings().holoStatus.getOrDefault(g.status.name(), g.status.name()));
        List<String> lines = new ArrayList<>();
        for (String l : plugin.settings().holoLines) lines.add(Text.color(Text.replace(l, r)));
        return String.join("\n", lines);
    }
}

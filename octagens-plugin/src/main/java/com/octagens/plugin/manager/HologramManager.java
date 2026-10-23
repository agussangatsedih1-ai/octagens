package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.model.EnergyPool;
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
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Holograms are shown only briefly (placement, interaction, drops near a player) and then removed, so a
 * server with thousands of generators never keeps thousands of display entities alive.
 */
public final class HologramManager {
    private static final int PERIOD = 5;

    private final OctaGensPlugin plugin;
    private final Set<PlacedGenerator> visible = new HashSet<>();
    private final Map<PlacedGenerator, Long> nextDropShow = new HashMap<>();
    private BukkitTask task;
    private long clock;

    public HologramManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void tick() {
        clock += PERIOD;
        if (visible.isEmpty()) return;
        for (PlacedGenerator g : new ArrayList<>(visible)) {
            if (clock >= g.holoUntil) {
                remove(g);
            } else {
                update(g);
            }
        }
    }

    /** Shows (or extends) the hologram for the configured duration. */
    public void show(PlacedGenerator g) {
        Settings s = plugin.settings();
        if (!s.holoEnabled) return;
        World w = Bukkit.getWorld(g.key.world());
        if (w == null || !w.isChunkLoaded(g.key.x() >> 4, g.key.z() >> 4)) return;
        g.holoUntil = clock + s.holoDuration;
        Entity existing = g.holoId == null ? null : Bukkit.getEntity(g.holoId);
        if (existing != null && existing.isValid()) {
            visible.add(g);
            update(g);
            return;
        }
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
        visible.add(g);
    }

    /** Called after a drop: shown only if a player is nearby, and at most once per cooldown. */
    public void showOnDrop(PlacedGenerator g, World w, long unused) {
        Settings s = plugin.settings();
        if (!s.holoEnabled) return;
        Long next = nextDropShow.get(g);
        if (next != null && clock < next && !visible.contains(g)) return;
        double r = s.holoNearbyRadius;
        Location c = new Location(w, g.key.x() + 0.5, g.key.y() + 1.0, g.key.z() + 0.5);
        if (w.getNearbyEntities(c, r, r, r, e -> e instanceof Player).isEmpty()) return;
        nextDropShow.put(g, clock + s.holoDropCooldown);
        show(g);
    }

    private void update(PlacedGenerator g) {
        if (g.holoId == null) return;
        Entity e = Bukkit.getEntity(g.holoId);
        if (!(e instanceof TextDisplay td) || !e.isValid()) {
            visible.remove(g);
            g.holoId = null;
            return;
        }
        String text = format(g);
        if (!text.equals(g.holoText)) {
            td.setText(text);
            g.holoText = text;
        }
    }

    public void remove(PlacedGenerator g) {
        visible.remove(g);
        nextDropShow.remove(g);
        if (g.holoId != null) {
            Entity e = Bukkit.getEntity(g.holoId);
            if (e != null) e.remove();
        }
        g.holoId = null;
        g.holoText = "";
    }

    public void removeAll() {
        for (PlacedGenerator g : new ArrayList<>(visible)) remove(g);
        visible.clear();
        nextDropShow.clear();
    }

    public void reloadAll() {
        removeAll();
        start();
    }

    private String format(PlacedGenerator g) {
        GeneratorType t = plugin.registry().generator(g.typeId);
        Map<String, String> r = new HashMap<>();
        EnergyPool pool = plugin.energy().pool(g.owner);
        double eff = 1;
        if (t != null) {
            eff = plugin.energy().efficiency(g, t);
            r.putAll(plugin.items().generatorRepl(t));
        } else {
            r.put("name", g.typeId);
        }
        r.put("owner", g.ownerName == null ? "?" : g.ownerName);
        r.put("pool", Fmt.number(Math.floor(pool.current)));
        r.put("pool_max", Fmt.number(pool.max));
        r.put("regen", Fmt.number(plugin.energy().regenRate(g.owner)));
        r.put("required", Fmt.number(g.required));
        r.put("efficiency", Fmt.percent(eff));
        r.put("status", plugin.settings().holoStatus.getOrDefault(g.status.name(), g.status.name()));
        List<String> lines = new ArrayList<>();
        for (String l : plugin.settings().holoLines) lines.add(Text.color(Text.replace(l, r)));
        return String.join("\n", lines);
    }
}

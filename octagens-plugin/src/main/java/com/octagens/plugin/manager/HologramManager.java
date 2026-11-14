package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.model.EnergyPool;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedEnergy;
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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Holograms are temporary (removed after a few seconds) so servers with thousands of generators never keep
 * thousands of display entities alive:
 * <ul>
 *   <li>generator hologram: info on placement/click, and just "Drop!" when it drops items;</li>
 *   <li>Magicule Power Collector hologram: "+X Mcu" pulse every few seconds while it regenerates.</li>
 * </ul>
 */
public final class HologramManager {
    private static final int PERIOD = 5;

    private record EnergyHolo(UUID id, long until) {
    }

    private final OctaGensPlugin plugin;
    private final Set<PlacedGenerator> visible = new HashSet<>();
    private final Map<PlacedGenerator, Long> nextDropShow = new HashMap<>();
    private final Map<PlacedEnergy, EnergyHolo> energyVisible = new HashMap<>();
    private BukkitTask task;
    private long clock;
    private long nextEnergyPulse;

    public HologramManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        nextEnergyPulse = clock + plugin.settings().holoEnergyInterval;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD, PERIOD);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void tick() {
        clock += PERIOD;
        if (!visible.isEmpty()) {
            for (PlacedGenerator g : new ArrayList<>(visible)) {
                if (clock >= g.holoUntil) remove(g);
                else update(g);
            }
        }
        if (!energyVisible.isEmpty()) {
            Iterator<Map.Entry<PlacedEnergy, EnergyHolo>> it = energyVisible.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<PlacedEnergy, EnergyHolo> en = it.next();
                Entity ent = Bukkit.getEntity(en.getValue().id());
                if (clock >= en.getValue().until() || ent == null || !ent.isValid()) {
                    if (ent != null) ent.remove();
                    it.remove();
                }
            }
        }
        Settings s = plugin.settings();
        if (s.holoEnabled && s.holoEnergyEnabled && clock >= nextEnergyPulse) {
            nextEnergyPulse = clock + s.holoEnergyInterval;
            energyPulse();
        }
    }

    private TextDisplay spawn(Location loc, String text) {
        Settings s = plugin.settings();
        return loc.getWorld().spawn(loc, TextDisplay.class, d -> {
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
    }

    // ------------------------------------------------------------------ generator holograms

    /** Info hologram (placement / interaction). */
    public void show(PlacedGenerator g) {
        show(g, plugin.settings().holoDuration, false);
    }

    private void show(PlacedGenerator g, int duration, boolean dropMode) {
        Settings s = plugin.settings();
        if (!s.holoEnabled) return;
        World w = Bukkit.getWorld(g.key.world());
        if (w == null || !w.isChunkLoaded(g.key.x() >> 4, g.key.z() >> 4)) return;
        g.holoUntil = clock + duration;
        g.holoDropMode = dropMode;
        Entity existing = g.holoId == null ? null : Bukkit.getEntity(g.holoId);
        if (existing != null && existing.isValid()) {
            visible.add(g);
            update(g);
            return;
        }
        Location loc = new Location(w, g.key.x() + 0.5, g.key.y() + s.holoHeight, g.key.z() + 0.5);
        String text = format(g);
        TextDisplay td = spawn(loc, text);
        g.holoId = td.getUniqueId();
        g.holoText = text;
        visible.add(g);
    }

    /** Called after a drop: shows only "Drop!" if a player is nearby, at most once per cooldown. */
    public void showOnDrop(PlacedGenerator g, World w, long unused) {
        Settings s = plugin.settings();
        if (!s.holoEnabled) return;
        Long next = nextDropShow.get(g);
        if (next != null && clock < next) return;
        double r = s.holoNearbyRadius;
        Location c = new Location(w, g.key.x() + 0.5, g.key.y() + 1.0, g.key.z() + 0.5);
        if (w.getNearbyEntities(c, r, r, r, e -> e instanceof Player).isEmpty()) return;
        nextDropShow.put(g, clock + s.holoDropCooldown);
        show(g, s.holoDropDuration, true);
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
        g.holoDropMode = false;
    }

    // ------------------------------------------------------------------ energy holograms

    private void energyPulse() {
        Settings s = plugin.settings();
        List<Location> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getLocation());
        if (players.isEmpty()) return;
        double r2 = s.holoEnergyRadius * s.holoEnergyRadius;
        for (PlacedEnergy e : plugin.energy().all()) {
            if (energyVisible.size() >= s.holoEnergyMaxVisible) return;
            if (e.mcu <= 0 || energyVisible.containsKey(e)) continue;
            World w = Bukkit.getWorld(e.key.world());
            if (w == null || !w.isChunkLoaded(e.key.x() >> 4, e.key.z() >> 4)) continue;
            EnergyPool pool = plugin.energy().pool(e.owner);
            if (pool.current >= pool.max - 0.01) continue; // pool full: nothing is being regenerated
            boolean near = false;
            for (Location pl : players) {
                if (pl.getWorld() != w) continue;
                double dx = pl.getX() - e.key.x();
                double dy = pl.getY() - e.key.y();
                double dz = pl.getZ() - e.key.z();
                if (dx * dx + dy * dy + dz * dz <= r2) {
                    near = true;
                    break;
                }
            }
            if (!near) continue;
            double gain = plugin.energy().effectiveRate(e); // Mcu/s provided by this collector
            String text = Text.color(Text.replace(plugin.messages().raw("holo-energy"), Map.of("gain", Fmt.number(gain), "mcu", Fmt.number(gain))));
            Location loc = new Location(w, e.key.x() + 0.5, e.key.y() + s.holoEnergyHeight, e.key.z() + 0.5);
            TextDisplay td = spawn(loc, text);
            energyVisible.put(e, new EnergyHolo(td.getUniqueId(), clock + s.holoEnergyDuration));
        }
    }

    public void removeEnergy(PlacedEnergy e) {
        EnergyHolo h = energyVisible.remove(e);
        if (h != null) {
            Entity ent = Bukkit.getEntity(h.id());
            if (ent != null) ent.remove();
        }
    }

    // ------------------------------------------------------------------ lifecycle

    public void removeAll() {
        for (PlacedGenerator g : new ArrayList<>(visible)) remove(g);
        visible.clear();
        nextDropShow.clear();
        for (PlacedEnergy e : new ArrayList<>(energyVisible.keySet())) removeEnergy(e);
        energyVisible.clear();
    }

    public void reloadAll() {
        removeAll();
        start();
    }

    private String format(PlacedGenerator g) {
        if (g.holoDropMode) return Text.color(plugin.messages().raw("holo-drop"));
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
        r.put("status", plugin.messages().raw("holo-status-" + g.status.name().toLowerCase()));
        List<String> lines = new ArrayList<>();
        for (String l : plugin.messages().rawList("holo-lines")) lines.add(Text.color(Text.replace(plugin.messages().resolveLang(l), r)));
        return String.join("\n", lines);
    }
}

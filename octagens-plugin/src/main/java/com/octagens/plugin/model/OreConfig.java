package com.octagens.plugin.model;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Parsed oregen.yml. */
public final class OreConfig {
    public static final class Gen {
        public boolean enabled;
        public Set<String> worlds = new HashSet<>();
        public Set<World.Environment> environments = new HashSet<>();
        public final List<OreEntry> entries = new ArrayList<>();
    }

    public record Bonus(String permission, double multiplier, Set<Material> blocks) {
    }

    public final Gen stone = new Gen();
    public final Gen basalt = new Gen();
    public boolean deepslateVariants = true;
    public int deepslateBelowY = 0;
    public boolean bonusEnabled;
    public double bonusRadius = 8;
    public final List<Bonus> bonuses = new ArrayList<>();
    public boolean effects = true;

    public static OreConfig parse(ConfigurationSection root, Logger log) {
        OreConfig c = new OreConfig();
        c.deepslateVariants = root.getBoolean("deepslate.enabled", true);
        c.deepslateBelowY = root.getInt("deepslate.below-y", 0);
        c.effects = root.getBoolean("effects", true);
        parseGen(c.stone, root.getConfigurationSection("stone-generator"), log, "stone-generator");
        parseGen(c.basalt, root.getConfigurationSection("basalt-generator"), log, "basalt-generator");
        ConfigurationSection b = root.getConfigurationSection("bonus");
        if (b != null) {
            c.bonusEnabled = b.getBoolean("enabled", false);
            c.bonusRadius = Math.max(1, b.getDouble("player-radius", 8));
            for (java.util.Map<?, ?> m : b.getMapList("entries")) {
                Object perm = m.get("permission");
                if (perm == null) continue;
                double mult = m.get("multiplier") instanceof Number n ? n.doubleValue() : 1.0;
                Set<Material> blocks = new HashSet<>();
                if (m.get("blocks") instanceof List<?> l) {
                    for (Object o : l) {
                        Material mat = Material.matchMaterial(String.valueOf(o));
                        if (mat != null) blocks.add(mat);
                        else log.warning("[oregen] bonus: material tidak dikenal '" + o + "'");
                    }
                }
                c.bonuses.add(new Bonus(String.valueOf(perm), mult, blocks));
            }
        }
        return c;
    }

    private static void parseGen(Gen g, ConfigurationSection s, Logger log, String name) {
        if (s == null) return;
        g.enabled = s.getBoolean("enabled", true);
        for (String w : s.getStringList("worlds")) g.worlds.add(w);
        for (String e : s.getStringList("environments")) {
            try {
                g.environments.add(World.Environment.valueOf(e.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ex) {
                log.warning("[oregen] " + name + ": environment tidak dikenal '" + e + "'");
            }
        }
        for (java.util.Map<?, ?> m : s.getMapList("entries")) {
            Object bo = m.get("block");
            Material mat = bo == null ? null : Material.matchMaterial(String.valueOf(bo));
            if (mat == null || !mat.isBlock()) {
                log.warning("[oregen] " + name + ": block tidak valid '" + bo + "' (dilewati)");
                continue;
            }
            double chance = m.get("chance") instanceof Number n ? n.doubleValue() : 0;
            if (chance <= 0) continue;
            int minY = m.get("min-y") instanceof Number n ? n.intValue() : -2048;
            int maxY = m.get("max-y") instanceof Number n ? n.intValue() : 2048;
            Set<String> worlds = new HashSet<>();
            if (m.get("worlds") instanceof List<?> l) for (Object o : l) worlds.add(String.valueOf(o));
            Set<String> biomes = new HashSet<>();
            if (m.get("biomes") instanceof List<?> l) {
                for (Object o : l) biomes.add(String.valueOf(o).toLowerCase(Locale.ROOT).replace("minecraft:", ""));
            }
            g.entries.add(new OreEntry(mat, chance, minY, maxY, worlds, biomes));
        }
    }
}

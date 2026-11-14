package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.Bukkit;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * MythicLib hook via reflection (no compile-time dependency).
 * Reads a player stat (default MAX_MANA_REGENERATION, in percent) from MMOPlayerData#getStatMap() and uses it
 * to boost the Magicule (Mcu) regeneration of the player's Magicule Power Collectors.
 */
public final class MythicLibHook {
    private static final long CACHE_MS = 1000;

    private final OctaGensPlugin plugin;
    private Method getData;
    private Method getStatMap;
    private Method getStat;
    private final Map<UUID, double[]> cache = new HashMap<>();
    private boolean warned;

    public MythicLibHook(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        try {
            Class<?> c = Class.forName("io.lumine.mythic.lib.api.player.MMOPlayerData");
            getData = c.getMethod("get", UUID.class);
            getStatMap = c.getMethod("getStatMap");
            Class<?> sm = getStatMap.getReturnType();
            getStat = sm.getMethod("getStat", String.class);
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("Hook MythicLib gagal diinisialisasi: " + t);
            return false;
        }
    }

    /** Raw value of the configured stat for an ONLINE player (0 when unavailable). Cached for one second. */
    public double stat(UUID id) {
        long now = System.currentTimeMillis();
        double[] c = cache.get(id);
        if (c != null && now - c[1] < CACHE_MS) return c[0];
        double v = 0;
        if (Bukkit.getPlayer(id) != null) {
            try {
                Object data = getData.invoke(null, id);
                Object map = getStatMap.invoke(data);
                Object r = getStat.invoke(map, plugin.settings().mythicStat);
                if (r instanceof Number n) v = n.doubleValue();
            } catch (Throwable t) {
                if (!warned && plugin.settings().debug) {
                    warned = true;
                    plugin.getLogger().warning("MythicLib stat '" + plugin.settings().mythicStat + "' tidak bisa dibaca: " + t);
                }
            }
        }
        cache.put(id, new double[]{v, now});
        return v;
    }

    /**
     * Applies the bonus to a base regeneration rate.
     * PERCENT: base * (1 + stat * scale / 100)   |   FLAT: base + stat * scale
     */
    public double apply(UUID owner, double base) {
        if (base <= 0) return base;
        double s = stat(owner) * plugin.settings().mythicScale;
        if (s == 0) return base;
        return plugin.settings().mythicMode.equals("FLAT") ? base + s : base * (1.0 + s / 100.0);
    }

    /** Multiplier equivalent of {@link #apply} for a single collector (1.0 when there is no bonus). */
    public double factor(UUID owner) {
        double s = stat(owner) * plugin.settings().mythicScale;
        if (s == 0 || plugin.settings().mythicMode.equals("FLAT")) return 1.0;
        return 1.0 + s / 100.0;
    }

    public void clear(UUID id) {
        cache.remove(id);
    }

    public static String normalizeMode(String s) {
        return s != null && s.trim().toUpperCase(Locale.ROOT).equals("FLAT") ? "FLAT" : "PERCENT";
    }
}

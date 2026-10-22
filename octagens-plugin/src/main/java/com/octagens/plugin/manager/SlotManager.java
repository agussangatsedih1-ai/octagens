package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.storage.Database;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Slot limits: highest permission value (or default) + bonus slots stored in the database. */
public final class SlotManager {
    public static final int UNLIMITED = Integer.MAX_VALUE;
    private static final long CACHE_MS = 3000;

    private record Cached(int gen, int energy, long at) {
    }

    private final OctaGensPlugin plugin;
    private final Map<UUID, int[]> bonus = new HashMap<>();
    private final Map<UUID, Cached> cache = new HashMap<>();

    public SlotManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(List<Database.PlayerRow> rows) {
        bonus.clear();
        for (Database.PlayerRow r : rows) bonus.put(r.uuid(), new int[]{r.bonusGenerators(), r.bonusEnergy()});
    }

    public void clear(UUID id) {
        cache.remove(id);
    }

    public void clearAll() {
        cache.clear();
    }

    public int bonusGenerators(UUID id) {
        int[] b = bonus.get(id);
        return b == null ? 0 : b[0];
    }

    public int bonusEnergy(UUID id) {
        int[] b = bonus.get(id);
        return b == null ? 0 : b[1];
    }

    public void addBonus(UUID id, int genDelta, int energyDelta) {
        int[] b = bonus.computeIfAbsent(id, k -> new int[2]);
        b[0] = Math.max(0, b[0] + genDelta);
        b[1] = Math.max(0, b[1] + energyDelta);
        cache.remove(id);
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        plugin.database().savePlayer(new Database.PlayerRow(id, op.getName(), b[0], b[1]));
    }

    public int generatorLimit(Player p) {
        return limits(p).gen();
    }

    public int energyLimit(Player p) {
        return limits(p).energy();
    }

    public int generatorLimit(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return generatorLimit(p);
        return add(plugin.settings().defaultGeneratorSlots, bonusGenerators(id));
    }

    public int energyLimit(UUID id) {
        Player p = Bukkit.getPlayer(id);
        if (p != null) return energyLimit(p);
        return add(plugin.settings().defaultEnergySlots, bonusEnergy(id));
    }

    private Cached limits(Player p) {
        long now = System.currentTimeMillis();
        Cached c = cache.get(p.getUniqueId());
        if (c != null && now - c.at() < CACHE_MS) return c;
        int gen;
        int en;
        if (p.hasPermission("octagens.admin.bypass")) {
            gen = UNLIMITED;
            en = UNLIMITED;
        } else {
            gen = add(permMax(p, "octagens.generatorslot.", plugin.settings().defaultGeneratorSlots), bonusGenerators(p.getUniqueId()));
            en = add(permMax(p, "octagens.energyslot.", plugin.settings().defaultEnergySlots), bonusEnergy(p.getUniqueId()));
        }
        c = new Cached(gen, en, now);
        cache.put(p.getUniqueId(), c);
        return c;
    }

    private static int add(int a, int b) {
        if (a == UNLIMITED) return UNLIMITED;
        long s = (long) a + b;
        return s >= UNLIMITED ? UNLIMITED : (int) s;
    }

    private int permMax(Player p, String prefix, int def) {
        int best = def;
        for (PermissionAttachmentInfo pai : p.getEffectivePermissions()) {
            if (!pai.getValue()) continue;
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith(prefix)) continue;
            String rest = perm.substring(prefix.length());
            if (rest.equals("*")) return UNLIMITED;
            try {
                best = Math.max(best, Integer.parseInt(rest));
            } catch (NumberFormatException ignored) {
            }
        }
        return best;
    }
}

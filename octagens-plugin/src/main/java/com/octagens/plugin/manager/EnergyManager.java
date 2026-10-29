package com.octagens.plugin.manager;

import com.octagens.api.event.EnergyBreakEvent;
import com.octagens.api.event.McuMaxUpgradeEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.model.EnergyPool;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedEnergy;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.storage.Database;
import com.octagens.plugin.util.BlockKey;
import com.octagens.plugin.util.ChunkKey;
import com.octagens.plugin.util.Fmt;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Placed energy blocks + the per-player Magicule pool.
 * <p>
 * Energy blocks regenerate Mcu/s into the owner's pool (capped by the pool maximum). A generator must have
 * at least one energy block in range and spends its {@code energy-requirement} (Mcu) from the pool on every drop.
 */
public final class EnergyManager {
    private record Rate(int version, double value) {
    }

    private final OctaGensPlugin plugin;
    private final Map<BlockKey, PlacedEnergy> byBlock = new HashMap<>();
    private final Map<UUID, List<PlacedEnergy>> byOwner = new HashMap<>();
    private final Map<ChunkKey, List<PlacedEnergy>> byChunk = new HashMap<>();
    private final Map<UUID, EnergyPool> pools = new HashMap<>();
    private final Map<UUID, Rate> rates = new HashMap<>();
    private BukkitTask saveTask;
    private int version;

    public EnergyManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ registry

    public void load(List<Database.BlockRow> rows) {
        for (Database.BlockRow r : rows) {
            EnergyType t = plugin.registry().energy(r.type());
            if (t == null) plugin.getLogger().warning("Energy dengan tipe '" + r.type() + "' tidak ada di energy.yml (tetap disimpan, tidak aktif).");
            PlacedEnergy e = new PlacedEnergy(r.owner(), r.ownerName(), r.type(), new BlockKey(r.world(), r.x(), r.y(), r.z()),
                    r.placedAt(), t == null ? 0 : t.mcuPerSecond);
            register(e);
        }
    }

    public void loadPools(List<Database.PlayerRow> rows) {
        Settings s = plugin.settings();
        for (Database.PlayerRow r : rows) {
            double max = Math.min(s.mcuMaxLimit, Math.max(r.mcuMax(), s.mcuMaxDefault));
            EnergyPool p = new EnergyPool(Math.max(0, Math.min(max, r.mcuCurrent())), max);
            p.lastSaved = p.current;
            p.savedMax = p.max;
            pools.put(r.uuid(), p);
        }
    }

    public void register(PlacedEnergy e) {
        bump(e.owner);
        PlacedEnergy old = byBlock.put(e.key, e);
        if (old != null) removeIndexes(old);
        byOwner.computeIfAbsent(e.owner, k -> new ArrayList<>()).add(e);
        byChunk.computeIfAbsent(e.key.chunk(), k -> new ArrayList<>()).add(e);
    }

    public void unregister(PlacedEnergy e, boolean deleteDb) {
        bump(e.owner);
        byBlock.remove(e.key);
        removeIndexes(e);
        plugin.holograms().removeEnergy(e);
        if (deleteDb) plugin.database().deleteEnergy(e.key);
    }

    /** Snapshot of every placed energy block (main thread only). */
    public List<PlacedEnergy> all() {
        return new ArrayList<>(byBlock.values());
    }

    /** Settle the pool with the OLD regen rate before it changes, then invalidate caches. */
    private void bump(UUID owner) {
        EnergyPool p = pools.get(owner);
        if (p != null) p.update(System.currentTimeMillis(), regenRate(owner));
        version++;
    }

    private void removeIndexes(PlacedEnergy e) {
        List<PlacedEnergy> o = byOwner.get(e.owner);
        if (o != null) {
            o.remove(e);
            if (o.isEmpty()) byOwner.remove(e.owner);
        }
        List<PlacedEnergy> c = byChunk.get(e.key.chunk());
        if (c != null) {
            c.remove(e);
            if (c.isEmpty()) byChunk.remove(e.key.chunk());
        }
    }

    public PlacedEnergy get(BlockKey k) {
        return byBlock.get(k);
    }

    public PlacedEnergy get(Block b) {
        return byBlock.get(BlockKey.of(b));
    }

    public List<PlacedEnergy> list(UUID owner) {
        List<PlacedEnergy> l = byOwner.get(owner);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(l));
    }

    public int count(UUID owner) {
        List<PlacedEnergy> l = byOwner.get(owner);
        return l == null ? 0 : l.size();
    }

    public int total() {
        return byBlock.size();
    }

    /** Called when energy config reloaded. */
    public void refreshTypes() {
        for (PlacedEnergy e : byBlock.values()) {
            EnergyType t = plugin.registry().energy(e.typeId);
            e.mcu = t == null ? 0 : t.mcuPerSecond;
        }
        Settings s = plugin.settings();
        for (EnergyPool p : pools.values()) {
            p.max = Math.min(s.mcuMaxLimit, Math.max(p.max, s.mcuMaxDefault));
        }
        rates.clear();
        version++;
    }

    public void onChunkLoad(Chunk c) {
        List<PlacedEnergy> list = byChunk.get(ChunkKey.of(c));
        if (list == null || !plugin.settings().verifyBlocks) return;
        for (PlacedEnergy e : new ArrayList<>(list)) {
            Block b = c.getWorld().getBlockAt(e.key.x(), e.key.y(), e.key.z());
            if (b.getType().isAir()) {
                plugin.getLogger().warning("Energy " + e.typeId + " milik " + e.ownerName + " di " + e.key + " tidak ada lagi di dunia; registrasi dihapus.");
                unregister(e, true);
            }
        }
    }

    // ------------------------------------------------------------------ pool

    /** Sum of Mcu/s of every energy block the owner has placed = regeneration per second. */
    public double totalMcu(UUID owner) {
        double sum = 0;
        List<PlacedEnergy> l = byOwner.get(owner);
        if (l != null) for (PlacedEnergy e : l) sum += e.mcu;
        return sum;
    }

    /** Base regeneration (sum of the owner's collectors), cached until a collector is placed/removed. */
    public double baseRate(UUID owner) {
        Rate r = rates.get(owner);
        if (r != null && r.version() == version) return r.value();
        double v = totalMcu(owner);
        rates.put(owner, new Rate(version, v));
        return v;
    }

    /** Effective regeneration per second: base rate plus the MythicLib stat bonus (when MythicLib is hooked). */
    public double regenRate(UUID owner) {
        double base = baseRate(owner);
        var ml = plugin.hooks().ml();
        return ml == null ? base : ml.apply(owner, base);
    }

    /** Effective regeneration of one collector (its Mcu/s times the owner's MythicLib factor). */
    public double effectiveRate(PlacedEnergy e) {
        var ml = plugin.hooks().ml();
        return ml == null ? e.mcu : e.mcu * ml.factor(e.owner);
    }

    /** The owner's pool, settled up to now (created with the default maximum if it does not exist yet). */
    public EnergyPool pool(UUID owner) {
        EnergyPool p = pools.get(owner);
        if (p == null) {
            p = new EnergyPool(0, plugin.settings().mcuMaxDefault);
            pools.put(owner, p);
        }
        p.update(System.currentTimeMillis(), regenRate(owner));
        return p;
    }

    /** Spends Mcu from the pool if enough is stored. */
    public boolean tryConsume(UUID owner, double amount) {
        EnergyPool p = pool(owner);
        if (p.current + 1e-9 < amount) return false;
        p.current = Math.max(0, p.current - amount);
        return true;
    }

    /** Mcu/s of the owner's energy blocks within range of the generator (cached per energy version). */
    public double supply(PlacedGenerator g) {
        if (g.supplyVersion == version) return g.supply;
        double max = plugin.settings().maxDistance;
        double max2 = max * max;
        boolean flat = plugin.settings().distance2D;
        Collection<PlacedEnergy> source = plugin.settings().sharedEnergy ? byBlock.values() : byOwner.getOrDefault(g.owner, Collections.emptyList());
        double sum = 0;
        for (PlacedEnergy e : source) {
            if (!e.key.world().equals(g.key.world())) continue;
            double dx = e.key.x() - g.key.x();
            double dz = e.key.z() - g.key.z();
            double dy = flat ? 0 : e.key.y() - g.key.y();
            if (dx * dx + dy * dy + dz * dz <= max2) sum += e.mcu;
        }
        g.supply = sum;
        g.supplyVersion = version;
        return sum;
    }

    /**
     * Readiness 0..1 = stored Mcu / Mcu needed per drop. 0 when no energy block is in range.
     * Always 1 when energy is disabled or the generator needs none.
     */
    public double efficiency(PlacedGenerator g, GeneratorType t) {
        g.required = t.energyRequirement;
        if (!plugin.settings().energyRequired || t.energyRequirement <= 0) return 1.0;
        if (supply(g) <= 0) return 0.0;
        return Math.min(1.0, pool(g.owner).current / t.energyRequirement);
    }

    /** Average readiness of all generators the owner has placed (1.0 when none). */
    public double averageEfficiency(UUID owner) {
        List<PlacedGenerator> gens = plugin.generators().list(owner);
        if (gens.isEmpty()) return 1.0;
        double sum = 0;
        for (PlacedGenerator g : gens) {
            GeneratorType t = plugin.registry().generator(g.typeId);
            sum += t == null ? 0 : efficiency(g, t);
        }
        return sum / gens.size();
    }

    // ------------------------------------------------------------------ persistence

    public void savePlayer(UUID id) {
        EnergyPool p = pool(id);
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        plugin.database().savePlayer(new Database.PlayerRow(id, op.getName(), plugin.slots().bonusGenerators(id),
                plugin.slots().bonusEnergy(id), p.max, p.current));
        p.lastSaved = p.current;
        p.savedMax = p.max;
    }

    public void startAutosave() {
        stopAutosave();
        int period = plugin.settings().energySaveTicks;
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Map.Entry<UUID, EnergyPool> en : new ArrayList<>(pools.entrySet())) {
                EnergyPool p = en.getValue();
                p.update(System.currentTimeMillis(), regenRate(en.getKey()));
                if (Math.abs(p.current - p.lastSaved) > 0.5 || p.max != p.savedMax) savePlayer(en.getKey());
            }
        }, period, period);
    }

    public void stopAutosave() {
        if (saveTask != null) saveTask.cancel();
        saveTask = null;
    }

    public void saveAll() {
        for (UUID id : new ArrayList<>(pools.keySet())) savePlayer(id);
    }

    public void onQuit(UUID id) {
        if (pools.containsKey(id)) savePlayer(id);
    }

    // ------------------------------------------------------------------ Mcu maximum upgrades

    /** Number of upgrades already bought: (max - default) / step. */
    public int level(EnergyPool p) {
        Settings s = plugin.settings();
        return Math.max(0, (int) Math.round((p.max - s.mcuMaxDefault) / s.upgradeStep));
    }

    public int remainingLevels(EnergyPool p) {
        Settings s = plugin.settings();
        if (p.max >= s.mcuMaxLimit) return 0;
        return (int) Math.ceil((s.mcuMaxLimit - p.max) / s.upgradeStep);
    }

    /** Cost of {@code n} upgrades starting at level {@code level0}: base * (1 + level * increase) per level. */
    public double upgradeCost(int level0, int n) {
        Settings s = plugin.settings();
        double sumLevels = (double) n * level0 + (double) n * (n - 1) / 2.0;
        return s.upgradeCostBase * (n + s.upgradeCostIncrease * sumLevels);
    }

    public double maxAfter(double max, int n) {
        Settings s = plugin.settings();
        return Math.min(s.mcuMaxLimit, max + s.upgradeStep * n);
    }

    /**
     * Buys upgrades of the maximum Mcu.
     * @param requested number of levels, or -1 for "as many as affordable"
     */
    public boolean upgrade(Player p, int requested) {
        Settings s = plugin.settings();
        EnergyPool pool = pool(p.getUniqueId());
        int remaining = remainingLevels(pool);
        if (remaining <= 0) {
            plugin.messages().send(p, "upgrade-max-reached", "limit", Fmt.number(s.mcuMaxLimit));
            return false;
        }
        Currency c = plugin.hooks().currency(s.upgradeCurrency);
        if (c == null) {
            plugin.messages().send(p, "upgrade-unavailable");
            return false;
        }
        int level0 = level(pool);
        int n;
        if (requested < 0) {
            double balance = c.balance(p);
            n = 0;
            while (n < remaining && upgradeCost(level0, n + 1) <= balance + 1e-9) n++;
            if (n == 0) {
                plugin.messages().send(p, "upgrade-not-enough-money", "price", c.format(upgradeCost(level0, 1)));
                return false;
            }
        } else {
            n = Math.min(Math.max(1, requested), remaining);
        }
        double oldMax = pool.max;
        double newMax = maxAfter(oldMax, n);
        McuMaxUpgradeEvent ev = new McuMaxUpgradeEvent(p, oldMax, newMax, n, upgradeCost(level0, n), c.id());
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            plugin.messages().send(p, "buy-cancelled");
            return false;
        }
        double price = ev.getPrice();
        if (!c.has(p, price)) {
            plugin.messages().send(p, "upgrade-not-enough-money", "price", c.format(price));
            return false;
        }
        if (!c.withdraw(p, price)) {
            plugin.messages().send(p, "buy-failed");
            return false;
        }
        pool.max = newMax;
        savePlayer(p.getUniqueId());
        plugin.messages().send(p, "upgrade-success", "levels", String.valueOf(n), "max", Fmt.number(newMax), "price", c.format(price));
        plugin.txLog().log(false, p.getName(), "upgrade Mcu max " + Fmt.number(oldMax) + " -> " + Fmt.number(newMax) + " seharga " + c.format(price), price);
        if (s.cpTransactions) plugin.hooks().cp().logTransaction(p, "octagens-upgrade mcu-max " + newMax);
        return true;
    }

    // ------------------------------------------------------------------ actions

    public PlacedEnergy place(Player p, Block b, EnergyType t) {
        PlacedEnergy e = new PlacedEnergy(p.getUniqueId(), p.getName(), t.id, BlockKey.of(b), System.currentTimeMillis(), t.mcuPerSecond);
        register(e);
        plugin.database().saveEnergy(new Database.BlockRow(e.key.world(), e.key.x(), e.key.y(), e.key.z(), e.owner, e.ownerName, e.typeId, e.placedAt));
        if (plugin.settings().cpPlacement) plugin.hooks().cp().logPlacement(p.getName(), b);
        return e;
    }

    /** Break or sneak-pickup: returns the item to the player. */
    public boolean pickup(Player p, PlacedEnergy e) {
        EnergyType t = plugin.registry().energy(e.typeId);
        if (t == null) {
            plugin.messages().send(p, "energy-unknown-type");
            return false;
        }
        EnergyBreakEvent ev = new EnergyBreakEvent(p, e);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;
        World w = Bukkit.getWorld(e.key.world());
        if (w != null) {
            Block b = w.getBlockAt(e.key.x(), e.key.y(), e.key.z());
            if (plugin.settings().cpRemoval) plugin.hooks().cp().logRemoval(p.getName(), b);
            b.setType(Material.AIR, false);
        }
        unregister(e, true);
        ItemStack item = plugin.items().energyItem(t, 1);
        plugin.items().giveOrDrop(p, item);
        plugin.messages().send(p, "energy-pickup", "name", t.displayName);
        return true;
    }

    /** Admin removal without returning items. */
    public void removeSilently(PlacedEnergy e, String actor) {
        World w = Bukkit.getWorld(e.key.world());
        if (w != null) {
            Block b = w.getBlockAt(e.key.x(), e.key.y(), e.key.z());
            if (plugin.settings().cpRemoval) plugin.hooks().cp().logRemoval(actor, b);
            b.setType(Material.AIR, false);
        }
        unregister(e, true);
    }
}

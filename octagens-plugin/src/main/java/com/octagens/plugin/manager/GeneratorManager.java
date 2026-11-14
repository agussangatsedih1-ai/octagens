package com.octagens.plugin.manager;

import com.octagens.api.event.CancellableOctaGensEvent;
import com.octagens.api.event.EnergyEfficiencyChangeEvent;
import com.octagens.api.event.GeneratorBreakEvent;
import com.octagens.api.event.GeneratorDropEvent;
import com.octagens.api.event.GeneratorPickupEvent;
import com.octagens.api.event.GeneratorUpgradeEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.storage.Database;
import com.octagens.plugin.util.BlockKey;
import com.octagens.plugin.util.ChunkKey;
import com.octagens.plugin.util.Registries;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Registry + the single scheduler that drives every generator (one repeating task, not one per generator).
 */
public final class GeneratorManager {
    private final OctaGensPlugin plugin;
    private final Map<BlockKey, PlacedGenerator> byBlock = new HashMap<>();
    private final Map<UUID, List<PlacedGenerator>> byOwner = new HashMap<>();
    private final Map<ChunkKey, List<PlacedGenerator>> byChunk = new HashMap<>();
    private final Set<PlacedGenerator> active = new LinkedHashSet<>();
    private final Set<String> warned = new HashSet<>();
    private BukkitTask task;
    private long tickCounter;

    public GeneratorManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ registry

    public void load(List<Database.BlockRow> rows) {
        for (Database.BlockRow r : rows) {
            if (plugin.registry().generator(r.type()) == null) {
                plugin.getLogger().warning("Generator bertipe '" + r.type() + "' tidak ada di generators.yml (tetap disimpan, tidak aktif).");
            }
            register(new PlacedGenerator(r.owner(), r.ownerName(), r.type(),
                    new BlockKey(r.world(), r.x(), r.y(), r.z()), r.placedAt()));
        }
    }

    /** Activates generators in chunks that are already loaded (called once after load). */
    public void activateLoadedChunks() {
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) onChunkLoad(c);
        }
    }

    public void register(PlacedGenerator g) {
        PlacedGenerator old = byBlock.put(g.key, g);
        if (old != null) removeIndexes(old);
        byOwner.computeIfAbsent(g.owner, k -> new ArrayList<>()).add(g);
        byChunk.computeIfAbsent(g.key.chunk(), k -> new ArrayList<>()).add(g);
    }

    public void unregister(PlacedGenerator g, boolean deleteDb) {
        byBlock.remove(g.key);
        removeIndexes(g);
        if (deleteDb) plugin.database().deleteGenerator(g.key);
    }

    private void removeIndexes(PlacedGenerator g) {
        active.remove(g);
        plugin.holograms().remove(g);
        List<PlacedGenerator> o = byOwner.get(g.owner);
        if (o != null) {
            o.remove(g);
            if (o.isEmpty()) byOwner.remove(g.owner);
        }
        List<PlacedGenerator> c = byChunk.get(g.key.chunk());
        if (c != null) {
            c.remove(g);
            if (c.isEmpty()) byChunk.remove(g.key.chunk());
        }
    }

    public PlacedGenerator get(BlockKey k) {
        return byBlock.get(k);
    }

    public PlacedGenerator get(Block b) {
        return byBlock.get(BlockKey.of(b));
    }

    public List<PlacedGenerator> list(UUID owner) {
        List<PlacedGenerator> l = byOwner.get(owner);
        return l == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(l));
    }

    public int count(UUID owner) {
        List<PlacedGenerator> l = byOwner.get(owner);
        return l == null ? 0 : l.size();
    }

    public Collection<PlacedGenerator> all() {
        return new ArrayList<>(byBlock.values());
    }

    public List<PlacedGenerator> activeSnapshot() {
        return new ArrayList<>(active);
    }

    public boolean isActive(PlacedGenerator g) {
        return active.contains(g);
    }

    public int total() {
        return byBlock.size();
    }

    // ------------------------------------------------------------------ chunks

    public void onChunkLoad(Chunk c) {
        List<PlacedGenerator> list = byChunk.get(ChunkKey.of(c));
        if (list == null) return;
        for (PlacedGenerator g : new ArrayList<>(list)) {
            if (plugin.settings().verifyBlocks && c.getWorld().getBlockAt(g.key.x(), g.key.y(), g.key.z()).getType().isAir()) {
                plugin.getLogger().warning("Generator " + g.typeId + " milik " + g.ownerName + " di " + g.key + " tidak ada lagi di dunia; registrasi dihapus.");
                unregister(g, true);
                continue;
            }
            active.add(g);
        }
    }

    public void onChunkUnload(Chunk c) {
        List<PlacedGenerator> list = byChunk.get(ChunkKey.of(c));
        if (list == null) return;
        for (PlacedGenerator g : list) {
            active.remove(g);
            plugin.holograms().remove(g);
        }
    }

    // ------------------------------------------------------------------ scheduler

    public void start() {
        stop();
        int period = plugin.settings().tickInterval;
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void tick() {
        Settings s = plugin.settings();
        int period = s.tickInterval;
        tickCounter += period;
        PlacedGenerator[] snapshot = active.toArray(new PlacedGenerator[0]);
        for (PlacedGenerator g : snapshot) {
            if (!active.contains(g)) continue;
            try {
                process(g, s, period);
            } catch (Exception ex) {
                if (warned.add("tick:" + g.typeId)) plugin.getLogger().warning("Error saat memproses generator " + g.typeId + ": " + ex);
            }
        }
    }

    private void process(PlacedGenerator g, Settings s, int period) {
        GeneratorType t = plugin.registry().generator(g.typeId);
        if (t == null) {
            g.status = PlacedGenerator.Status.UNKNOWN;
            return;
        }
        World w = Bukkit.getWorld(g.key.world());
        if (w == null) return;
        if (s.verifyBlocks && w.getBlockAt(g.key.x(), g.key.y(), g.key.z()).getType().isAir()) {
            plugin.getLogger().warning("Generator " + g.typeId + " di " + g.key + " hilang dari dunia; registrasi dihapus.");
            unregister(g, true);
            return;
        }
        if (s.onlyWhenOwnerOnline && Bukkit.getPlayer(g.owner) == null) {
            g.status = PlacedGenerator.Status.OWNER_OFFLINE;
            return;
        }

        // The drop timer always runs; it simply waits at "ready" until the owner has enough Mcu.
        double interval = t.dropIntervalTicks;
        if (g.progress < interval) g.progress = Math.min(interval, g.progress + period);

        EnergyManager em = plugin.energy();
        boolean needsEnergy = s.energyRequired && t.energyRequirement > 0;
        double eff = em.efficiency(g, t);
        boolean powered = !needsEnergy || eff >= 1.0 - 1e-9;
        if (g.lastEfficiency < 0) {
            g.lastEfficiency = eff;
            g.powered = powered;
        } else if (powered != g.powered) {
            Bukkit.getPluginManager().callEvent(new EnergyEfficiencyChangeEvent(g, g.lastEfficiency, eff));
            g.lastEfficiency = eff;
            g.powered = powered;
        }

        if (!powered) {
            if (em.supply(g) <= 0) g.status = PlacedGenerator.Status.NOT_CONNECTED;
            else if (t.energyRequirement > em.pool(g.owner).max) g.status = PlacedGenerator.Status.NO_CAPACITY;
            else g.status = PlacedGenerator.Status.NO_ENERGY;
            return;
        }
        if (g.progress < interval) {
            g.status = PlacedGenerator.Status.ACTIVE;
            return;
        }
        if (tickCounter < g.nextFullCheck) {
            g.status = PlacedGenerator.Status.FULL;
            return;
        }
        if (storedAmount(g, w) >= s.maxStoredDrops) {
            g.nextFullCheck = tickCounter + s.fullRecheckTicks;
            g.status = PlacedGenerator.Status.FULL;
            return;
        }
        // Spend the Mcu only when the drop really happens (another generator of the same owner may have taken it).
        if (needsEnergy && !em.tryConsume(g.owner, t.energyRequirement)) {
            g.status = PlacedGenerator.Status.NO_ENERGY;
            return;
        }
        g.status = PlacedGenerator.Status.ACTIVE;
        g.progress = 0;
        drop(g, t, w);
    }

    private int storedAmount(PlacedGenerator g, World w) {
        Location c = new Location(w, g.key.x() + 0.5, g.key.y() + 1.5, g.key.z() + 0.5);
        int sum = 0;
        for (Entity e : w.getNearbyEntities(c, 1.0, 1.5, 1.0, en -> en instanceof Item)) {
            ItemStack st = ((Item) e).getItemStack();
            if (g.typeId.equals(plugin.items().dropId(st))) sum += st.getAmount();
        }
        return sum;
    }

    private void drop(PlacedGenerator g, GeneratorType t, World w) {
        double mult = plugin.boosters() == null ? plugin.weekend().dropMultiplier() : plugin.boosters().dropMultiplier();
        GeneratorDropEvent ev = new GeneratorDropEvent(g, t.dropAmount, mult);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return;
        int total = ev.getFinalAmount();
        if (total <= 0) return;
        ItemStack proto = plugin.items().dropItem(t, 1, g.owner);
        if (proto == null) {
            if (warned.add("drop:" + t.id)) plugin.getLogger().warning("Generator '" + t.id + "': drop-item tidak bisa dibuat (hook/item hilang). Drop dilewati.");
            return;
        }
        Location loc = new Location(w, g.key.x() + 0.5, g.key.y() + 1.1, g.key.z() + 0.5);
        int maxStack = Math.max(1, proto.getMaxStackSize());
        int left = total;
        while (left > 0) {
            int n = Math.min(maxStack, left);
            left -= n;
            ItemStack st = proto.clone();
            st.setAmount(n);
            Item it = w.dropItem(loc, st);
            it.setVelocity(new Vector(0, 0, 0));
            if (plugin.settings().dropInvulnerable) it.setInvulnerable(true);
        }
        if (t.effects) {
            Particle p = t.particle == null || t.particle.isBlank() ? null : Registries.particle(t.particle);
            if (p != null && t.particleCount > 0) w.spawnParticle(p, loc.clone().add(0, 0.3, 0), t.particleCount, 0.25, 0.25, 0.25, 0.01);
            if (t.sound != null && !t.sound.isBlank()) w.playSound(loc, t.sound, t.soundVolume, t.soundPitch);
        }
        if (plugin.settings().holoOnDrop) plugin.holograms().showOnDrop(g, w, tickCounter);
    }

    // ------------------------------------------------------------------ upgrade

    /** The next generator tier (by shop-slot order) or null when this is the highest tier. */
    public GeneratorType nextType(PlacedGenerator g) {
        List<GeneratorType> list = plugin.registry().generators();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(g.typeId)) return i + 1 < list.size() ? list.get(i + 1) : null;
        }
        return null;
    }

    /** Upgrade price: shop price of the next tier (FULL) or the difference with the current tier (DIFFERENCE). */
    public double upgradePrice(GeneratorType from, GeneratorType to) {
        if (plugin.settings().genUpgradeDifference) return Math.max(0, to.shopPrice - from.shopPrice);
        return to.shopPrice;
    }

    /** Upgrades a placed generator to the next tier, charging the player. Returns true on success. */
    public boolean upgrade(Player p, PlacedGenerator g) {
        if (!plugin.settings().genUpgradeEnabled) {
            plugin.messages().send(p, "gen-upgrade-disabled");
            return false;
        }
        GeneratorType from = plugin.registry().generator(g.typeId);
        if (from == null) {
            plugin.messages().send(p, "generator-unknown-type");
            return false;
        }
        GeneratorType to = nextType(g);
        if (to == null) {
            plugin.messages().send(p, "gen-upgrade-max");
            return false;
        }
        com.octagens.plugin.hook.Currency c = plugin.hooks().currency(to.currency);
        ItemStack newItem = c == null ? null : plugin.items().generatorItem(to, 1);
        if ((!to.permission.isBlank() && !p.hasPermission(to.permission)) || newItem == null) {
            plugin.messages().send(p, "gen-upgrade-locked");
            return false;
        }
        double price = upgradePrice(from, to);
        GeneratorUpgradeEvent ev = new GeneratorUpgradeEvent(p, g, from.id, to.id, price, c.id());
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            plugin.messages().send(p, "buy-cancelled");
            return false;
        }
        price = ev.getPrice();
        if (!c.has(p, price)) {
            plugin.messages().send(p, "buy-not-enough-money", "price", c.format(price));
            return false;
        }
        if (!c.withdraw(p, price)) {
            plugin.messages().send(p, "buy-failed");
            return false;
        }
        World w = Bukkit.getWorld(g.key.world());
        if (w != null) {
            Block b = w.getBlockAt(g.key.x(), g.key.y(), g.key.z());
            Material m = newItem.getType();
            if (m != Material.PLAYER_HEAD && b.getType() != m) {
                if (plugin.settings().cpRemoval) plugin.hooks().cp().logRemoval(p.getName(), b);
                b.setType(m, false);
                if (plugin.settings().cpRemoval) plugin.hooks().cp().logPlacement(p.getName(), b);
            }
        }
        g.typeId = to.id;
        g.progress = 0;
        g.status = PlacedGenerator.Status.ACTIVE;
        plugin.database().saveGenerator(new Database.BlockRow(g.key.world(), g.key.x(), g.key.y(), g.key.z(), g.owner, g.ownerName, g.typeId, g.placedAt));
        plugin.holograms().remove(g);
        plugin.holograms().show(g);
        plugin.messages().send(p, "gen-upgrade-success", "name", to.displayName, "price", c.format(price));
        plugin.txLog().log(false, p.getName(), "upgrade generator " + from.id + " -> " + to.id + " seharga " + c.format(price), price);
        if (plugin.settings().cpTransactions) plugin.hooks().cp().logTransaction(p, "octagens-upgrade generator " + from.id + "->" + to.id + " " + price);
        if (plugin.missions() != null) plugin.missions().progress(p, "UPGRADE_GENERATOR", 1);
        if (plugin.playerData() != null) plugin.playerData().save(p);
        return true;
    }

    // ------------------------------------------------------------------ actions

    public PlacedGenerator place(Player p, Block b, GeneratorType t) {
        PlacedGenerator g = new PlacedGenerator(p.getUniqueId(), p.getName(), t.id, BlockKey.of(b), System.currentTimeMillis());
        register(g);
        active.add(g);
        plugin.database().saveGenerator(new Database.BlockRow(g.key.world(), g.key.x(), g.key.y(), g.key.z(), g.owner, g.ownerName, g.typeId, g.placedAt));
        if (plugin.settings().cpPlacement) plugin.hooks().cp().logPlacement(p.getName(), b);
        if (plugin.settings().holoOnPlace) plugin.holograms().show(g);
        if (plugin.playerData() != null) plugin.playerData().save(p);
        return g;
    }

    /**
     * Picks a generator up and returns it to the player.
     * @param viaBreak true when triggered by breaking the block (fires GeneratorBreakEvent), false for pickup
     */
    public boolean pickup(Player p, PlacedGenerator g, boolean viaBreak) {
        GeneratorType t = plugin.registry().generator(g.typeId);
        if (t == null) {
            plugin.messages().send(p, "generator-unknown-type");
            return false;
        }
        ItemStack item = plugin.items().generatorItem(t, 1);
        if (item == null) {
            plugin.messages().send(p, "generator-item-unavailable");
            return false;
        }
        CancellableOctaGensEvent ev = viaBreak ? new GeneratorBreakEvent(p, g) : new GeneratorPickupEvent(p, g);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;
        World w = Bukkit.getWorld(g.key.world());
        if (w != null) {
            Block b = w.getBlockAt(g.key.x(), g.key.y(), g.key.z());
            if (plugin.settings().cpRemoval) plugin.hooks().cp().logRemoval(p.getName(), b);
            b.setType(Material.AIR, false);
        }
        unregister(g, true);
        plugin.items().giveOrDrop(p, item);
        plugin.messages().send(p, "generator-pickup", "name", t.displayName);
        if (plugin.playerData() != null) plugin.playerData().save(p);
        return true;
    }

    /** Admin removal without returning items. */
    public void removeSilently(PlacedGenerator g, String actor) {
        World w = Bukkit.getWorld(g.key.world());
        if (w != null) {
            Block b = w.getBlockAt(g.key.x(), g.key.y(), g.key.z());
            if (plugin.settings().cpRemoval) plugin.hooks().cp().logRemoval(actor, b);
            b.setType(Material.AIR, false);
        }
        unregister(g, true);
    }
}

package com.octagens.plugin.manager;

import com.octagens.api.event.EnergyBreakEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedEnergy;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.storage.Database;
import com.octagens.plugin.util.BlockKey;
import com.octagens.plugin.util.ChunkKey;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Registry of placed energy blocks and the Mcu/s supply / efficiency calculation. */
public final class EnergyManager {
    private final OctaGensPlugin plugin;
    private final Map<BlockKey, PlacedEnergy> byBlock = new HashMap<>();
    private final Map<UUID, List<PlacedEnergy>> byOwner = new HashMap<>();
    private final Map<ChunkKey, List<PlacedEnergy>> byChunk = new HashMap<>();
    private int version;

    public EnergyManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(List<Database.BlockRow> rows) {
        for (Database.BlockRow r : rows) {
            EnergyType t = plugin.registry().energy(r.type());
            if (t == null) plugin.getLogger().warning("Energy dengan tipe '" + r.type() + "' tidak ada di energy.yml (tetap disimpan, tidak aktif).");
            PlacedEnergy e = new PlacedEnergy(r.owner(), r.ownerName(), r.type(), new BlockKey(r.world(), r.x(), r.y(), r.z()),
                    r.placedAt(), t == null ? 0 : t.mcuPerSecond);
            register(e);
        }
    }

    public void register(PlacedEnergy e) {
        PlacedEnergy old = byBlock.put(e.key, e);
        if (old != null) removeIndexes(old);
        byOwner.computeIfAbsent(e.owner, k -> new ArrayList<>()).add(e);
        byChunk.computeIfAbsent(e.key.chunk(), k -> new ArrayList<>()).add(e);
        version++;
    }

    public void unregister(PlacedEnergy e, boolean deleteDb) {
        byBlock.remove(e.key);
        removeIndexes(e);
        version++;
        if (deleteDb) plugin.database().deleteEnergy(e.key);
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

    /** Called when energy config reloaded or energies changed. */
    public void refreshTypes() {
        for (PlacedEnergy e : byBlock.values()) {
            EnergyType t = plugin.registry().energy(e.typeId);
            e.mcu = t == null ? 0 : t.mcuPerSecond;
        }
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

    /** Sum of Mcu/s of every energy block the owner has placed. */
    public double totalMcu(UUID owner) {
        double sum = 0;
        List<PlacedEnergy> l = byOwner.get(owner);
        if (l != null) for (PlacedEnergy e : l) sum += e.mcu;
        return sum;
    }

    /** Mcu/s reaching the generator (cached per energy version). */
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

    /** efficiency = min(1, supply / requirement); 1 when no requirement. */
    public double efficiency(PlacedGenerator g, GeneratorType t) {
        g.required = t.energyRequirement;
        if (t.energyRequirement <= 0) return 1.0;
        return Math.min(1.0, supply(g) / t.energyRequirement);
    }

    /** Average efficiency of all generators the owner has placed (1.0 when none). */
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

    // ------------------------------------------------------------ actions

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

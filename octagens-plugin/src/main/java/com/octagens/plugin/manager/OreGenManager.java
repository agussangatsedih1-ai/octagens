package com.octagens.plugin.manager;

import com.octagens.api.event.OreGeneratorFormEvent;
import com.octagens.api.model.OreGeneratorType;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.OreConfig;
import com.octagens.plugin.model.OreEntry;
import com.octagens.plugin.util.Registries;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockFromToEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Custom stone (lava+water) and basalt (lava+blue ice+soul soil) generators. */
public final class OreGenManager {
    private static final BlockFace[] FACES = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    private static final BlockFace[] HORIZONTAL = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};

    private final OctaGensPlugin plugin;

    public OreGenManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    /** Handles a liquid flow. Returns true when the event was replaced by a generated block. */
    public boolean handle(BlockFromToEvent e) {
        Block from = e.getBlock();
        Block to = e.getToBlock();
        Material ft = from.getType();
        if (ft != Material.WATER && ft != Material.LAVA) return false;
        if (!to.getType().isAir()) return false;

        OreConfig cfg = plugin.registry().ore();
        OreGeneratorType kind = detect(ft, to);
        if (kind == null) return false;
        OreConfig.Gen gen = kind == OreGeneratorType.STONE ? cfg.stone : cfg.basalt;
        if (!gen.enabled || gen.entries.isEmpty()) return false;
        World w = to.getWorld();
        if (!gen.worlds.isEmpty() && gen.worlds.stream().noneMatch(n -> n.equalsIgnoreCase(w.getName()))) return false;
        if (!gen.environments.isEmpty() && !gen.environments.contains(w.getEnvironment())) return false;

        Player nearest = cfg.bonusEnabled ? nearestPlayer(to, cfg.bonusRadius) : null;
        Material result = choose(to, gen, cfg, nearest);
        if (result == null) return false;

        OreGeneratorFormEvent ev = new OreGeneratorFormEvent(to, kind, result);
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) return false;

        e.setCancelled(true);
        to.setType(ev.getResult());
        if (cfg.effects) {
            Location c = to.getLocation().add(0.5, 0.5, 0.5);
            w.playSound(c, "block.lava.extinguish", 0.4f, 2.0f);
            Particle smoke = Registries.particle("SMOKE");
            if (smoke == null) smoke = Registries.particle("SMOKE_NORMAL");
            if (smoke != null) w.spawnParticle(smoke, c.add(0, 0.5, 0), 4, 0.2, 0.1, 0.2, 0.01);
        }
        if (plugin.settings().cpOregen) {
            plugin.hooks().cp().logPlacement("#octagens-oregen", to.getLocation(), to.getType(), to.getBlockData());
        }
        return true;
    }

    private OreGeneratorType detect(Material flowing, Block to) {
        if (flowing == Material.LAVA && to.getRelative(BlockFace.DOWN).getType() == Material.SOUL_SOIL) {
            for (BlockFace f : HORIZONTAL) {
                if (to.getRelative(f).getType() == Material.BLUE_ICE) return OreGeneratorType.BASALT;
            }
        }
        for (BlockFace f : FACES) {
            Block n = to.getRelative(f);
            boolean match = flowing == Material.WATER ? n.getType() == Material.LAVA : holdsWater(n);
            if (match) return OreGeneratorType.STONE;
        }
        return null;
    }

    /**
     * True for water, waterlogged blocks (slabs, stairs, fences, trapdoors...) and blocks that always contain
     * water (kelp, seagrass, bubble column). Vanilla treats all of these as water for cobblestone generation.
     */
    private static boolean holdsWater(Block b) {
        Material m = b.getType();
        if (m == Material.WATER) return true;
        switch (m) {
            case KELP, KELP_PLANT, SEAGRASS, TALL_SEAGRASS, BUBBLE_COLUMN:
                return true;
            default:
                break;
        }
        return b.getBlockData() instanceof Waterlogged wl && wl.isWaterlogged();
    }

    private Player nearestPlayer(Block b, double radius) {
        Player best = null;
        double bestD = radius * radius;
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);
        for (Player p : b.getWorld().getPlayers()) {
            double d = p.getLocation().distanceSquared(loc);
            if (d <= bestD) {
                bestD = d;
                best = p;
            }
        }
        return best;
    }

    private Material choose(Block b, OreConfig.Gen gen, OreConfig cfg, Player nearest) {
        int y = b.getY();
        String world = b.getWorld().getName();
        String biome = String.valueOf(b.getBiome()).toLowerCase(Locale.ROOT).replace("minecraft:", "");
        List<Material> mats = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        double total = 0;
        for (OreEntry en : gen.entries) {
            if (y < en.minY() || y > en.maxY()) continue;
            if (!en.worlds().isEmpty() && en.worlds().stream().noneMatch(n -> n.equalsIgnoreCase(world))) continue;
            if (!en.biomes().isEmpty() && !en.biomes().contains(biome)) continue;
            double chance = en.chance() * bonusFor(nearest, en.material(), cfg);
            Material m = cfg.deepslateVariants && y < cfg.deepslateBelowY ? deepslateOf(en.material()) : en.material();
            mats.add(m);
            weights.add(chance);
            total += chance;
        }
        if (mats.isEmpty() || total <= 0) return null;
        double roll = ThreadLocalRandom.current().nextDouble() * total; // chances are normalised automatically
        for (int i = 0; i < mats.size(); i++) {
            roll -= weights.get(i);
            if (roll <= 0) return mats.get(i);
        }
        return mats.get(mats.size() - 1);
    }

    private double bonusFor(Player p, Material m, OreConfig cfg) {
        if (p == null || !cfg.bonusEnabled) return 1.0;
        double best = 1.0;
        for (OreConfig.Bonus b : cfg.bonuses) {
            if (!b.blocks().isEmpty() && !b.blocks().contains(m)) continue;
            if (p.hasPermission(b.permission())) best = Math.max(best, b.multiplier());
        }
        return best;
    }

    /** Overworld stone -> deepslate, cobblestone -> cobbled deepslate, *_ORE -> DEEPSLATE_*_ORE. */
    public static Material deepslateOf(Material m) {
        String n = m.name();
        if (n.endsWith("_ORE") && !n.startsWith("DEEPSLATE_") && !n.startsWith("NETHER_")) {
            Material d = Material.matchMaterial("DEEPSLATE_" + n);
            return d != null ? d : m;
        }
        if (m == Material.STONE) return Material.DEEPSLATE;
        if (m == Material.COBBLESTONE) return Material.COBBLED_DEEPSLATE;
        return m;
    }
}

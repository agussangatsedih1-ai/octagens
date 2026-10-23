package com.octagens.plugin;

import com.octagens.api.OctaGensAPI;
import com.octagens.api.model.EnergyInfo;
import com.octagens.api.model.GeneratorInfo;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.util.BlockKey;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implementation of the public API. */
final class OctaGensAPIImpl implements OctaGensAPI {
    private final OctaGensPlugin plugin;

    OctaGensAPIImpl(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    private PlacedGenerator resolve(GeneratorInfo info) {
        if (info instanceof PlacedGenerator pg) return pg;
        return info == null ? null : plugin.generators().get(new BlockKey(info.getWorldName(), info.getX(), info.getY(), info.getZ()));
    }

    @Override public List<GeneratorInfo> getGenerators(UUID owner) { return new ArrayList<>(plugin.generators().list(owner)); }
    @Override public List<EnergyInfo> getEnergies(UUID owner) { return new ArrayList<>(plugin.energy().list(owner)); }

    @Override
    public Optional<GeneratorInfo> getGeneratorAt(Location l) {
        return l == null || l.getWorld() == null ? Optional.empty() : Optional.ofNullable(plugin.generators().get(BlockKey.of(l)));
    }

    @Override
    public Optional<EnergyInfo> getEnergyAt(Location l) {
        return l == null || l.getWorld() == null ? Optional.empty() : Optional.ofNullable(plugin.energy().get(BlockKey.of(l)));
    }

    @Override public double getTotalMagicule(UUID owner) { return plugin.energy().totalMcu(owner); }

    @Override
    public double getSupply(GeneratorInfo generator) {
        PlacedGenerator g = resolve(generator);
        return g == null ? 0 : plugin.energy().supply(g);
    }

    @Override
    public double getEfficiency(GeneratorInfo generator) {
        PlacedGenerator g = resolve(generator);
        if (g == null) return 0;
        GeneratorType t = plugin.registry().generator(g.typeId);
        return t == null ? 0 : plugin.energy().efficiency(g, t);
    }

    @Override public double getMcuCurrent(UUID owner) { return plugin.energy().pool(owner).current; }
    @Override public double getMcuMax(UUID owner) { return plugin.energy().pool(owner).max; }
    @Override public double getMcuRegen(UUID owner) { return plugin.energy().regenRate(owner); }
    @Override public double getAverageEfficiency(UUID owner) { return plugin.energy().averageEfficiency(owner); }
    @Override public int getGeneratorLimit(UUID owner) { return plugin.slots().generatorLimit(owner); }
    @Override public int getEnergyLimit(UUID owner) { return plugin.slots().energyLimit(owner); }
    @Override public int getBonusGeneratorSlots(UUID owner) { return plugin.slots().bonusGenerators(owner); }
    @Override public int getBonusEnergySlots(UUID owner) { return plugin.slots().bonusEnergy(owner); }
    @Override public void addGeneratorSlots(UUID owner, int amount) { plugin.slots().addBonus(owner, Math.abs(amount), 0); }
    @Override public void removeGeneratorSlots(UUID owner, int amount) { plugin.slots().addBonus(owner, -Math.abs(amount), 0); }
    @Override public void addEnergySlots(UUID owner, int amount) { plugin.slots().addBonus(owner, 0, Math.abs(amount)); }
    @Override public void removeEnergySlots(UUID owner, int amount) { plugin.slots().addBonus(owner, 0, -Math.abs(amount)); }
    @Override public boolean isWeekendBonusActive() { return plugin.weekend().isActive(); }
    @Override public double getWeekendMultiplier() { return plugin.weekend().dropMultiplier(); }
    @Override public long getWeekendRemainingSeconds() { return plugin.weekend().remainingSeconds(); }
}

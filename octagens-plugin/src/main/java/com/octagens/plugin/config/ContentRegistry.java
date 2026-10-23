package com.octagens.plugin.config;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyRecipe;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.OreConfig;
import com.octagens.plugin.model.SellwandType;
import com.octagens.plugin.util.Yml;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.Material;

/** Holds generators, energies, recipes and ore generator definitions. */
public final class ContentRegistry {
    private final OctaGensPlugin plugin;
    private Map<String, GeneratorType> generators = new LinkedHashMap<>();
    private Map<String, EnergyType> energies = new LinkedHashMap<>();
    private Map<String, EnergyRecipe> recipes = new LinkedHashMap<>();
    private OreConfig ore = new OreConfig();
    private Map<String, SellwandType> wands = new LinkedHashMap<>();
    private Set<String> wandContainers = new HashSet<>();
    private String wandSuccessSound = "";
    private String wandBrokenSound = "";

    public ContentRegistry(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String defCur = plugin.settings().defaultCurrency;

        Map<String, GeneratorType> g = new LinkedHashMap<>();
        YamlConfiguration gy = Yml.load(plugin, "generators.yml", false);
        ConfigurationSection groot = gy.getConfigurationSection("generators");
        if (groot != null) {
            List<GeneratorType> list = new ArrayList<>();
            for (String key : groot.getKeys(false)) {
                ConfigurationSection s = groot.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    list.add(GeneratorType.parse(key, s, defCur));
                } catch (Exception ex) {
                    plugin.getLogger().warning("generators.yml: gagal memuat '" + key + "': " + ex.getMessage());
                }
            }
            list.sort(Comparator.comparingInt(t -> t.shopSlot));
            int tier = 1;
            for (GeneratorType t : list) {
                t.tier = tier++;
                g.put(t.id, t);
            }
        }
        generators = g;

        Map<String, EnergyType> e = new LinkedHashMap<>();
        YamlConfiguration ey = Yml.load(plugin, "energy.yml", false);
        ConfigurationSection eroot = ey.getConfigurationSection("energy");
        if (eroot != null) {
            for (String key : eroot.getKeys(false)) {
                ConfigurationSection s = eroot.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    EnergyType t = EnergyType.parse(key, s);
                    e.put(t.id, t);
                } catch (Exception ex) {
                    plugin.getLogger().warning("energy.yml: gagal memuat '" + key + "': " + ex.getMessage());
                }
            }
        }
        energies = e;

        Map<String, EnergyRecipe> r = new LinkedHashMap<>();
        YamlConfiguration ry = Yml.load(plugin, "energyshop.yml", false);
        ConfigurationSection rroot = ry.getConfigurationSection("recipes");
        if (rroot != null) {
            for (String key : rroot.getKeys(false)) {
                ConfigurationSection s = rroot.getConfigurationSection(key);
                if (s == null) continue;
                if (!e.containsKey(key.toLowerCase(Locale.ROOT))) {
                    plugin.getLogger().warning("energyshop.yml: resep '" + key + "' tidak punya energy dengan id yang sama (dilewati)");
                    continue;
                }
                try {
                    EnergyRecipe rec = EnergyRecipe.parse(key, s, defCur);
                    r.put(rec.energyId, rec);
                } catch (Exception ex) {
                    plugin.getLogger().warning("energyshop.yml: gagal memuat '" + key + "': " + ex.getMessage());
                }
            }
        }
        recipes = r;

        YamlConfiguration oy = Yml.load(plugin, "oregen.yml", false);
        ore = OreConfig.parse(oy, plugin.getLogger());

        Map<String, SellwandType> w = new LinkedHashMap<>();
        YamlConfiguration wy = Yml.load(plugin, "sellwand.yml", false);
        ConfigurationSection wroot = wy.getConfigurationSection("wands");
        if (wroot != null) {
            for (String key : wroot.getKeys(false)) {
                ConfigurationSection s = wroot.getConfigurationSection(key);
                if (s == null) continue;
                try {
                    SellwandType t = SellwandType.parse(key, s);
                    w.put(t.id, t);
                } catch (Exception ex) {
                    plugin.getLogger().warning("sellwand.yml: gagal memuat '" + key + "': " + ex.getMessage());
                }
            }
        }
        wands = w;
        wandContainers = new HashSet<>();
        for (String c : wy.getStringList("settings.containers")) wandContainers.add(c.toUpperCase(Locale.ROOT));
        wandSuccessSound = wy.getString("settings.success-sound", "");
        wandBrokenSound = wy.getString("settings.broken-sound", "");

        plugin.getLogger().info("Dimuat: " + generators.size() + " generator, " + energies.size()
                + " energy, " + recipes.size() + " resep energy shop, " + wands.size() + " sellwand.");
    }

    public GeneratorType generator(String id) {
        return id == null ? null : generators.get(id.toLowerCase(Locale.ROOT));
    }

    public List<GeneratorType> generators() {
        return Collections.unmodifiableList(new ArrayList<>(generators.values()));
    }

    public EnergyType energy(String id) {
        return id == null ? null : energies.get(id.toLowerCase(Locale.ROOT));
    }

    public List<EnergyType> energies() {
        return Collections.unmodifiableList(new ArrayList<>(energies.values()));
    }

    public EnergyRecipe recipe(String energyId) {
        return energyId == null ? null : recipes.get(energyId.toLowerCase(Locale.ROOT));
    }

    public List<EnergyRecipe> recipes() {
        return Collections.unmodifiableList(new ArrayList<>(recipes.values()));
    }

    public OreConfig ore() {
        return ore;
    }

    public SellwandType wand(String id) {
        return id == null ? null : wands.get(id.toLowerCase(Locale.ROOT));
    }

    public List<SellwandType> wands() {
        return Collections.unmodifiableList(new ArrayList<>(wands.values()));
    }

    /** True when the block type is listed under sellwand.yml -> settings.containers. */
    public boolean isWandContainer(Material m) {
        String n = m.name();
        for (String c : wandContainers) {
            if (n.equals(c) || (c.equals("SHULKER_BOX") && n.endsWith("SHULKER_BOX"))) return true;
        }
        return false;
    }

    public String wandSuccessSound() {
        return wandSuccessSound;
    }

    public String wandBrokenSound() {
        return wandBrokenSound;
    }
}

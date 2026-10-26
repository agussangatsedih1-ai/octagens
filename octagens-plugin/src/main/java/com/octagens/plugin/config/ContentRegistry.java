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

/** Holds generators, energies, recipes and ore generator definitions. */
public final class ContentRegistry {
    private final OctaGensPlugin plugin;
    private Map<String, GeneratorType> generators = new LinkedHashMap<>();
    private Map<String, EnergyType> energies = new LinkedHashMap<>();
    private Map<String, EnergyRecipe> recipes = new LinkedHashMap<>();
    private OreConfig ore = new OreConfig();
    private Map<String, SellwandType> wands = new LinkedHashMap<>();

    public ContentRegistry(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        String defCur = plugin.settings().defaultCurrency;

        Map<String, GeneratorType> g = new LinkedHashMap<>();
        YamlConfiguration gy = Yml.load(plugin, "generators/generators.yml", false);
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
        YamlConfiguration ey = Yml.load(plugin, "energy/energy.yml", false);
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
        YamlConfiguration ty = Yml.load(plugin, "energy/textures.yml", false);
        ConfigurationSection ts = ty.getConfigurationSection("textures");
        if (ts != null) {
            for (EnergyType et : e.values()) {
                String v = ts.getString(et.id);
                if (v != null && !v.isBlank()) et.headTexture = v.trim();
            }
        }
        energies = e;
        List<String> noTexture = new ArrayList<>();
        for (EnergyType et : e.values()) {
            if (!com.octagens.plugin.item.ItemService.validTexture(et.headTexture)) noTexture.add(et.id);
        }
        if (!noTexture.isEmpty()) {
            plugin.getLogger().warning("Magicule Power Collector tanpa head texture valid (memakai block cadangan): "
                    + String.join(", ", noTexture) + ". Atur lewat /octagens settexture <id> atau energy/textures.yml.");
        }

        Map<String, EnergyRecipe> r = new LinkedHashMap<>();
        YamlConfiguration ry = Yml.load(plugin, "energy/energyshop.yml", false);
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

        YamlConfiguration oy = Yml.load(plugin, "oregen/oregen.yml", false);
        ore = OreConfig.parse(oy, plugin.getLogger());

        Map<String, SellwandType> w = new LinkedHashMap<>();
        YamlConfiguration wy = Yml.load(plugin, "sellwand/sellwand.yml", false);
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

    /** Saves a texture override for a collector into energy/textures.yml and applies it immediately. */
    public boolean setTexture(String energyId, String base64) {
        EnergyType t = energy(energyId);
        if (t == null) return false;
        t.headTexture = base64;
        java.io.File file = new java.io.File(plugin.getDataFolder(), "energy/textures.yml");
        YamlConfiguration y = Yml.load(plugin, "energy/textures.yml", false);
        y.set("textures." + t.id, base64);
        try {
            y.save(file);
        } catch (java.io.IOException ex) {
            plugin.getLogger().warning("Gagal menyimpan energy/textures.yml: " + ex.getMessage());
        }
        return true;
    }
}

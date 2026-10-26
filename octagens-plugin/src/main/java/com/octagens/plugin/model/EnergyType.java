package com.octagens.plugin.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/** Energy definition loaded from energy.yml. */
public final class EnergyType {
    public final String id;
    public String displayName;
    public String headTexture;
    public double mcuPerSecond;
    public List<String> lore;
    public int customModelData;

    private EnergyType(String id) {
        this.id = id;
    }

    public static EnergyType parse(String key, ConfigurationSection s) {
        EnergyType e = new EnergyType(s.getString("id", key).toLowerCase(Locale.ROOT));
        e.displayName = s.getString("display-name", e.id);
        e.headTexture = s.getString("head-texture", "");
        e.mcuPerSecond = Math.max(0, s.getDouble("mcu-per-second", 0));
        e.lore = s.getStringList("lore");
        e.customModelData = s.getInt("custom-model-data", 0);
        return e;
    }
}

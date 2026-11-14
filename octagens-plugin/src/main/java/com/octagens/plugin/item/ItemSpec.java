package com.octagens.plugin.item;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Declarative description of an item as written in the YAML configs. */
public final class ItemSpec {
    public String ref = "VANILLA:STONE";
    public String name;
    public List<String> lore;
    public Integer customModelData;
    public String itemModel;
    public boolean glow;
    public int amount = 1;
    public boolean hideFlags = true;

    public static ItemSpec ofRef(String ref) {
        ItemSpec s = new ItemSpec();
        s.ref = ref;
        return s;
    }

    /** Accepts a String (item ref) or a ConfigurationSection. Returns null for null input. */
    public static ItemSpec from(Object raw) {
        if (raw == null) return null;
        if (raw instanceof ConfigurationSection cs) return fromSection(cs);
        return ofRef(String.valueOf(raw));
    }

    public static ItemSpec fromSection(ConfigurationSection s) {
        ItemSpec sp = new ItemSpec();
        sp.ref = s.getString("item", s.getString("material", ""));
        if (s.contains("name")) sp.name = s.getString("name");
        if (s.contains("lore")) sp.lore = new ArrayList<>(s.getStringList("lore"));
        if (s.contains("custom-model-data")) {
            int v = s.getInt("custom-model-data", 0);
            if (v > 0) sp.customModelData = v;
        }
        sp.itemModel = s.getString("item-model");
        sp.glow = s.getBoolean("glow", false);
        sp.amount = Math.max(1, s.getInt("amount", 1));
        sp.hideFlags = s.getBoolean("hide-flags", true);
        return sp;
    }

    public ItemSpec copy() {
        ItemSpec c = new ItemSpec();
        c.ref = ref;
        c.name = name;
        c.lore = lore == null ? null : new ArrayList<>(lore);
        c.customModelData = customModelData;
        c.itemModel = itemModel;
        c.glow = glow;
        c.amount = amount;
        c.hideFlags = hideFlags;
        return c;
    }
}

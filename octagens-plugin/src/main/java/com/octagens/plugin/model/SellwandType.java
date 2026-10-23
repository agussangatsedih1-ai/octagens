package com.octagens.plugin.model;

import com.octagens.plugin.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/** Sellwand definition loaded from sellwand.yml. */
public final class SellwandType {
    public final String id;
    public ItemSpec spec;
    public int uses;
    public double multiplier;
    public long cooldownMs;

    private SellwandType(String id) {
        this.id = id;
    }

    public static SellwandType parse(String key, ConfigurationSection s) {
        SellwandType t = new SellwandType(key.toLowerCase(Locale.ROOT));
        t.spec = ItemSpec.fromSection(s);
        if (t.spec.ref.isBlank()) t.spec.ref = "VANILLA:STICK";
        t.spec.amount = 1;
        int u = s.getInt("uses", 100);
        t.uses = u <= 0 ? -1 : u;
        t.multiplier = Math.max(0.0, s.getDouble("multiplier", 1.0));
        t.cooldownMs = Math.max(0, s.getLong("cooldown-ms", 1000));
        return t;
    }
}

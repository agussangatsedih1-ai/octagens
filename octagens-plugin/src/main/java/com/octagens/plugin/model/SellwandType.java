package com.octagens.plugin.model;

import com.octagens.plugin.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;

/** Sellwand definition loaded from sellwand/sellwand.yml. */
public final class SellwandType {
    public final String id;
    public ItemSpec spec;
    public String symbol;
    public int uses;
    public double multiplier;
    public long cooldownMs;
    public double shopPrice = 0;
    public String shopCurrency = "excellenteconomy:denari";
    public boolean shopEnabled = false;
    /** Custom text placeholders from sellwand.yml; keys are exposed as {key}. */
    public final Map<String,String> placeholders = new LinkedHashMap<>();

    private SellwandType(String id) {
        this.id = id;
    }

    public static SellwandType parse(String key, ConfigurationSection s) {
        SellwandType t = new SellwandType(key.toLowerCase(Locale.ROOT));
        t.spec = ItemSpec.fromSection(s);
        if (t.spec.ref.isBlank()) t.spec.ref = "VANILLA:BLAZE_ROD";
        t.spec.amount = 1;
        t.symbol = s.getString("symbol", "\u2726");
        int u = s.getInt("uses", -1);
        t.uses = u <= 0 ? -1 : u;
        // No upper limit; decimals allowed (1.5, 0.25, 1000000...). Invalid/negative values fall back to 1.0.
        double m = s.getDouble("multiplier", 1.0);
        t.multiplier = Double.isFinite(m) && m >= 0 ? m : 1.0;
        t.cooldownMs = Math.max(0, s.getLong("cooldown-ms", 1000));
        t.shopEnabled = s.getBoolean("shop.enabled", false);
        t.shopPrice = Math.max(0, s.getDouble("shop.price", 0));
        t.shopCurrency = s.getString("shop.currency", "excellenteconomy:denari");
        ConfigurationSection custom = s.getConfigurationSection("placeholders");
        if (custom != null) for (String p : custom.getKeys(false)) t.placeholders.put(p.toLowerCase(Locale.ROOT), custom.getString(p, ""));
        return t;
    }
}

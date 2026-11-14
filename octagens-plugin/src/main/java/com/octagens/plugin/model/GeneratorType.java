package com.octagens.plugin.model;

import com.octagens.plugin.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;
import java.util.Locale;

/** Generator definition loaded from generators.yml. */
public final class GeneratorType {
    public final String id;
    public String displayName;
    public List<String> lore;
    public ItemSpec block;
    public double shopPrice;
    public String currency;
    public String sellCurrency;
    public long dropIntervalTicks;
    public ItemSpec dropItem;
    public int dropAmount;
    public double sellPrice;
    public double energyRequirement;
    public int shopSlot;
    public ItemSpec shopIcon;
    public String permission;
    public boolean effects;
    public String particle;
    public int particleCount;
    public String sound;
    public float soundVolume;
    public float soundPitch;
    /** 1-based position after sorting by shop-slot. */
    public int tier;

    private GeneratorType(String id) {
        this.id = id;
    }

    public static GeneratorType parse(String key, ConfigurationSection s, String defaultCurrency) {
        GeneratorType t = new GeneratorType(s.getString("id", key).toLowerCase(Locale.ROOT));
        t.displayName = s.getString("display-name", t.id);
        t.lore = s.getStringList("lore");
        t.block = ItemSpec.from(s.get("block"));
        if (t.block == null || t.block.ref.isBlank()) t.block = ItemSpec.ofRef("VANILLA:STONE");
        t.shopPrice = Math.max(0, s.getDouble("shop-price", 0));
        t.currency = s.getString("currency", defaultCurrency);
        t.sellCurrency = s.getString("sell-currency", t.currency);
        t.dropIntervalTicks = Math.max(1, s.getLong("drop-interval-ticks", 100));
        t.dropItem = ItemSpec.from(s.get("drop-item"));
        if (t.dropItem == null || t.dropItem.ref.isBlank()) t.dropItem = ItemSpec.ofRef("VANILLA:PAPER");
        t.dropAmount = Math.max(1, s.getInt("drop-amount", 1));
        t.sellPrice = Math.max(0, s.getDouble("sell-price", 0));
        t.energyRequirement = Math.max(0, s.getDouble("energy-requirement", 0));
        t.shopSlot = s.getInt("shop-slot", 999);
        t.permission = s.getString("permission", "");
        ConfigurationSection icon = s.getConfigurationSection("shop-icon");
        if (icon != null) {
            t.shopIcon = ItemSpec.fromSection(icon);
            if (t.shopIcon.ref.isBlank()) t.shopIcon.ref = t.block.ref;
        } else if (s.isString("shop-icon")) {
            t.shopIcon = ItemSpec.ofRef(s.getString("shop-icon"));
        }
        ConfigurationSection fx = s.getConfigurationSection("effects");
        t.effects = fx != null && fx.getBoolean("enabled", true);
        if (fx != null) {
            t.particle = fx.getString("particle", "");
            t.particleCount = Math.max(0, fx.getInt("particle-count", 6));
            t.sound = fx.getString("sound", "");
            t.soundVolume = (float) fx.getDouble("sound-volume", 0.5);
            t.soundPitch = (float) fx.getDouble("sound-pitch", 1.0);
        }
        return t;
    }
}

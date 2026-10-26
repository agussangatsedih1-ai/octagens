package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.Bukkit;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Owns every hook. Required hooks are verified in {@link OctaGensPlugin}; optional ones fail soft. */
public final class HookManager {
    private final OctaGensPlugin plugin;
    private final CoreProtectHook coreProtect;
    private VaultCurrency vault;
    private MMOItemsHook mmo;
    private ItemsAdderHook ia;
    private ExcellentEconomyHook ee;
    private final Map<String, Currency> currencies = new HashMap<>();
    private final Set<String> warned = new HashSet<>();

    public HookManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
        this.coreProtect = new CoreProtectHook(plugin);
    }

    /** @return false if CoreProtect is unusable (plugin will disable itself) */
    public boolean setup() {
        if (!coreProtect.init()) return false;
        vault = new VaultCurrency(plugin);
        if (!vault.available()) {
            plugin.getLogger().warning("Vault terpasang tetapi belum ada provider economy; akan dicoba lagi saat dipakai.");
        }
        if (plugin.settings().hookMmo && Bukkit.getPluginManager().isPluginEnabled("MMOItems")) {
            MMOItemsHook h = new MMOItemsHook(plugin);
            if (h.init()) mmo = h;
        }
        if (plugin.settings().hookIa && Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) {
            ItemsAdderHook h = new ItemsAdderHook(plugin);
            if (h.init()) ia = h;
        }
        if (plugin.settings().hookEe && Bukkit.getPluginManager().isPluginEnabled("ExcellentEconomy")) {
            ExcellentEconomyHook h = new ExcellentEconomyHook(plugin);
            if (h.init()) ee = h;
        }
        plugin.getLogger().info("Hook: Vault=ok PlaceholderAPI=ok CoreProtect=ok MMOItems=" + (mmo != null)
                + " ItemsAdder=" + (ia != null) + " ExcellentEconomy=" + (ee != null));
        return true;
    }

    public CoreProtectHook cp() {
        return coreProtect;
    }

    public MMOItemsHook mmo() {
        return mmo;
    }

    public ItemsAdderHook ia() {
        return ia;
    }


    /** @return currency or null when unavailable (logs a warning once per id) */
    public Currency currency(String id) {
        String key = (id == null || id.isBlank() ? plugin.settings().defaultCurrency : id).toLowerCase(Locale.ROOT);
        Currency cur = null;
        if (key.equals("vault")) {
            cur = vault;
        } else if (key.startsWith("excellenteconomy:") && ee != null) {
            cur = currencies.get(key);
            if (cur == null) {
                cur = ee.currency(key.substring("excellenteconomy:".length()));
                if (cur != null) currencies.put(key, cur);
            }
        }
        if (cur == null || !cur.available()) {
            if (warned.add(key)) plugin.getLogger().warning("Currency '" + key + "' tidak tersedia (hook tidak terpasang / id salah). Item yang memakainya dilewati.");
            return null;
        }
        return cur;
    }

    /** One-line status of all hooks, for /octagens version. */
    public String summary() {
        return "Vault \u2714, PlaceholderAPI \u2714, CoreProtect \u2714, MMOItems " + (mmo != null ? "\u2714" : "\u2718")
                + ", ItemsAdder " + (ia != null ? "\u2714" : "\u2718")
                + ", ExcellentEconomy " + (ee != null ? "\u2714" : "\u2718");
    }

    public String fonts(String text) {
        return ia == null ? text : ia.fonts(text);
    }

    public void reload() {
        currencies.clear();
        warned.clear();
    }
}

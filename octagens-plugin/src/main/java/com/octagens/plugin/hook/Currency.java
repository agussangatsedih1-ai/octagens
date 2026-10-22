package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Fmt;
import org.bukkit.OfflinePlayer;

import java.util.Locale;

/** Abstraction over an economy currency (Vault or ExcellentEconomy). */
public abstract class Currency {
    protected final OctaGensPlugin plugin;
    private final String id;

    protected Currency(OctaGensPlugin plugin, String id) {
        this.plugin = plugin;
        this.id = id;
    }

    /** @return full id, e.g. {@code vault} or {@code excellenteconomy:gems} */
    public String id() {
        return id;
    }

    public abstract boolean available();

    public abstract double balance(OfflinePlayer p);

    public boolean has(OfflinePlayer p, double amount) {
        return amount <= 0 || balance(p) + 1e-9 >= amount;
    }

    /** @return true if the money was taken */
    public abstract boolean withdraw(OfflinePlayer p, double amount);

    /** @return true if the money was given */
    public abstract boolean deposit(OfflinePlayer p, double amount);

    /** Formats an amount with the template from config (format.currency-formats). */
    public String format(double amount) {
        String key = id.toLowerCase(Locale.ROOT);
        String tpl = plugin.settings().currencyFormats.get(key);
        if (tpl == null) tpl = plugin.settings().currencyFormats.getOrDefault(key.startsWith("vault") ? "vault" : "default", "{amount} {currency}");
        String name = key.contains(":") ? key.substring(key.indexOf(':') + 1) : key;
        return tpl.replace("{amount}", Fmt.money(amount)).replace("{currency}", name);
    }
}

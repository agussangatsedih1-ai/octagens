package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.UUID;

/**
 * ExcellentEconomy hook via reflection, so the plugin compiles and runs without it.
 * The API class/method names are discovered at runtime (see README: perlu diverifikasi
 * terhadap versi ExcellentEconomy yang terpasang).
 */
public final class ExcellentEconomyHook {
    private static final String[] API_CLASSES = {
            "su.nightexpress.excellenteconomy.api.ExcellentEconomyAPI",
            "su.nightexpress.coinsengine.api.CoinsEngineAPI"
    };

    private final OctaGensPlugin plugin;
    private Class<?> apiClass;
    private Object apiInstance;
    private Method getCurrency, getBalance, addBalance, removeBalance;
    private boolean ready;

    public ExcellentEconomyHook(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public boolean init() {
        for (String cn : API_CLASSES) {
            try {
                apiClass = Class.forName(cn);
                break;
            } catch (ClassNotFoundException ignored) {
            }
        }
        if (apiClass == null) {
            plugin.getLogger().warning("ExcellentEconomy terdeteksi tetapi class API tidak ditemukan; currency excellenteconomy:* dinonaktifkan.");
            return false;
        }
        try {
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration((Class) apiClass);
            if (rsp != null) apiInstance = rsp.getProvider();
        } catch (Throwable ignored) {
        }
        getCurrency = find("getCurrency", 1);
        getBalance = find("getBalance", 2);
        addBalance = firstOf(new String[]{"addBalance", "deposit", "give"}, 3);
        removeBalance = firstOf(new String[]{"removeBalance", "withdraw", "take"}, 3);
        ready = getCurrency != null && getBalance != null && addBalance != null && removeBalance != null;
        if (!ready) {
            plugin.getLogger().warning("API ExcellentEconomy tidak cocok dengan yang diharapkan; currency excellenteconomy:* dinonaktifkan.");
        }
        return ready;
    }

    private Method find(String name, int params) {
        for (Method m : apiClass.getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == params) return m;
        }
        return null;
    }

    private Method firstOf(String[] names, int params) {
        for (String n : names) {
            Method m = find(n, params);
            if (m != null) return m;
        }
        return null;
    }

    private Object target(Method m) {
        return Modifier.isStatic(m.getModifiers()) ? null : apiInstance;
    }

    private Object playerArg(Method m, OfflinePlayer p) {
        Class<?> t = m.getParameterTypes()[0];
        if (t.isAssignableFrom(UUID.class)) return p.getUniqueId();
        if (Player.class.isAssignableFrom(t)) return p.getPlayer();
        return p;
    }

    /** @return currency adapter or null when the currency does not exist / hook not ready */
    public Currency currency(String currencyId) {
        if (!ready) return null;
        try {
            Object cur = getCurrency.invoke(target(getCurrency), currencyId);
            if (cur == null) {
                plugin.getLogger().warning("Currency ExcellentEconomy '" + currencyId + "' tidak ditemukan.");
                return null;
            }
            return new Adapter(currencyId, cur);
        } catch (Exception e) {
            plugin.getLogger().warning("Gagal mengambil currency ExcellentEconomy '" + currencyId + "': " + e.getMessage());
            return null;
        }
    }

    private final class Adapter extends Currency {
        private final Object currency;

        Adapter(String cid, Object currency) {
            super(ExcellentEconomyHook.this.plugin, "excellenteconomy:" + cid);
            this.currency = currency;
        }

        @Override
        public boolean available() {
            return ready;
        }

        @Override
        public double balance(OfflinePlayer p) {
            try {
                Object r = getBalance.invoke(target(getBalance), playerArg(getBalance, p), currency);
                return r instanceof Number n ? n.doubleValue() : 0;
            } catch (Exception e) {
                return 0;
            }
        }

        private boolean call(Method m, OfflinePlayer p, double amount) {
            try {
                Object r = m.invoke(target(m), playerArg(m, p), currency, amount);
                return !(r instanceof Boolean b) || b;
            } catch (Exception e) {
                plugin.getLogger().warning("ExcellentEconomy error: " + e.getMessage());
                return false;
            }
        }

        @Override
        public boolean withdraw(OfflinePlayer p, double amount) {
            if (amount <= 0) return true;
            if (!has(p, amount)) return false;
            return call(removeBalance, p, amount);
        }

        @Override
        public boolean deposit(OfflinePlayer p, double amount) {
            return amount <= 0 || call(addBalance, p, amount);
        }
    }
}

package com.octagens.plugin.hook;

import com.octagens.plugin.OctaGensPlugin;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

/** Vault economy. The provider is resolved lazily because economy plugins may enable after us. */
public final class VaultCurrency extends Currency {
    private Economy economy;

    public VaultCurrency(OctaGensPlugin plugin) {
        super(plugin, "vault");
    }

    private Economy eco() {
        if (economy == null) {
            RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
            if (rsp != null) economy = rsp.getProvider();
        }
        return economy;
    }

    @Override
    public boolean available() {
        return eco() != null;
    }

    @Override
    public double balance(OfflinePlayer p) {
        Economy e = eco();
        return e == null ? 0 : e.getBalance(p);
    }

    @Override
    public boolean withdraw(OfflinePlayer p, double amount) {
        Economy e = eco();
        if (e == null) return false;
        if (amount <= 0) return true;
        return e.withdrawPlayer(p, amount).transactionSuccess();
    }

    @Override
    public boolean deposit(OfflinePlayer p, double amount) {
        Economy e = eco();
        if (e == null) return false;
        if (amount <= 0) return true;
        return e.depositPlayer(p, amount).transactionSuccess();
    }
}

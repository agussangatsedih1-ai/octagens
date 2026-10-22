package com.octagens.plugin.config;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.util.Fmt;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Typed snapshot of config.yml. Call {@link #load()} again on reload. */
public final class Settings {
    private final OctaGensPlugin plugin;

    public boolean debug;
    public String defaultCurrency;

    public String dbType;
    public String sqliteFile;
    public String myHost, myDatabase, myUser, myPass;
    public int myPort, myPool;
    public boolean mySsl;

    public boolean moneyShort;
    public final Map<String, String> currencyFormats = new HashMap<>();

    public int defaultGeneratorSlots, defaultEnergySlots;

    public boolean onlyWhenOwnerOnline, requireFreeSpace, dropInvulnerable, pickupRequireEmptyHand, manageGui;
    public int maxStoredDrops, tickInterval, fullRecheckTicks;
    public Set<String> blacklistedWorlds = new HashSet<>();

    public boolean energyRequired, distance2D, sharedEnergy;
    public double minEfficiency, maxDistance;

    public boolean holoEnabled;
    public double holoHeight;
    public int holoInterval;
    public float holoViewRange;
    public List<String> holoLines = new ArrayList<>();
    public final Map<String, String> holoStatus = new HashMap<>();

    public long sellCooldownMs;
    public boolean sellGui, shopConfirm;
    public final Map<String, Double> sellMultiplierDefault = new HashMap<>();

    public long guiClickCooldownMs;
    public double largeTransaction;
    public boolean protPiston, protExplosion, protDispenser, protFluid, allowHopperPickup;

    public boolean logSell, logPurchase;
    public String logFile;

    public boolean cpPlacement, cpRemoval, cpOregen, cpTransactions;
    public boolean hookMmo, hookIa, hookEe;
    public boolean verifyBlocks;

    public Settings(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();
        debug = c.getBoolean("general.debug", false);
        defaultCurrency = c.getString("general.default-currency", "vault");

        dbType = c.getString("database.type", "SQLITE").toUpperCase(Locale.ROOT);
        sqliteFile = c.getString("database.sqlite-file", "octagens.db");
        myHost = c.getString("database.mysql.host", "localhost");
        myPort = c.getInt("database.mysql.port", 3306);
        myDatabase = c.getString("database.mysql.database", "octagens");
        myUser = c.getString("database.mysql.username", "root");
        myPass = c.getString("database.mysql.password", "");
        mySsl = c.getBoolean("database.mysql.use-ssl", false);
        myPool = Math.max(2, c.getInt("database.mysql.pool-size", 8));

        moneyShort = c.getBoolean("format.money-short", true);
        Fmt.moneyShort = moneyShort;
        currencyFormats.clear();
        ConfigurationSection cf = c.getConfigurationSection("format.currency-formats");
        if (cf != null) for (String k : cf.getKeys(false)) currencyFormats.put(k.toLowerCase(Locale.ROOT), cf.getString(k, "{amount}"));

        defaultGeneratorSlots = c.getInt("slots.default-generators", 10);
        defaultEnergySlots = c.getInt("slots.default-energy", 10);

        onlyWhenOwnerOnline = c.getBoolean("generators.only-when-owner-online", false);
        maxStoredDrops = Math.max(1, c.getInt("generators.max-stored-drops", 512));
        requireFreeSpace = c.getBoolean("generators.require-free-space-above", true);
        dropInvulnerable = c.getBoolean("generators.drop-invulnerable", true);
        pickupRequireEmptyHand = c.getBoolean("generators.pickup-require-empty-hand", true);
        manageGui = c.getBoolean("generators.manage-gui", true);
        tickInterval = Math.max(1, c.getInt("generators.tick-interval-ticks", 10));
        fullRecheckTicks = Math.max(1, c.getInt("generators.full-recheck-ticks", 40));
        blacklistedWorlds = new HashSet<>();
        for (String w : c.getStringList("generators.blacklisted-worlds")) blacklistedWorlds.add(w.toLowerCase(Locale.ROOT));

        energyRequired = c.getBoolean("energy.required", true);
        minEfficiency = Math.max(0, Math.min(1, c.getDouble("energy.min-efficiency", 0.0)));
        maxDistance = Math.max(1, c.getDouble("energy.max-distance", 64));
        distance2D = c.getString("energy.distance-mode", "3D").equalsIgnoreCase("2D");
        sharedEnergy = c.getBoolean("energy.shared-energy", false);

        holoEnabled = c.getBoolean("hologram.enabled", true);
        holoHeight = c.getDouble("hologram.height", 1.9);
        holoInterval = Math.max(5, c.getInt("hologram.update-interval-ticks", 40));
        holoViewRange = (float) Math.max(0.1, c.getDouble("hologram.view-range", 0.6));
        holoLines = new ArrayList<>(c.getStringList("hologram.lines"));
        holoStatus.clear();
        ConfigurationSection hs = c.getConfigurationSection("hologram.status");
        if (hs != null) for (String k : hs.getKeys(false)) holoStatus.put(k.toUpperCase(Locale.ROOT), hs.getString(k, k));

        sellCooldownMs = Math.max(0, c.getLong("sell.cooldown-ms", 1000));
        sellGui = c.getBoolean("sell.sell-gui", false);
        shopConfirm = c.getBoolean("shop.confirm-gui", true);

        guiClickCooldownMs = Math.max(0, c.getLong("security.gui-click-cooldown-ms", 150));
        largeTransaction = c.getDouble("security.large-transaction-threshold", 1_000_000_000d);
        protPiston = c.getBoolean("security.protection.block-piston", true);
        protExplosion = c.getBoolean("security.protection.block-explosion", true);
        protDispenser = c.getBoolean("security.protection.block-dispenser", true);
        protFluid = c.getBoolean("security.protection.block-fluid", true);
        allowHopperPickup = c.getBoolean("security.protection.allow-hopper-pickup", true);

        logSell = c.getBoolean("logging.sell-log", true);
        logPurchase = c.getBoolean("logging.purchase-log", true);
        logFile = c.getString("logging.file", "sell-log.txt");

        cpPlacement = c.getBoolean("coreprotect.log-placement", false);
        cpRemoval = c.getBoolean("coreprotect.log-removal", true);
        cpOregen = c.getBoolean("coreprotect.log-oregen", true);
        cpTransactions = c.getBoolean("coreprotect.log-transactions", true);

        hookMmo = c.getBoolean("hooks.mmoitems", true);
        hookIa = c.getBoolean("hooks.itemsadder", true);
        hookEe = c.getBoolean("hooks.excellenteconomy", true);
        verifyBlocks = c.getBoolean("performance.verify-blocks", true);
    }
}

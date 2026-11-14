package com.octagens.plugin;

import com.octagens.api.OctaGensAPI;
import com.octagens.plugin.command.OctaCommand;
import com.octagens.plugin.config.ContentRegistry;
import com.octagens.plugin.config.Messages;
import com.octagens.plugin.config.Settings;
import com.octagens.plugin.gui.GuiManager;
import com.octagens.plugin.hook.HookManager;
import com.octagens.plugin.hook.OctaPlaceholders;
import com.octagens.plugin.item.ItemService;
import com.octagens.plugin.listener.ChunkListener;
import com.octagens.plugin.listener.GeneratorListener;
import com.octagens.plugin.listener.GuiListener;
import com.octagens.plugin.listener.OreGenListener;
import com.octagens.plugin.listener.PlayerListener;
import com.octagens.plugin.listener.ProtectionListener;
import com.octagens.plugin.manager.EnergyManager;
import com.octagens.plugin.manager.PlayerDataManager;
import com.octagens.plugin.manager.DailyMissionManager;
import com.octagens.plugin.manager.BoosterManager;
import com.octagens.plugin.manager.GeneratorManager;
import com.octagens.plugin.manager.HologramManager;
import com.octagens.plugin.manager.OreGenManager;
import com.octagens.plugin.manager.SellManager;
import com.octagens.plugin.manager.ShopManager;
import com.octagens.plugin.manager.SlotManager;
import com.octagens.plugin.manager.TradeManager;
import com.octagens.plugin.manager.TransactionLog;
import com.octagens.plugin.manager.WeekendManager;
import com.octagens.plugin.storage.Database;
import com.octagens.plugin.util.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * OctaGens main class. NOTE: this class must not reference Vault / PlaceholderAPI / CoreProtect types
 * directly, so the dependency check below can run (and fail gracefully) before those classes are touched.
 */
public final class OctaGensPlugin extends JavaPlugin {
    private Settings settings;
    private Messages messages;
    private Keys keys;
    private ContentRegistry registry;
    private HookManager hooks;
    private ItemService items;
    private Database database;
    private TransactionLog txLog;
    private SlotManager slots;
    private EnergyManager energy;
    private GeneratorManager generators;
    private WeekendManager weekend;
    private HologramManager holograms;
    private OreGenManager ore;
    private SellManager sell;
    private ShopManager shop;
    private GuiManager guis;
    private TradeManager trade;
    private OctaPlaceholders placeholders;
    private PlayerDataManager playerData;
    private DailyMissionManager missions;
    private BoosterManager boosters;
    private com.octagens.plugin.manager.SellwandShopManager sellwandShop;
    private boolean ready;

    @Override
    public void onEnable() {
        printBanner();
        if (!checkRequired()) return;
        com.octagens.plugin.config.LegacyMigrator.run(this);
        saveDefaultConfig();
        settings = new Settings(this);
        settings.load();
        messages = new Messages(this);
        messages.load();
        keys = new Keys(this);

        hooks = new HookManager(this);
        if (!hooks.setup()) {
            fatal("CoreProtect terpasang tetapi API-nya tidak bisa dipakai (butuh CoreProtect dengan API v10+ dan API diaktifkan di config CoreProtect).");
            return;
        }
        registry = new ContentRegistry(this);
        registry.load();
        items = new ItemService(this);
        txLog = new TransactionLog(this);
        slots = new SlotManager(this);
        energy = new EnergyManager(this);
        generators = new GeneratorManager(this);
        weekend = new WeekendManager(this);
        holograms = new HologramManager(this);
        ore = new OreGenManager(this);
        sell = new SellManager(this);
        shop = new ShopManager(this);
        trade = new TradeManager(this);
        guis = new GuiManager(this);
        guis.load();
        playerData = new PlayerDataManager(this);
        missions = new DailyMissionManager(this);
        boosters = new BoosterManager(this);
        sellwandShop = new com.octagens.plugin.manager.SellwandShopManager(this);
        missions.load();
        boosters.load();

        database = new Database(this);
        database.init().thenCompose(v -> database.loadAll()).whenComplete((result, err) -> {
            try {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (err != null) {
                        getLogger().severe("Database gagal: " + (err.getCause() != null ? err.getCause().getMessage() : err.getMessage()));
                        getServer().getPluginManager().disablePlugin(this);
                        return;
                    }
                    finishStartup(result);
                });
            } catch (RuntimeException ignored) {
                // plugin disabled while loading
            }
        });
    }

    @SuppressWarnings("deprecation")
    private void printBanner() {
        String[] logo = {
                "  ___   ____ ",
                " / _ \\ / ___|",
                "| | | | |  _ ",
                "| |_| | |_| |",
                " \\___/ \\____|"
        };
        String[] text = {
                "",
                "",
                "\u00a7fOctaGens \u00a7bv" + getDescription().getVersion(),
                "\u00a77Server Version: \u00a7f" + Bukkit.getVersion(),
                ""
        };
        var console = Bukkit.getConsoleSender();
        console.sendMessage("");
        for (int i = 0; i < logo.length; i++) {
            console.sendMessage("\u00a7d" + logo[i] + "  " + text[i]);
        }
        console.sendMessage("");
    }

    private boolean checkRequired() {
        PluginManager pm = getServer().getPluginManager();
        List<String> missing = new ArrayList<>();
        for (String n : new String[]{"Vault", "PlaceholderAPI", "CoreProtect"}) {
            if (!pm.isPluginEnabled(n)) missing.add(n);
        }
        if (missing.isEmpty()) return true;
        fatal("Plugin wajib tidak ditemukan / tidak aktif: " + String.join(", ", missing)
                + ". Pasang plugin tersebut (Vault juga butuh plugin economy) lalu restart server.");
        return false;
    }

    private void fatal(String msg) {
        getLogger().severe("==================================================");
        getLogger().severe("OctaGens dimatikan: " + msg);
        getLogger().severe("==================================================");
        getServer().getPluginManager().disablePlugin(this);
    }

    private void finishStartup(Database.LoadResult data) {
        slots.load(data.players());
        trade.load(data.trades());
        energy.loadPools(data.players());
        energy.load(data.energies());
        generators.load(data.generators());
        for (World w : Bukkit.getWorlds()) {
            for (Chunk c : w.getLoadedChunks()) energy.onChunkLoad(c);
        }
        generators.activateLoadedChunks();

        weekend.reload();
        weekend.start();
        boosters.load();
        boosters.start();
        missions.load();
        missions.start();
        playerData.start();
        for (Player online : Bukkit.getOnlinePlayers()) playerData.save(online);
        generators.start();
        holograms.start();
        energy.startAutosave();

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new GeneratorListener(this), this);
        pm.registerEvents(new ProtectionListener(this), this);
        pm.registerEvents(new OreGenListener(this), this);
        pm.registerEvents(new GuiListener(this), this);
        pm.registerEvents(new com.octagens.plugin.listener.SellwandListener(this), this);
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new ChunkListener(this), this);
        pm.registerEvents(missions, this);
        pm.registerEvents(boosters, this);
        pm.registerEvents(sellwandShop, this);

        OctaCommand cmd = new OctaCommand(this);
        if (getCommand("octagens") != null) {
            getCommand("octagens").setExecutor(cmd);
            getCommand("octagens").setTabCompleter(cmd);
        }

        placeholders = new OctaPlaceholders(this);
        placeholders.register();
        if (getCommand("mpc") != null) {
            com.octagens.plugin.command.MpcCommand mpc = new com.octagens.plugin.command.MpcCommand(this);
            getCommand("mpc").setExecutor(mpc);
            getCommand("mpc").setTabCompleter(mpc);
        }
        if (getCommand("dailymissions") != null) {
            getCommand("dailymissions").setExecutor((sender, command, label, args) -> {
                if (!(sender instanceof Player p)) { messages.send(sender, "player-only"); return true; }
                missions.open(p); return true;
            });
        }
        OctaGensAPI.register(new OctaGensAPIImpl(this));
        ready = true;
        getLogger().info("OctaGens aktif: " + generators.total() + " generator, " + energy.total() + " energy dimuat ("
                + settings.dbType + ").");
    }

    @Override
    public void onDisable() {
        if (playerData != null) {
            for (Player p : Bukkit.getOnlinePlayers()) playerData.save(p);
            playerData.stop();
        }
        if (missions != null) missions.stop();
        if (boosters != null) boosters.stop();
        ready = false;
        OctaGensAPI.unregister();
        if (placeholders != null) placeholders.unregister();
        if (energy != null) {
            energy.stopAutosave();
            energy.saveAll();
        }
        if (generators != null) generators.stop();
        if (weekend != null) weekend.stop();
        if (holograms != null) {
            holograms.stop();
            holograms.removeAll();
        }
        if (guis != null) guis.closeAll();
        if (database != null) database.close();
        if (txLog != null) txLog.close();
    }

    /**
     * Reloads every config file without a restart (database settings need a restart).
     * Progress is printed to the console step by step.
     * @return the time it took in milliseconds
     */
    public long reloadAll() {
        long start = System.currentTimeMillis();
        getLogger().info("Reloading OctaGens...");
        reloadStep("Config", () -> settings.load());
        reloadStep("Messages", () -> messages.load());
        reloadStep("Content (generators, collectors, shop, ore generator)", () -> {
            registry.load();
            hooks.reload();
            items.clearCache();
        });
        reloadStep("Magicule Power", () -> {
            energy.refreshTypes();
            energy.startAutosave();
            slots.clearAll();
        });
        reloadStep("Generator", () -> generators.start());
        reloadStep("Weekend Bonus", () -> {
            weekend.reload();
            weekend.start();
        });
        reloadStep("Hologram", () -> holograms.reloadAll());
        reloadStep("Daily Missions", () -> { missions.load(); missions.start(); });
        reloadStep("Boosters", () -> { boosters.load(); boosters.start(); });
        reloadStep("GUI", () -> guis.load());
        long took = System.currentTimeMillis() - start;
        getLogger().info("Reload complete in " + took + "ms.");
        return took;
    }

    private void reloadStep(String label, Runnable step) {
        long t = System.currentTimeMillis();
        getLogger().info(" \u00bb Reloading " + label + "...");
        try {
            step.run();
            getLogger().info("   done (" + (System.currentTimeMillis() - t) + "ms)");
        } catch (Exception ex) {
            getLogger().severe("   FAILED: " + ex);
        }
    }

    public boolean canEdit(Player p, UUID owner) {
        return p.getUniqueId().equals(owner) || p.hasPermission("octagens.edit");
    }

    public boolean isOctaBlock(Block b) {
        return ready && (generators.get(b) != null || energy.get(b) != null);
    }

    public boolean isReady() { return ready; }
    public Settings settings() { return settings; }
    public Messages messages() { return messages; }
    public Keys keys() { return keys; }
    public ContentRegistry registry() { return registry; }
    public HookManager hooks() { return hooks; }
    public ItemService items() { return items; }
    public Database database() { return database; }
    public TransactionLog txLog() { return txLog; }
    public SlotManager slots() { return slots; }
    public EnergyManager energy() { return energy; }
    public GeneratorManager generators() { return generators; }
    public WeekendManager weekend() { return weekend; }
    public HologramManager holograms() { return holograms; }
    public OreGenManager ore() { return ore; }
    public SellManager sell() { return sell; }
    public ShopManager shop() { return shop; }
    public GuiManager guis() { return guis; }
    public TradeManager trade() { return trade; }
    public PlayerDataManager playerData() { return playerData; }
    public DailyMissionManager missions() { return missions; }
    public BoosterManager boosters() { return boosters; }
    public com.octagens.plugin.manager.SellwandShopManager sellwandShop() { return sellwandShop; }
}

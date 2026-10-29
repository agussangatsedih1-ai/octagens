package com.octagens.plugin.gui;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.model.EnergyRecipe;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.model.PlacedGenerator;
import com.octagens.plugin.manager.SellManager;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import com.octagens.plugin.util.Yml;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntConsumer;

/** Builds and opens every GUI from guis/*.yml. */
public final class GuiManager {
    private static final List<String> IDS = List.of("main", "shop", "energyshop", "confirm", "manage", "sell", "trade", "tradecategory");

    private final OctaGensPlugin plugin;
    private final Map<String, GuiTemplate> templates = new HashMap<>();

    public GuiManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        templates.clear();
        for (String id : IDS) {
            YamlConfiguration y = Yml.load(plugin, "guis/" + id + ".yml", false);
            templates.put(id, GuiTemplate.parse(id, y));
        }
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(plugin, r);
    }

    // ------------------------------------------------------------------ dispatch

    public void open(Player p, String id, Map<String, Object> data) {
        switch (id.toLowerCase(Locale.ROOT)) {
            case "main" -> openMain(p);
            case "shop" -> openShop(p, data.get("page") instanceof Integer i ? i : 1);
            case "energyshop", "magiculeshop" -> openEnergyShop(p, data.get("page") instanceof Integer i ? i : 1);
            case "sell" -> openSell(p);
            case "trade" -> openTrade(p, data.get("page") instanceof Integer i ? i : 1);
            case "tradecategory" -> openTradeCategory(p, String.valueOf(data.getOrDefault("category", "")), data.get("page") instanceof Integer i ? i : 1);
            case "confirm" -> {
                if (data.get("kind") != null && data.get("id") != null) {
                    openConfirm(p, String.valueOf(data.get("kind")), String.valueOf(data.get("id")),
                            String.valueOf(data.getOrDefault("back", "main")), data.get("backPage") instanceof Integer i ? i : 1);
                }
            }
            case "manage" -> {
                if (data.get("gen") instanceof PlacedGenerator g) openManage(p, g);
            }
            default -> plugin.getLogger().warning("GUI tidak dikenal: " + id);
        }
    }

    public void closeAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof OctaGui) p.closeInventory();
        }
    }

    // ------------------------------------------------------------------ building blocks

    private OctaGui create(Player p, GuiTemplate t, Map<String, String> repl, Map<String, Object> data) {
        OctaGui g = new OctaGui(t, p);
        g.data.putAll(data);
        String title = plugin.items().fill(t.title, repl, p);
        title = plugin.hooks().fonts(Text.color(title));
        g.init(title);
        if (t.filler != null) {
            ItemStack f = plugin.items().build(t.filler, repl, p);
            if (f != null) for (int i = 0; i < g.size(); i++) g.setRaw(i, f);
        }
        return g;
    }

    private Map<String, String> baseRepl(Player p) {
        Map<String, String> r = new HashMap<>();
        r.put("player", p.getName());
        r.put("generators", String.valueOf(plugin.generators().count(p.getUniqueId())));
        r.put("energies", String.valueOf(plugin.energy().count(p.getUniqueId())));
        mcuRepl(p, r);
        return r;
    }

    /** Magicule placeholders: {mcu} {mcu_max} {regen} {level} {next_max} {price} {price10} {limit} {remaining}. */
    private void mcuRepl(Player p, Map<String, String> r) {
        com.octagens.plugin.manager.EnergyManager em = plugin.energy();
        com.octagens.plugin.model.EnergyPool pool = em.pool(p.getUniqueId());
        int level = em.level(pool);
        int remaining = em.remainingLevels(pool);
        com.octagens.plugin.hook.Currency c = plugin.hooks().currency(plugin.settings().upgradeCurrency);
        r.put("mcu", Fmt.number(Math.floor(pool.current)));
        r.put("mcu_max", Fmt.number(pool.max));
        r.put("regen", Fmt.number(em.regenRate(p.getUniqueId())));
        r.put("level", String.valueOf(level));
        r.put("limit", Fmt.number(plugin.settings().mcuMaxLimit));
        r.put("step", Fmt.number(plugin.settings().upgradeStep));
        r.put("remaining", String.valueOf(remaining));
        if (remaining <= 0) {
            r.put("next_max", Fmt.number(pool.max));
            r.put("price", plugin.messages().raw("upgrade-maxed"));
            r.put("price10", plugin.messages().raw("upgrade-maxed"));
        } else {
            double one = em.upgradeCost(level, 1);
            double ten = em.upgradeCost(level, Math.min(10, remaining));
            r.put("next_max", Fmt.number(em.maxAfter(pool.max, 1)));
            r.put("price", c != null ? c.format(one) : Fmt.money(one));
            r.put("price10", c != null ? c.format(ten) : Fmt.money(ten));
        }
    }

    private void addStatic(OctaGui g, GuiTemplate t, Player p, Map<String, String> repl) {
        for (GuiTemplate.Item it : t.items) {
            if (it.function != null) continue;
            ItemStack st = plugin.items().buildSafe(it.spec, repl, p);
            for (int slot : it.slots) g.set(slot, st, click -> runItem(g, p, it, click));
        }
    }

    private void runItem(OctaGui g, Player p, GuiTemplate.Item it, ClickType click) {
        List<String> acts = new ArrayList<>(it.actions);
        if (click.isLeftClick()) acts.addAll(it.left);
        if (click.isRightClick()) acts.addAll(it.right);
        runActions(g, p, acts);
    }

    private void dynamicItems(OctaGui g, GuiTemplate t, Player p, String function, ItemStack override, Map<String, String> repl) {
        for (GuiTemplate.Item it : t.items) {
            if (!function.equals(it.function)) continue;
            ItemStack st = override != null ? override : plugin.items().buildSafe(it.spec, repl, p);
            for (int slot : it.slots) g.set(slot, st, click -> runItem(g, p, it, click));
        }
    }

    private int[] pageRange(GuiTemplate t, int total, int page) {
        int per = Math.max(1, t.contentSlots.length);
        int pages = Math.max(1, (total + per - 1) / per);
        int pg = Math.min(Math.max(1, page), pages);
        int start = (pg - 1) * per;
        return new int[]{pg, pages, start, Math.min(total, start + per)};
    }

    private void addNav(OctaGui g, GuiTemplate t, Player p, Map<String, String> repl, int pg, int pages, IntConsumer opener) {
        for (GuiTemplate.Item it : t.items) {
            boolean next = "NEXT".equals(it.function);
            boolean prev = "PREV".equals(it.function);
            if (!(next || prev)) continue;
            if (next && pg >= pages) continue;
            if (prev && pg <= 1) continue;
            ItemStack st = plugin.items().buildSafe(it.spec, repl, p);
            int target = next ? pg + 1 : pg - 1;
            for (int slot : it.slots) g.set(slot, st, click -> later(() -> opener.accept(target)));
        }
    }

    private void show(Player p, OctaGui g) {
        p.openInventory(g.getInventory());
    }

    // ------------------------------------------------------------------ GUIs

    public void openMain(Player p) {
        GuiTemplate t = templates.get("main");
        Map<String, String> repl = baseRepl(p);
        repl.put("generators_max", limitText(plugin.slots().generatorLimit(p)));
        repl.put("energy_max", limitText(plugin.slots().energyLimit(p)));
        OctaGui g = create(p, t, repl, Map.of());
        addStatic(g, t, p, repl);
        for (GuiTemplate.Item it : t.items) {
            if (!"SELF_HEAD".equals(it.function)) continue;
            ItemStack head = plugin.items().buildSafe(it.spec, repl, p);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta sm) {
                try {
                    sm.setOwnerProfile(p.getPlayerProfile()); // the viewer's own skin: always valid
                    head.setItemMeta(sm);
                } catch (Exception ignored) {
                    // keep the plain head
                }
            }
            for (int slot : it.slots) g.set(slot, head, click -> runItem(g, p, it, click));
        }
        dynamicItems(g, t, p, "UPGRADE", null, repl);
        show(p, g);
    }

    private String limitText(int v) {
        return v == Integer.MAX_VALUE ? plugin.messages().get("placeholder-unlimited") : String.valueOf(v);
    }

    public void openShop(Player p, int page) {
        GuiTemplate t = templates.get("shop");
        List<GeneratorType> list = plugin.registry().generators();
        int[] r = pageRange(t, list.size(), page);
        Map<String, String> repl = baseRepl(p);
        repl.put("page", String.valueOf(r[0]));
        repl.put("pages", String.valueOf(r[1]));
        OctaGui g = create(p, t, repl, Map.of("page", r[0]));
        for (int i = r[2]; i < r[3]; i++) {
            GeneratorType gt = list.get(i);
            int slot = t.contentSlots[i - r[2]];
            int pg = r[0];
            g.set(slot, plugin.shop().shopIcon(gt, p, t.entryLore), click -> {
                if (plugin.settings().shopConfirm) {
                    later(() -> openConfirm(p, "generator", gt.id, "shop", pg));
                } else {
                    plugin.shop().buyGenerator(p, gt);
                    later(() -> openShop(p, pg));
                }
            });
        }
        addStatic(g, t, p, repl);
        addNav(g, t, p, repl, r[0], r[1], pg -> openShop(p, pg));
        show(p, g);
    }

    public void openEnergyShop(Player p, int page) {
        GuiTemplate t = templates.get("energyshop");
        List<EnergyRecipe> list = plugin.registry().recipes();
        int[] r = pageRange(t, list.size(), page);
        Map<String, String> repl = baseRepl(p);
        repl.put("page", String.valueOf(r[0]));
        repl.put("pages", String.valueOf(r[1]));
        OctaGui g = create(p, t, repl, Map.of("page", r[0]));
        for (int i = r[2]; i < r[3]; i++) {
            EnergyRecipe rec = list.get(i);
            int slot = t.contentSlots[i - r[2]];
            int pg = r[0];
            g.set(slot, plugin.shop().energyIcon(rec, p, t.entryLore), click -> {
                if (plugin.settings().shopConfirm) {
                    later(() -> openConfirm(p, "energy", rec.energyId, "energyshop", pg));
                } else {
                    plugin.shop().buyEnergy(p, rec);
                    later(() -> openEnergyShop(p, pg));
                }
            });
        }
        addStatic(g, t, p, repl);
        dynamicItems(g, t, p, "UPGRADE", null, repl);
        addNav(g, t, p, repl, r[0], r[1], pg -> openEnergyShop(p, pg));
        show(p, g);
    }

    public void openConfirm(Player p, String kind, String id, String back, int backPage) {
        GuiTemplate t = templates.get("confirm");
        Map<String, String> repl = baseRepl(p);
        ItemStack preview;
        if (kind.equals("generator")) {
            GeneratorType gt = plugin.registry().generator(id);
            if (gt == null) return;
            repl.put("name", gt.displayName);
            preview = plugin.shop().shopIcon(gt, p, t.entryLore);
        } else {
            EnergyRecipe rec = plugin.registry().recipe(id);
            if (rec == null) return;
            repl.put("name", plugin.registry().energy(id).displayName);
            preview = plugin.shop().energyIcon(rec, p, t.entryLore);
        }
        Map<String, Object> data = new HashMap<>();
        data.put("kind", kind);
        data.put("id", id);
        data.put("back", back);
        data.put("backPage", backPage);
        OctaGui g = create(p, t, repl, data);
        addStatic(g, t, p, repl);
        dynamicItems(g, t, p, "PREVIEW", preview, repl);
        show(p, g);
    }

    public void openManage(Player p, PlacedGenerator gen) {
        GeneratorType gt = plugin.registry().generator(gen.typeId);
        if (gt == null) return;
        GuiTemplate t = templates.get("manage");
        Map<String, String> repl = baseRepl(p);
        repl.putAll(plugin.items().generatorRepl(gt));
        double eff = plugin.energy().efficiency(gen, gt);
        repl.put("owner", gen.ownerName == null ? "?" : gen.ownerName);
        repl.put("supply", Fmt.number(plugin.energy().supply(gen)));
        repl.put("pool", repl.get("mcu"));
        repl.put("pool_max", repl.get("mcu_max"));
        repl.put("required", Fmt.number(gt.energyRequirement));
        repl.put("efficiency", Fmt.percent(eff));
        repl.put("status", plugin.messages().get("holo-status-" + gen.status.name().toLowerCase()));
        repl.put("x", String.valueOf(gen.key.x()));
        repl.put("y", String.valueOf(gen.key.y()));
        repl.put("z", String.valueOf(gen.key.z()));
        repl.put("world", gen.key.world());
        repl.put("multiplier", plugin.weekend().displayMultiplier());
        com.octagens.plugin.model.GeneratorType next = plugin.generators().nextType(gen);
        if (next == null) {
            for (String k : new String[]{"name", "tier", "drop_name", "interval", "amount", "energy", "price", "sell_price"}) repl.put("next_" + k, "-");
            repl.put("upgrade_price", plugin.messages().raw("upgrade-maxed"));
        } else {
            for (Map.Entry<String, String> en : plugin.items().generatorRepl(next).entrySet()) repl.put("next_" + en.getKey(), en.getValue());
            double up = plugin.generators().upgradePrice(gt, next);
            com.octagens.plugin.hook.Currency uc = plugin.hooks().currency(next.currency);
            repl.put("upgrade_price", uc != null ? uc.format(up) : Fmt.money(up));
        }
        Map<String, Object> data = new HashMap<>();
        data.put("gen", gen);
        OctaGui g = create(p, t, repl, data);
        addStatic(g, t, p, repl);
        dynamicItems(g, t, p, "INFO", null, repl);
        dynamicItems(g, t, p, "UPGRADE_GENERATOR", null, repl);
        show(p, g);
    }

    public void openSell(Player p) {
        GuiTemplate t = templates.get("sell");
        SellManager.Preview pv = plugin.sell().preview(p);
        Map<String, String> repl = baseRepl(p);
        repl.put("items", String.valueOf(pv.items()));
        repl.put("total", pv.total());
        repl.put("multiplier", Fmt.number(pv.multiplier()));
        repl.put("lines", pv.empty() ? plugin.messages().raw("sell-gui-empty") : String.join("\n", pv.lines()));
        OctaGui g = create(p, t, repl, Map.of());
        addStatic(g, t, p, repl);
        dynamicItems(g, t, p, "SUMMARY", null, repl);
        show(p, g);
    }

    // ------------------------------------------------------------------ trading (/octagens trade)

    public void openTrade(Player p, int page) {
        GuiTemplate t = templates.get("trade");
        List<com.octagens.plugin.model.TradeCategory> list = plugin.registry().tradeCategories();
        int[] r = pageRange(t, list.size(), page);
        Map<String, String> repl = baseRepl(p);
        repl.put("page", String.valueOf(r[0]));
        repl.put("pages", String.valueOf(r[1]));
        OctaGui g = create(p, t, repl, Map.of("page", r[0]));
        for (int i = r[2]; i < r[3]; i++) {
            com.octagens.plugin.model.TradeCategory cat = list.get(i);
            int slot = t.contentSlots[i - r[2]];
            g.set(slot, plugin.trade().categoryIcon(p, cat, t.entryLore), click -> {
                if (!plugin.trade().categoryAllowed(p, cat)) {
                    plugin.messages().send(p, "trade-locked");
                    return;
                }
                later(() -> openTradeCategory(p, cat.id, 1));
            });
        }
        addStatic(g, t, p, repl);
        addNav(g, t, p, repl, r[0], r[1], pg -> openTrade(p, pg));
        show(p, g);
    }

    public void openTradeCategory(Player p, String categoryId, int page) {
        com.octagens.plugin.model.TradeCategory cat = plugin.registry().tradeCategory(categoryId);
        if (cat == null) {
            plugin.messages().send(p, "trade-unknown-category", "category", categoryId);
            return;
        }
        GuiTemplate t = templates.get("tradecategory");
        List<com.octagens.plugin.model.Trade> list = cat.trades;
        int[] r = pageRange(t, list.size(), page);
        Map<String, String> repl = baseRepl(p);
        repl.put("page", String.valueOf(r[0]));
        repl.put("pages", String.valueOf(r[1]));
        repl.put("category", cat.displayName);
        Map<String, Object> data = new HashMap<>();
        data.put("page", r[0]);
        data.put("category", cat.id);
        OctaGui g = create(p, t, repl, data);
        for (int i = r[2]; i < r[3]; i++) {
            com.octagens.plugin.model.Trade trade = list.get(i);
            int slot = t.contentSlots[i - r[2]];
            int pg = r[0];
            g.set(slot, plugin.trade().icon(p, cat, trade, t.entryLore), click -> {
                plugin.trade().execute(p, cat, trade);
                later(() -> openTradeCategory(p, cat.id, pg));
            });
        }
        addStatic(g, t, p, repl);
        addNav(g, t, p, repl, r[0], r[1], pg -> openTradeCategory(p, cat.id, pg));
        show(p, g);
    }

    // ------------------------------------------------------------------ actions

    public void runActions(OctaGui gui, Player p, List<String> actions) {
        for (String raw : actions) {
            String a = raw == null ? "" : raw.trim();
            if (a.isEmpty()) continue;
            String tag = "";
            String arg = a;
            if (a.startsWith("[")) {
                int e = a.indexOf(']');
                if (e > 0) {
                    tag = a.substring(1, e).toLowerCase(Locale.ROOT);
                    arg = a.substring(e + 1).trim();
                }
            }
            arg = plugin.items().fill(arg, Map.of("player", p.getName()), p);
            switch (tag) {
                case "close" -> later(p::closeInventory);
                case "message" -> p.sendMessage(Text.color(arg));
                case "sound" -> playSound(p, arg);
                case "console" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg.startsWith("/") ? arg.substring(1) : arg);
                case "player" -> p.performCommand(arg.startsWith("/") ? arg.substring(1) : arg);
                case "buy" -> doBuy(gui, p);
                case "sell" -> {
                    plugin.sell().sell(p, false);
                    if (gui.template.id.equals("sell")) later(() -> openSell(p));
                }
                case "sell-hand" -> plugin.sell().sell(p, true);
                case "pickup" -> {
                    if (gui.data.get("gen") instanceof PlacedGenerator pg && plugin.canEdit(p, pg.owner)) {
                        plugin.generators().pickup(p, pg, false);
                    }
                    later(p::closeInventory);
                }
                case "upgrade-generator" -> {
                    if (gui.data.get("gen") instanceof PlacedGenerator pg) {
                        if (plugin.canEdit(p, pg.owner)) plugin.generators().upgrade(p, pg);
                        else plugin.messages().send(p, "edit-denied");
                        later(() -> {
                            if (plugin.generators().get(pg.key) == pg) openManage(p, pg);
                        });
                    }
                }
                case "back" -> {
                    String back = String.valueOf(gui.data.getOrDefault("back", "main"));
                    int page = gui.data.get("backPage") instanceof Integer i ? i : 1;
                    later(() -> open(p, back, Map.of("page", page)));
                }
                default -> {
                    if (tag.equals("upgrade") || tag.startsWith("upgrade:")) {
                        int n = 1;
                        if (tag.startsWith("upgrade:")) {
                            String v = tag.substring(8);
                            if (v.equals("max")) n = -1;
                            else {
                                try {
                                    n = Integer.parseInt(v);
                                } catch (NumberFormatException ex) {
                                    n = 1;
                                }
                            }
                        }
                        plugin.energy().upgrade(p, n);
                        String cur = gui.template.id;
                        int page = gui.data.get("page") instanceof Integer i ? i : 1;
                        later(() -> open(p, cur, Map.of("page", page)));
                    } else if (tag.startsWith("open:")) {
                        String id = tag.substring(5);
                        Map<String, Object> copy = new HashMap<>(gui.data);
                        later(() -> open(p, id, copy));
                    } else {
                        plugin.getLogger().warning("Aksi GUI tidak dikenal: '" + raw + "' di " + gui.template.id + ".yml");
                    }
                }
            }
        }
    }

    private void doBuy(OctaGui gui, Player p) {
        String kind = String.valueOf(gui.data.get("kind"));
        String id = String.valueOf(gui.data.get("id"));
        if (kind.equals("generator")) {
            GeneratorType t = plugin.registry().generator(id);
            if (t != null) plugin.shop().buyGenerator(p, t);
        } else if (kind.equals("energy")) {
            EnergyRecipe r = plugin.registry().recipe(id);
            if (r != null) plugin.shop().buyEnergy(p, r);
        }
    }

    private void playSound(Player p, String arg) {
        String[] parts = arg.split(",");
        String key = parts[0].trim();
        if (key.isEmpty()) return;
        float vol = 1f;
        float pitch = 1f;
        try {
            if (parts.length > 1) vol = Float.parseFloat(parts[1].trim());
            if (parts.length > 2) pitch = Float.parseFloat(parts[2].trim());
        } catch (NumberFormatException ignored) {
        }
        p.playSound(p.getLocation(), key, vol, pitch);
    }
}

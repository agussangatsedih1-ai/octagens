package com.octagens.plugin.manager;

import com.octagens.api.event.EnergyPurchaseEvent;
import com.octagens.api.event.GeneratorPurchaseEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.model.EnergyRecipe;
import com.octagens.plugin.model.EnergyType;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.util.Fmt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generator shop and energy shop (purchase logic + icons). */
public final class ShopManager {
    public enum GenStatus { BUYABLE, LOCKED, SLOT_FULL, NO_MONEY }

    public enum EnergyStatus { BUYABLE, UNAVAILABLE, MISSING, NO_MONEY }

    private final OctaGensPlugin plugin;

    public ShopManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ generator shop

    private int carriedGenerators(Player p) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s != null && plugin.items().generatorId(s) != null) n += s.getAmount();
        }
        return n;
    }

    private boolean slotsFull(Player p) {
        int limit = plugin.slots().generatorLimit(p);
        if (limit == SlotManager.UNLIMITED) return false;
        return plugin.generators().count(p.getUniqueId()) + carriedGenerators(p) >= limit;
    }

    public GenStatus generatorStatus(Player p, GeneratorType t) {
        if (!t.permission.isBlank() && !p.hasPermission(t.permission)) return GenStatus.LOCKED;
        Currency c = plugin.hooks().currency(t.currency);
        if (c == null || plugin.items().generatorItem(t, 1) == null) return GenStatus.LOCKED;
        if (slotsFull(p)) return GenStatus.SLOT_FULL;
        if (!c.has(p, t.shopPrice)) return GenStatus.NO_MONEY;
        return GenStatus.BUYABLE;
    }

    public String statusText(GenStatus s) {
        return plugin.messages().get("status-" + s.name().toLowerCase().replace('_', '-'));
    }

    public ItemStack shopIcon(GeneratorType t, Player p, List<String> entryLore) {
        Map<String, String> extra = new HashMap<>();
        extra.put("status", statusText(generatorStatus(p, t)));
        return plugin.items().generatorIcon(t, extra, p, entryLore);
    }

    public boolean buyGenerator(Player p, GeneratorType t) {
        switch (generatorStatus(p, t)) {
            case LOCKED -> {
                plugin.messages().send(p, "buy-locked");
                return false;
            }
            case SLOT_FULL -> {
                plugin.messages().send(p, "buy-slot-full");
                return false;
            }
            case NO_MONEY -> {
                plugin.messages().send(p, "buy-not-enough-money", "price", plugin.hooks().currency(t.currency).format(t.shopPrice));
                return false;
            }
            default -> {
            }
        }
        Currency c = plugin.hooks().currency(t.currency);
        GeneratorPurchaseEvent ev = new GeneratorPurchaseEvent(p, t.id, t.shopPrice, c.id());
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            plugin.messages().send(p, "buy-cancelled");
            return false;
        }
        double price = ev.getPrice();
        if (!c.has(p, price)) {
            plugin.messages().send(p, "buy-not-enough-money", "price", c.format(price));
            return false;
        }
        ItemStack item = plugin.items().generatorItem(t, 1);
        if (item == null) {
            plugin.messages().send(p, "buy-locked");
            return false;
        }
        if (!c.withdraw(p, price)) {
            plugin.messages().send(p, "buy-failed");
            return false;
        }
        plugin.items().giveOrDrop(p, item);
        plugin.messages().send(p, "buy-generator-success", "name", t.displayName, "price", c.format(price));
        plugin.txLog().log(false, p.getName(), "membeli generator " + t.id + " seharga " + c.format(price), price);
        if (plugin.settings().cpTransactions) plugin.hooks().cp().logTransaction(p, "octagens-buy generator " + t.id + " " + price);
        return true;
    }

    // ------------------------------------------------------------------ energy shop

    private Map<String, Integer> needs(EnergyRecipe r) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (EnergyRecipe.Ingredient i : r.ingredients) m.merge(i.ref(), i.amount(), Integer::sum);
        return m;
    }

    public EnergyStatus energyStatus(Player p, EnergyRecipe r) {
        if (plugin.registry().energy(r.energyId) == null) return EnergyStatus.UNAVAILABLE;
        for (EnergyRecipe.Ingredient i : r.ingredients) {
            if (!plugin.items().isAvailable(i.ref())) return EnergyStatus.UNAVAILABLE;
        }
        Currency c = null;
        if (r.price > 0) {
            c = plugin.hooks().currency(r.currency);
            if (c == null) return EnergyStatus.UNAVAILABLE;
        }
        for (Map.Entry<String, Integer> n : needs(r).entrySet()) {
            if (plugin.items().count(p.getInventory(), n.getKey()) < n.getValue()) return EnergyStatus.MISSING;
        }
        if (c != null && !c.has(p, r.price)) return EnergyStatus.NO_MONEY;
        return EnergyStatus.BUYABLE;
    }

    public String energyStatusText(EnergyStatus s) {
        return plugin.messages().get("energy-status-" + s.name().toLowerCase());
    }

    private String ingredientLines(Player p, EnergyRecipe r) {
        StringBuilder sb = new StringBuilder();
        Map<String, Integer> need = needs(r);
        for (Map.Entry<String, Integer> n : need.entrySet()) {
            int have = plugin.items().count(p.getInventory(), n.getKey());
            boolean ok = have >= n.getValue() && plugin.items().isAvailable(n.getKey());
            if (sb.length() > 0) sb.append('\n');
            sb.append(plugin.messages().raw(ok ? "ingredient-ok" : "ingredient-missing")
                    .replace("{amount}", String.valueOf(n.getValue()))
                    .replace("{name}", plugin.items().refName(n.getKey()))
                    .replace("{have}", String.valueOf(have)));
        }
        return sb.toString();
    }

    public Map<String, String> energyRepl(Player p, EnergyRecipe r) {
        Map<String, String> repl = new HashMap<>();
        Currency c = r.price > 0 ? plugin.hooks().currency(r.currency) : null;
        repl.put("price", r.price <= 0 ? plugin.messages().raw("no-money-cost") : (c != null ? c.format(r.price) : Fmt.money(r.price)));
        repl.put("status", energyStatusText(energyStatus(p, r)));
        repl.put("ingredients", ingredientLines(p, r));
        return repl;
    }

    public ItemStack energyIcon(EnergyRecipe r, Player p, List<String> entryLore) {
        EnergyType t = plugin.registry().energy(r.energyId);
        return plugin.items().energyItem(t, 1, entryLore, energyRepl(p, r));
    }

    public boolean buyEnergy(Player p, EnergyRecipe r) {
        EnergyType t = plugin.registry().energy(r.energyId);
        EnergyStatus st = energyStatus(p, r);
        switch (st) {
            case UNAVAILABLE -> {
                plugin.messages().send(p, "energy-unavailable");
                return false;
            }
            case MISSING -> {
                plugin.messages().send(p, "energy-missing-ingredients");
                return false;
            }
            case NO_MONEY -> {
                plugin.messages().send(p, "buy-not-enough-money", "price", plugin.hooks().currency(r.currency).format(r.price));
                return false;
            }
            default -> {
            }
        }
        Currency c = r.price > 0 ? plugin.hooks().currency(r.currency) : null;
        EnergyPurchaseEvent ev = new EnergyPurchaseEvent(p, r.energyId, r.price, c == null ? "none" : c.id());
        Bukkit.getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            plugin.messages().send(p, "buy-cancelled");
            return false;
        }
        double price = ev.getPrice();
        // Atomic: everything was verified above in the same tick; money first (the only step that can still fail),
        // then ingredients, then the reward. If the withdrawal fails nothing has been taken.
        if (price > 0) {
            if (c == null || !c.withdraw(p, price)) {
                plugin.messages().send(p, "buy-failed");
                return false;
            }
        }
        for (Map.Entry<String, Integer> n : needs(r).entrySet()) plugin.items().remove(p.getInventory(), n.getKey(), n.getValue());
        plugin.items().giveOrDrop(p, plugin.items().energyItem(t, 1));
        String priceText = price <= 0 ? plugin.messages().raw("no-money-cost") : c.format(price);
        plugin.messages().send(p, "energy-buy-success", "name", t.displayName, "price", priceText);
        plugin.txLog().log(false, p.getName(), "membeli energy " + t.id + " (uang " + priceText + ")", price);
        if (plugin.settings().cpTransactions) plugin.hooks().cp().logTransaction(p, "octagens-buy energy " + t.id);
        return true;
    }
}

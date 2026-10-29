package com.octagens.plugin.manager;

import com.octagens.api.event.DropSellEvent;
import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.model.GeneratorType;
import com.octagens.plugin.util.Fmt;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

/** Selling of OctaGens drops (identified by PDC) from the player inventory or from a container (sellwand). */
public final class SellManager {
    private static final class Group {
        final Currency currency;
        final Map<String, Integer> items = new LinkedHashMap<>();
        final List<Integer> slots = new ArrayList<>();
        double base;

        Group(Currency c) {
            this.currency = c;
        }
    }

    /** Summary shown in the sell GUI. */
    public record Preview(List<String> lines, int items, String total, double multiplier) {
        public boolean empty() {
            return items <= 0;
        }
    }

    private final OctaGensPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public SellManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void clear(UUID id) {
        cooldown.remove(id);
    }

    /** Highest octagens.sellmultiplier.<x> permission value (1.0 default). */
    public double multiplier(Player p) {
        double best = 1.0;
        String prefix = "octagens.sellmultiplier.";
        for (PermissionAttachmentInfo pai : p.getEffectivePermissions()) {
            if (!pai.getValue()) continue;
            String perm = pai.getPermission().toLowerCase(Locale.ROOT);
            if (!perm.startsWith(prefix)) continue;
            try {
                best = Math.max(best, Double.parseDouble(perm.substring(prefix.length())));
            } catch (NumberFormatException ignored) {
            }
        }
        return best;
    }

    private Map<String, Group> scan(ItemStack[] contents, int from, int to, boolean[] unavailable, Predicate<ItemStack> accept) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (int i = from; i < to && i < contents.length; i++) {
            ItemStack s = contents[i];
            String id = plugin.items().dropId(s);
            if (id == null || (accept != null && !accept.test(s))) continue;
            GeneratorType t = plugin.registry().generator(id);
            if (t == null) continue;
            Currency cur = plugin.hooks().currency(t.sellCurrency);
            if (cur == null) {
                unavailable[0] = true;
                continue;
            }
            Group g = groups.computeIfAbsent(cur.id(), k -> new Group(cur));
            g.items.merge(id, s.getAmount(), Integer::sum);
            g.base += t.sellPrice * s.getAmount();
            g.slots.add(i);
        }
        return groups;
    }

    public Preview preview(Player p) {
        double mult = multiplier(p);
        ItemStack[] contents = p.getInventory().getStorageContents();
        Map<String, Group> groups = scan(contents, 0, contents.length, new boolean[1], null);
        List<String> lines = new ArrayList<>();
        List<String> totals = new ArrayList<>();
        int count = 0;
        for (Group g : groups.values()) {
            for (Map.Entry<String, Integer> en : g.items.entrySet()) {
                GeneratorType t = plugin.registry().generator(en.getKey());
                String drop = t.dropItem.name == null ? Fmt.pretty(t.dropItem.ref) : t.dropItem.name;
                double money = t.sellPrice * en.getValue() * mult;
                lines.add(plugin.messages().raw("sell-gui-line")
                        .replace("{amount}", String.valueOf(en.getValue()))
                        .replace("{name}", drop)
                        .replace("{money}", g.currency.format(money)));
                count += en.getValue();
            }
            totals.add(g.currency.format(g.base * mult));
        }
        return new Preview(lines, count, totals.isEmpty() ? "-" : String.join(", ", totals), mult);
    }

    /** Sells drops from the player's own inventory. Returns true if at least one group was sold. */
    public boolean sell(Player p, boolean handOnly) {
        long now = System.currentTimeMillis();
        long cd = plugin.settings().sellCooldownMs;
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            plugin.messages().send(p, "sell-cooldown", "seconds", Fmt.number((cd - (now - last)) / 1000.0));
            return false;
        }
        cooldown.put(p.getUniqueId(), now);

        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getStorageContents();
        int from = 0;
        int to = contents.length;
        if (handOnly) {
            from = inv.getHeldItemSlot();
            to = from + 1;
        }
        boolean[] unavailable = new boolean[1];
        Map<String, Group> groups = scan(contents, from, to, unavailable, null);
        if (groups.isEmpty()) {
            if (unavailable[0]) plugin.messages().send(p, "sell-currency-unavailable");
            else plugin.messages().send(p, handOnly ? "sell-hand-not-drop" : "sell-nothing");
            return false;
        }
        return payout(p, groups, multiplier(p), slot -> inv.setItem(slot, null), "sell", "sell-success", "sell-success-multiplier") > 0;
    }

    /**
     * Sellwand: sells every OctaGens drop stored in a container inventory (chest, barrel, shulker box...).
     * @param multiplier the wand multiplier (no upper limit, decimals allowed) applied on top of the
     *                   player's permission multiplier
     * @return number of items sold (0 = nothing was sold)
     */
    public int sellContainer(Player p, Inventory inv, double multiplier) {
        boolean[] unavailable = new boolean[1];
        ItemStack[] contents = inv.getContents();
        Map<String, Group> groups = scan(contents, 0, contents.length, unavailable, null);
        if (groups.isEmpty()) {
            if (unavailable[0]) plugin.messages().send(p, "sell-currency-unavailable");
            return 0;
        }
        double mult = multiplier(p) * Math.max(0, multiplier);
        return payout(p, groups, mult, slot -> inv.setItem(slot, null), "sellwand", "sellwand-success", "sellwand-success");
    }

    private int payout(Player p, Map<String, Group> groups, double mult, IntConsumer clearSlot, String source, String keyPlain, String keyMult) {
        int soldItems = 0;
        for (Group g : groups.values()) {
            double total = g.base * mult;
            DropSellEvent ev = new DropSellEvent(p, g.currency.id(), new LinkedHashMap<>(g.items), total, mult);
            Bukkit.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                plugin.messages().send(p, "sell-cancelled");
                continue;
            }
            total = ev.getTotal();
            if (!g.currency.deposit(p, total)) {
                plugin.messages().send(p, "sell-failed");
                continue;
            }
            for (int slot : g.slots) clearSlot.accept(slot);
            int amount = g.items.values().stream().mapToInt(Integer::intValue).sum();
            soldItems += amount;
            String totalText = g.currency.format(total);
            plugin.messages().send(p, mult > 1.0 + 1e-9 ? keyMult : keyPlain,
                    "amount", String.valueOf(amount), "total", totalText, "multiplier", Fmt.number(mult));
            String summary = source + ": menjual " + amount + " item (" + g.items + ") = " + totalText + " x" + Fmt.number(mult);
            plugin.txLog().log(true, p.getName(), summary, total);
            if (plugin.settings().cpTransactions) plugin.hooks().cp().logTransaction(p, "octagens-" + source + " " + amount + " item " + total);
        }
        return soldItems;
    }
}

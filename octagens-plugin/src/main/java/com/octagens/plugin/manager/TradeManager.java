package com.octagens.plugin.manager;

import com.octagens.plugin.OctaGensPlugin;
import com.octagens.plugin.hook.Currency;
import com.octagens.plugin.item.ItemSpec;
import com.octagens.plugin.model.EnergyRecipe;
import com.octagens.plugin.model.Trade;
import com.octagens.plugin.model.TradeCategory;
import com.octagens.plugin.storage.Database;
import com.octagens.plugin.util.Fmt;
import com.octagens.plugin.util.Text;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * /octagens trade: players exchange items (typically generator drops, via "DROP:<generator_id>" ingredients)
 * for rewards such as MMOItems. Categories, trades, ingredients and requirements are defined in trade/trade.yml.
 */
public final class TradeManager {
    public enum Status { READY, LOCKED, UNAVAILABLE, LIMIT, CONDITION, MISSING, NO_MONEY }

    private static final Pattern COND = Pattern.compile("^\\s*(.+?)\\s*(>=|<=|==|!=|>|<)\\s*(.+?)\\s*$");

    private final OctaGensPlugin plugin;
    private final Map<UUID, Map<String, Integer>> uses = new HashMap<>();

    public TradeManager(OctaGensPlugin plugin) {
        this.plugin = plugin;
    }

    public void load(List<Database.TradeRow> rows) {
        uses.clear();
        for (Database.TradeRow r : rows) uses.computeIfAbsent(r.uuid(), k -> new HashMap<>()).put(r.tradeKey(), r.count());
    }

    public int used(UUID id, Trade t) {
        Map<String, Integer> m = uses.get(id);
        return m == null ? 0 : m.getOrDefault(t.key(), 0);
    }

    // ------------------------------------------------------------------ checks

    /** Evaluates "%placeholder% >= 10" style conditions after PlaceholderAPI parsing. */
    public boolean test(Player p, String expression) {
        String parsed = PlaceholderAPI.setPlaceholders(p, expression);
        Matcher m = COND.matcher(parsed);
        if (!m.matches()) {
            String v = parsed.trim().toLowerCase(Locale.ROOT);
            return v.equals("true") || v.equals("yes") || v.equals("ya");
        }
        String a = m.group(1).trim();
        String op = m.group(2);
        String b = m.group(3).trim();
        try {
            double x = Double.parseDouble(a.replace(",", ""));
            double y = Double.parseDouble(b.replace(",", ""));
            return switch (op) {
                case ">=" -> x >= y;
                case "<=" -> x <= y;
                case ">" -> x > y;
                case "<" -> x < y;
                case "==" -> Math.abs(x - y) < 1e-9;
                default -> Math.abs(x - y) >= 1e-9;
            };
        } catch (NumberFormatException e) {
            return switch (op) {
                case "==" -> a.equalsIgnoreCase(b);
                case "!=" -> !a.equalsIgnoreCase(b);
                default -> false;
            };
        }
    }

    private boolean ingredientsMissing(Player p, Trade t) {
        Map<String, Integer> need = new LinkedHashMap<>();
        for (EnergyRecipe.Ingredient i : t.ingredients) need.merge(i.ref(), i.amount(), Integer::sum);
        for (Map.Entry<String, Integer> n : need.entrySet()) {
            if (plugin.items().count(p.getInventory(), n.getKey()) < n.getValue()) return true;
        }
        return false;
    }

    public boolean categoryAllowed(Player p, TradeCategory c) {
        return c.permission.isBlank() || p.hasPermission(c.permission);
    }

    public Status status(Player p, TradeCategory c, Trade t) {
        if (!categoryAllowed(p, c) || (!t.permission.isBlank() && !p.hasPermission(t.permission))) return Status.LOCKED;
        if (!plugin.items().isAvailable(t.result) || plugin.items().resolve(t.result) == null) return Status.UNAVAILABLE;
        for (EnergyRecipe.Ingredient i : t.ingredients) {
            if (!plugin.items().isAvailable(i.ref())) return Status.UNAVAILABLE;
        }
        Currency cur = null;
        if (t.money > 0) {
            cur = plugin.hooks().currency(t.currency);
            if (cur == null) return Status.UNAVAILABLE;
        }
        if (t.limit > 0 && used(p.getUniqueId(), t) >= t.limit) return Status.LIMIT;
        for (Trade.Condition cond : t.conditions) {
            if (!test(p, cond.expression())) return Status.CONDITION;
        }
        if (ingredientsMissing(p, t)) return Status.MISSING;
        if (cur != null && !cur.has(p, t.money)) return Status.NO_MONEY;
        return Status.READY;
    }

    public String statusText(Status s) {
        return plugin.messages().get("trade-status-" + s.name().toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ display

    private String ingredientLines(Player p, Trade t) {
        if (t.ingredients.isEmpty()) return plugin.messages().raw("trade-none");
        Map<String, Integer> need = new LinkedHashMap<>();
        for (EnergyRecipe.Ingredient i : t.ingredients) need.merge(i.ref(), i.amount(), Integer::sum);
        StringBuilder sb = new StringBuilder();
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

    private String reqLine(boolean ok, String text) {
        return plugin.messages().raw(ok ? "trade-req-ok" : "trade-req-missing").replace("{text}", text);
    }

    private String requirementLines(Player p, Trade t) {
        List<String> lines = new ArrayList<>();
        if (t.money > 0) {
            Currency c = plugin.hooks().currency(t.currency);
            String price = c != null ? c.format(t.money) : Fmt.money(t.money);
            lines.add(reqLine(c != null && c.has(p, t.money),
                    plugin.messages().raw("trade-req-money").replace("{price}", price)));
        }
        if (!t.permission.isBlank()) {
            lines.add(reqLine(p.hasPermission(t.permission), plugin.messages().raw("trade-req-permission")));
        }
        for (Trade.Condition c : t.conditions) lines.add(reqLine(test(p, c.expression()), c.description()));
        return lines.isEmpty() ? plugin.messages().raw("trade-none") : String.join("\n", lines);
    }

    private Map<String, String> repl(Player p, TradeCategory c, Trade t) {
        Map<String, String> r = new HashMap<>();
        r.put("ingredients", ingredientLines(p, t));
        r.put("requirements", requirementLines(p, t));
        r.put("status", statusText(status(p, c, t)));
        r.put("amount", String.valueOf(t.amount));
        r.put("name", plugin.items().refName(t.result));
        r.put("category", c.displayName);
        int u = used(p.getUniqueId(), t);
        r.put("used", String.valueOf(u));
        r.put("limit", t.limit <= 0 ? Text.strip(plugin.messages().get("placeholder-unlimited")) : String.valueOf(t.limit));
        return r;
    }

    /** The GUI icon of a trade: the reward item (or the configured display) plus the entry lore. */
    public ItemStack icon(Player p, TradeCategory c, Trade t, List<String> entryLore) {
        Map<String, String> r = repl(p, c, t);
        ItemStack st;
        if (t.display != null) {
            st = plugin.items().buildSafe(t.display.copy(), r, p);
        } else {
            ItemStack base = plugin.items().resolve(t.result);
            st = base != null ? base.clone() : plugin.items().buildSafe(ItemSpec.ofRef(t.result), r, p);
        }
        st.setAmount(Math.max(1, Math.min(t.amount, st.getMaxStackSize())));
        ItemMeta meta = st.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.hasLore() && meta.getLore() != null ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            lore.addAll(plugin.items().lore(entryLore, r, p));
            meta.setLore(lore);
            st.setItemMeta(meta);
        }
        return st;
    }

    public ItemStack categoryIcon(Player p, TradeCategory c, List<String> entryLore) {
        ItemSpec spec = c.icon.copy();
        spec.amount = 1;
        List<String> lore = new ArrayList<>(spec.lore != null ? spec.lore : List.of());
        lore.addAll(entryLore);
        spec.lore = lore;
        Map<String, String> r = new HashMap<>();
        r.put("count", String.valueOf(c.trades.size()));
        r.put("category", c.displayName);
        return plugin.items().buildSafe(spec, r, p);
    }

    // ------------------------------------------------------------------ execute

    /** Performs a trade. Everything is verified first; if anything fails nothing is taken. */
    public boolean execute(Player p, TradeCategory c, Trade t) {
        Status st = status(p, c, t);
        if (st != Status.READY) {
            plugin.messages().send(p, "trade-" + st.name().toLowerCase(Locale.ROOT));
            return false;
        }
        ItemStack proto = plugin.items().resolve(t.result);
        if (proto == null) {
            plugin.messages().send(p, "trade-unavailable");
            return false;
        }
        Currency cur = t.money > 0 ? plugin.hooks().currency(t.currency) : null;
        if (cur != null && !cur.withdraw(p, t.money)) {
            plugin.messages().send(p, "buy-failed");
            return false;
        }
        Map<String, Integer> need = new LinkedHashMap<>();
        for (EnergyRecipe.Ingredient i : t.ingredients) need.merge(i.ref(), i.amount(), Integer::sum);
        for (Map.Entry<String, Integer> n : need.entrySet()) plugin.items().remove(p.getInventory(), n.getKey(), n.getValue());

        int left = t.amount;
        int max = Math.max(1, proto.getMaxStackSize());
        while (left > 0) {
            int n = Math.min(max, left);
            left -= n;
            ItemStack give = proto.clone();
            give.setAmount(n);
            plugin.items().giveOrDrop(p, give);
        }
        for (String cmd : t.commands) runCommand(p, cmd);

        int u = used(p.getUniqueId(), t) + 1;
        uses.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>()).put(t.key(), u);
        plugin.database().saveTrade(new Database.TradeRow(p.getUniqueId(), t.key(), u));

        String name = plugin.items().refName(t.result);
        plugin.messages().send(p, "trade-success", "amount", String.valueOf(t.amount), "name", name);
        p.playSound(p.getLocation(), "entity.player.levelup", 0.6f, 1.4f);
        plugin.txLog().log(false, p.getName(), "trade " + t.key() + " -> " + t.amount + "x " + t.result, t.money);
        if (plugin.settings().cpTransactions) plugin.hooks().cp().logTransaction(p, "octagens-trade " + t.key());
        return true;
    }

    private void runCommand(Player p, String raw) {
        String cmd = raw.trim();
        boolean asPlayer = false;
        if (cmd.regionMatches(true, 0, "[player]", 0, 8)) {
            asPlayer = true;
            cmd = cmd.substring(8).trim();
        } else if (cmd.regionMatches(true, 0, "[console]", 0, 9)) {
            cmd = cmd.substring(9).trim();
        }
        cmd = PlaceholderAPI.setPlaceholders(p, cmd.replace("{player}", p.getName()));
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        if (cmd.isEmpty()) return;
        if (asPlayer) p.performCommand(cmd);
        else Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}

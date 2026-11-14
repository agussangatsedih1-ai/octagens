package com.octagens.plugin.model;

import com.octagens.plugin.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One trade (recipe) inside a trade category, loaded from trade/trade.yml. */
public final class Trade {
    /** A PlaceholderAPI condition such as "%player_level% >= 10" with an optional description. */
    public record Condition(String expression, String description) {
    }

    public final String id;
    public String categoryId;
    public String result;
    public int amount;
    public ItemSpec display;
    public final List<EnergyRecipe.Ingredient> ingredients = new ArrayList<>();
    public String permission = "";
    public double money;
    public String currency;
    public final List<Condition> conditions = new ArrayList<>();
    public int limit;
    public final List<String> commands = new ArrayList<>();

    private Trade(String id) {
        this.id = id;
    }

    public String key() {
        return categoryId + ":" + id;
    }

    public static Trade parse(String categoryId, String key, ConfigurationSection s, String defaultCurrency) {
        Trade t = new Trade(key.toLowerCase(Locale.ROOT));
        t.categoryId = categoryId;
        t.result = s.getString("result", "");
        if (t.result.isBlank()) throw new IllegalArgumentException("'result' kosong");
        t.amount = Math.max(1, s.getInt("amount", 1));
        ConfigurationSection d = s.getConfigurationSection("display");
        if (d != null) {
            t.display = ItemSpec.fromSection(d);
            if (t.display.ref.isBlank()) t.display.ref = t.result;
        }
        for (String line : s.getStringList("ingredients")) t.ingredients.add(EnergyRecipe.parseIngredient(line));
        ConfigurationSection r = s.getConfigurationSection("requirements");
        if (r != null) {
            t.permission = r.getString("permission", "");
            t.money = Math.max(0, r.getDouble("money", 0));
            t.currency = r.getString("currency", defaultCurrency);
            List<?> raw = r.getList("placeholders");
            if (raw != null) {
                for (Object o : raw) {
                    if (o instanceof Map<?, ?> m) {
                        Object c = m.get("condition");
                        if (c == null) continue;
                        Object desc = m.get("description");
                        t.conditions.add(new Condition(String.valueOf(c), desc == null ? String.valueOf(c) : String.valueOf(desc)));
                    } else if (o != null) {
                        t.conditions.add(new Condition(String.valueOf(o), String.valueOf(o)));
                    }
                }
            }
        } else {
            t.currency = defaultCurrency;
        }
        t.limit = Math.max(0, s.getInt("limit", 0));
        t.commands.addAll(s.getStringList("commands"));
        return t;
    }
}

package com.octagens.plugin.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Energy shop recipe loaded from energyshop.yml. */
public final class EnergyRecipe {
    private static final Pattern ING = Pattern.compile("^\\s*(\\d+)\\s*[xX]\\s+(.+?)\\s*$");

    public record Ingredient(String ref, int amount) {
    }

    public final String energyId;
    public double price;
    public String currency;
    public final List<Ingredient> ingredients = new ArrayList<>();

    private EnergyRecipe(String energyId) {
        this.energyId = energyId;
    }

    public static EnergyRecipe parse(String key, ConfigurationSection s, String defaultCurrency) {
        EnergyRecipe r = new EnergyRecipe(key.toLowerCase(Locale.ROOT));
        r.price = Math.max(0, s.getDouble("price", 0));
        r.currency = s.getString("currency", defaultCurrency);
        for (String line : s.getStringList("ingredients")) r.ingredients.add(parseIngredient(line));
        return r;
    }

    /** Parses "<amount>x <ref>", e.g. "64x VANILLA:LAPIS_BLOCK" or "5x DROP:lesser_mana". */
    public static Ingredient parseIngredient(String line) {
        Matcher m = ING.matcher(line == null ? "" : line);
        if (!m.matches()) {
            throw new IllegalArgumentException("Format bahan salah: '" + line + "' (contoh: \"64x VANILLA:LAPIS_BLOCK\")");
        }
        return new Ingredient(m.group(2), Integer.parseInt(m.group(1)));
    }
}

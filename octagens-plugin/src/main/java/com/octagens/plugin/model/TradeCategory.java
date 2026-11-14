package com.octagens.plugin.model;

import com.octagens.plugin.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A trade category (unlimited, defined in trade/trade.yml). */
public final class TradeCategory {
    public final String id;
    public String displayName;
    public ItemSpec icon;
    public String permission = "";
    public int slot = -1;
    public final List<Trade> trades = new ArrayList<>();

    private TradeCategory(String id) {
        this.id = id;
    }

    public static TradeCategory parse(String key, ConfigurationSection s, String defaultCurrency, java.util.logging.Logger log) {
        TradeCategory c = new TradeCategory(key.toLowerCase(Locale.ROOT));
        c.displayName = s.getString("display-name", c.id);
        ConfigurationSection ic = s.getConfigurationSection("icon");
        c.icon = ic != null ? ItemSpec.fromSection(ic) : ItemSpec.ofRef(s.getString("icon", "VANILLA:CHEST"));
        if (c.icon.ref.isBlank()) c.icon.ref = "VANILLA:CHEST";
        if (c.icon.name == null) c.icon.name = c.displayName;
        c.permission = s.getString("permission", "");
        c.slot = s.getInt("slot", -1);
        ConfigurationSection ts = s.getConfigurationSection("trades");
        if (ts != null) {
            for (String tk : ts.getKeys(false)) {
                ConfigurationSection tsec = ts.getConfigurationSection(tk);
                if (tsec == null) continue;
                try {
                    c.trades.add(Trade.parse(c.id, tk, tsec, defaultCurrency));
                } catch (Exception ex) {
                    log.warning("trade.yml: trade '" + c.id + "." + tk + "' dilewati: " + ex.getMessage());
                }
            }
        }
        return c;
    }
}
